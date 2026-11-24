package com.kordi_api.world;

import com.kordi_api.character.entity.CharacterCatalog;
import com.kordi_api.character.service.AppearanceChanged;
import com.kordi_api.character.service.CharacterService;
import com.kordi_api.course.dto.CourseResult;
import com.kordi_api.course.service.CourseRecordService;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 3D 피아노 월드 실시간 동기화.
 *
 * <ul>
 *   <li>서버 → 클라이언트
 *       <ul>
 *         <li>{@code welcome}: 내 id, 지역·채널 번호, 같은 채널 접속자, 전체·지역별 접속자 수, 별 계단이 열려 있을 시간({@code sky},
 *             ms)
 *         <li>{@code join} / {@code leave}: 같은 채널 입·퇴장 (즉시)
 *         <li>{@code moves}: 같은 채널에서 움직인 사람들의 최신 위치 ({@value #TICK_MS}ms마다 묶어서)
 *         <li>{@code online}: 전체·지역별 접속자 수 ({@value #ONLINE_MS}ms마다)
 *         <li>{@code pet}: 펫을 새로 얻었다 ({@code item}). 그 사람에게만
 *         <li>{@code bell}: 같은 채널 누군가 자정의 종을 쳤다 ({@code left}: 별 계단이 열려 있을 시간, ms). 친 사람에게는 보내지 않는다
 *       </ul>
 *   <li>{@code look}: 같은 채널 누군가({@code id})의 표시 이름({@code name})과 꾸민 모습({@code look}, 없으면 null)이 바뀜
 *       (회원이 로그인을 밝혔을 때 이름은 DB 닉네임으로 바뀐다, 꾸미기를 저장했을 때). 자기 자신에게도 간다
 *   <li>{@code record}: 점프 코스 완주 기록 (서버가 잰 시간, 역대 순위, 저장 여부)
 *   <li>클라이언트 → 서버
 *       <ul>
 *         <li>{@link WorldMove} (x, y, z, h, m, e)
 *         <li>{@code {"t":"auth","ticket":...}}: 로그인 유저임을 밝힘 ({@link WorldTicketService})
 *         <li>{@code {"t":"bell"}}: 밤에 종탑의 종을 쳤다 ({@link WorldLobby#ringBell})
 *         <li>{@code {"t":"whale"}}: 꿈의 바다 꼭대기에서 고래가 노래했다 → 회원이면 달고래 펫 ({@link #adoptWhale})
 *         <li>{@code {"t":"ping"}}: 살아 있음 알림. 위치는 바뀔 때만 오므로 가만히 서 있는 클라이언트는 이것을 {@value
 *             #IDLE_TIMEOUT_MS}ms보다 충분히 자주(30초마다) 보내야 한다
 *       </ul>
 * </ul>
 *
 * 인증 없이 누구나 입장한다(홈 화면이 공개 페이지). 비회원 이름은 표시용이며 검증하지 않고, 입장권으로 로그인을 밝히면 DB 닉네임으로 덮어쓴다. 입장권으로 로그인을 밝힌
 * 소켓만 코스 기록이 랭킹에 저장된다.
 *
 * <p>보내기는 소켓마다 {@link WorldOutbox}에 넣기만 하므로, 느린 클라이언트가 타이머나 다른 사람의 전송을 붙잡지 못한다. 애플리케이션이 멈출 때는 모든
 * 소켓을 1001(GOING_AWAY)로 닫아 클라이언트가 곧바로 다시 붙게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorldSocketHandler extends TextWebSocketHandler implements SmartLifecycle {

  static final long TICK_MS = 100;
  static final long ONLINE_MS = 5_000;

  private static final String PLAYER_ID = "playerId";
  private static final String USER_ID = "userId";
  private static final String CLIENT_IP = "clientIp";
  private static final String CONNECTED_AT = "connectedAt";
  private static final String LAST_SEEN = "lastSeen";

  /** 이 소켓에서 고래 알림을 이미 처리했다 (펫을 줬든 이미 가졌든): 같은 알림을 반복해 DB를 두드리지 못하게 */
  private static final String WHALE_DONE = "whaleDone";

  /** 정상 클라이언트는 접속하자마자 자기 위치를 보낸다. 이 안에 아무것도 안 보내면 자리만 차지하는 연결로 보고 닫는다. */
  static final long FIRST_MESSAGE_TIMEOUT_MS = 30_000;

  /**
   * 마지막 메시지 이후 이만큼 조용하면 닫는다. 위치는 바뀔 때만 오므로 클라이언트는 30초마다 {@code {"t":"ping"}}을 보낸다 (백그라운드 탭에서 타이머가
   * 1분에 한 번으로 늦춰져도 넘지 않도록). 탭을 닫았는데 연결만 남은 경우나 자리만 차지하는 연결을 빨리 비운다.
   */
  static final long IDLE_TIMEOUT_MS = 90_000;

  static final long IDLE_SWEEP_MS = 10_000;

  /**
   * Tomcat의 블로킹 전송 대기 한도 (기본 20초). 받지 않는 클라이언트에게 쓰는 작업이 이보다 오래 걸리면 실패로 보고 그 소켓을 닫는다. 쓰는 일은 소켓 전용 가상
   * 스레드가 하므로 다른 소켓에는 영향이 없다.
   */
  static final long BLOCKING_SEND_TIMEOUT_MS = 2_000;

  static final String TOMCAT_BLOCKING_SEND_TIMEOUT =
      "org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT";

  /** 멈출 때 모든 소켓을 닫는 데 기다리는 최대 시간 */
  private static final long SHUTDOWN_CLOSE_WAIT_MS = 3_000;

  private static final int MAX_INCOMING_LENGTH = 256;

  /**
   * 꿈의 바다에 이만큼은 있어야 고래를 만났다고 믿는다: 다섯 음을 찾아 꼭대기까지 오르려면 걸어서도 몇 분은 걸린다. 지역을 옮기면 소켓을 새로 열므로 접속 시각이 곧 도착
   * 시각이다.
   */
  static final long WHALE_MIN_STAY_MS = 60_000;

  private final WorldLobby lobby;
  private final CourseTimer courseTimer;
  private final WorldTicketService ticketService;
  private final CourseRecordService courseRecordService;
  private final CharacterService characterService;
  private final ConnectionLimiter connectionLimiter;
  private final JsonMapper jsonMapper;
  private final UserRepository userRepository;
  private final Map<String, WorldOutbox> sessions = new ConcurrentHashMap<>();

  /** 소켓마다 보내는 일을 맡는 가상 스레드 (테스트에서는 바꿔 끼운다) */
  private Executor sendExecutor = Executors.newVirtualThreadPerTaskExecutor();

  private volatile boolean running;

  @Override
  public void afterConnectionEstablished(WebSocketSession rawSession) throws IOException {
    if (!running) {
      rawSession.close(CloseStatus.GOING_AWAY);
      return;
    }
    limitBlockingSend(rawSession);
    String ip = clientIp(rawSession);
    if (!connectionLimiter.tryAcquire(ip)) {
      rawSession.close(CloseStatus.POLICY_VIOLATION.withReason("too many connections"));
      return;
    }
    rawSession.getAttributes().put(CLIENT_IP, ip);
    rawSession.getAttributes().put(CONNECTED_AT, now());
    var seat = lobby.join(queryParam(rawSession, "region"), queryParam(rawSession, "name"));
    if (seat.isEmpty()) {
      // 전체 정원 초과: 클라이언트는 이 코드로 혼자 모드 전환
      disconnect(rawSession, CloseStatus.SERVICE_OVERLOAD.withReason("full"));
      return;
    }
    WorldPlayer me = seat.get().player();
    WorldLobby.ChannelKey channel = seat.get().channel();

    WorldOutbox outbox =
        new WorldOutbox(rawSession, sendExecutor, status -> disconnect(rawSession, status));
    rawSession.getAttributes().put(PLAYER_ID, me.id());
    // 줄을 먼저 등록해 둔다: welcome을 만드는 사이 들어온 다른 사람 소식도 잃지 않고 welcome 뒤에 간다
    sessions.put(me.id(), outbox);

    outbox.start(
        jsonMapper.writeValueAsString(
            Map.of(
                "t", "welcome",
                "id", me.id(),
                "region", channel.region(),
                "ch", channel.number(),
                "players", List.copyOf(lobby.playersIn(channel)),
                "online", lobby.totalPlayers(),
                "regions", lobby.playersPerRegion(),
                "sky", lobby.skyLeft(channel, now()))));
    broadcastToChannel(channel, Map.of("t", "join", "p", me), me.id());
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    String id = (String) session.getAttributes().get(PLAYER_ID);
    if (id == null || message.getPayloadLength() > MAX_INCOMING_LENGTH) {
      return;
    }
    session.getAttributes().put(LAST_SEEN, now());
    try {
      var node = jsonMapper.readTree(message.getPayload());
      String type = node.path("t").asString("");
      if ("ping".equals(type)) {
        return; // 살아 있음: LAST_SEEN만 갱신
      }
      if ("bell".equals(type)) {
        lobby
            .ringBell(id, now())
            .ifPresent(
                bell ->
                    broadcastToChannel(
                        bell.channel(), Map.of("t", "bell", "left", bell.openMs()), id));
        return;
      }
      if ("whale".equals(type)) {
        adoptWhale(session, id);
        return;
      }
      if ("auth".equals(type)) {
        ticketService
            .redeem(node.path("ticket").asString(null))
            .ifPresent(
                userId -> {
                  session.getAttributes().put(USER_ID, userId);
                  identify(id, userId);
                });
        return;
      }
      WorldMove move = jsonMapper.treeToValue(node, WorldMove.class);
      long now = now();
      if (lobby.move(id, move, now)) {
        timeCourse(session, id, move, now);
      }
    } catch (JacksonException e) {
      // 형식이 틀린 메시지는 무시
    }
  }

  /**
   * 고래가 노래했다는 알림: 회원이 꿈의 바다에 충분히 머물렀고 지금 꼭대기에 서 있으면 달고래 펫을 준다. 처음 얻었을 때만 그 사람에게 알리고, 모습 변경은 {@link
   * #onAppearanceChanged}가 채널에 퍼뜨린다. 비회원은 클라이언트가 브라우저에 기억한다.
   */
  private void adoptWhale(WebSocketSession session, String id) {
    Long userId = (Long) session.getAttributes().get(USER_ID);
    Long connectedAt = (Long) session.getAttributes().get(CONNECTED_AT);
    if (session.getAttributes().containsKey(WHALE_DONE)
        || userId == null
        || connectedAt == null
        || now() - connectedAt < WHALE_MIN_STAY_MS
        || !lobby.atWhaleSummit(id)) {
      return;
    }
    boolean adopted;
    try {
      adopted = characterService.grantPet(userId, CharacterCatalog.MOON_WHALE);
      session.getAttributes().put(WHALE_DONE, true);
    } catch (RuntimeException e) {
      log.warn("[World] pet grant failed: {}", e.getMessage());
      return;
    }
    WorldOutbox live = sessions.get(id);
    if (adopted && live != null) {
      live.offer(
          jsonMapper.writeValueAsString(Map.of("t", "pet", "item", CharacterCatalog.MOON_WHALE)));
    }
  }

  /**
   * 회원임이 밝혀지면 표시 이름을 DB 닉네임으로 덮어쓰고(입장 때 보낸 이름은 아무거나 쓸 수 있으므로) 저장된 모습을 입혀 같은 채널에 알린다. 둘 중 하나를 못 읽어도
   * 나머지는 반영한다.
   */
  private void identify(String playerId, Long userId) {
    Optional<WorldLobby.Seat> seat = Optional.empty();
    try {
      seat =
          userRepository
              .findById(userId)
              .map(User::getNickname)
              .flatMap(nickname -> lobby.rename(playerId, nickname));
    } catch (RuntimeException e) {
      log.debug("[World] nickname load failed: {}", e.getMessage());
    }
    try {
      WorldLook look = WorldLook.of(characterService.get(userId).appearance());
      seat = lobby.dress(playerId, look);
    } catch (RuntimeException e) {
      log.debug("[World] look load failed: {}", e.getMessage());
    }
    seat.ifPresent(this::announceLook);
  }

  private void dress(String playerId, WorldLook look) {
    lobby.dress(playerId, look).ifPresent(this::announceLook);
  }

  /** 이름과 모습을 함께 알린다 (자기 자신 포함: 서버가 정한 내 이름을 클라이언트도 알도록) */
  private void announceLook(WorldLobby.Seat seat) {
    WorldPlayer p = seat.player();
    var payload = new HashMap<String, Object>();
    payload.put("t", "look");
    payload.put("id", p.id());
    payload.put("name", p.name());
    payload.put("look", p.look());
    broadcastToChannel(seat.channel(), payload, null);
  }

  /**
   * 꾸미기 저장·보상 장착: 그 유저가 지금 월드에 있으면 바로 모습을 바꾼다. DB에 확정된 뒤에만 알린다 (롤백된 변경을 보여주지 않도록, 그리고 소켓 전송 동안 DB
   * 연결을 붙잡지 않도록).
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  void onAppearanceChanged(AppearanceChanged event) {
    WorldLook look = WorldLook.of(event.appearance());
    sessions.forEach(
        (playerId, outbox) -> {
          if (event.userId().equals(outbox.session().getAttributes().get(USER_ID))) {
            dress(playerId, look);
          }
        });
  }

  /** 점프 코스에서는 위치로 기록을 재고, 완주하면 결과를 그 사람에게만 알려준다. */
  private void timeCourse(WebSocketSession rawSession, String id, WorldMove move, long now) {
    String region = lobby.channelOf(id).map(WorldLobby.ChannelKey::region).orElse(null);
    double y = move.y() == null ? 0 : move.y();
    courseTimer
        .track(id, region, move.x(), y, move.z(), now)
        .ifPresent(
            finish -> {
              Long userId = (Long) rawSession.getAttributes().get(USER_ID);
              CourseResult result;
              try {
                result = courseRecordService.finish(userId, finish.course(), finish.millis());
              } catch (RuntimeException e) {
                log.warn("[World] course record failed: {}", e.getMessage());
                return;
              }
              WorldOutbox outbox = sessions.get(id);
              if (outbox == null) {
                return;
              }
              var payload = new HashMap<String, Object>();
              payload.put("t", "record");
              payload.put("course", finish.course().region());
              payload.put("ms", result.timeMs());
              payload.put("best", result.bestMs());
              payload.put("rank", result.rank());
              payload.put("saved", result.saved());
              payload.put("reward", result.reward());
              outbox.offer(jsonMapper.writeValueAsString(payload));
            });
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    cleanup(session);
  }

  /**
   * 소켓을 닫고 곧바로 정리한다. 정리는 컨테이너의 닫힘 알림({@link #afterConnectionClosed})을 기다리지 않는다: 받지 않는 클라이언트에게는 닫기
   * 프레임도 늦게 가거나 못 갈 수 있고, 그 사이 다른 사람들에게 남은 아바타(유령)가 보이면 안 되기 때문이다. 닫기 자체(네트워크 쓰기)는 가상 스레드에서 해 부른 쪽을
   * 붙잡지 않는다.
   */
  void disconnect(WebSocketSession session, CloseStatus status) {
    cleanup(session);
    Runnable close =
        () -> {
          try {
            session.close(status);
          } catch (IOException | RuntimeException e) {
            log.debug("[World] close failed: {}", e.getMessage());
          }
        };
    try {
      sendExecutor.execute(close);
    } catch (RejectedExecutionException e) {
      close.run();
    }
  }

  /** 자리·IP 몫 반납, 퇴장 알림. 여러 번 불려도 한 번만 한다 (속성을 꺼내며 확인). */
  private void cleanup(WebSocketSession session) {
    String ip = (String) session.getAttributes().remove(CLIENT_IP);
    if (ip != null) {
      connectionLimiter.release(ip);
    }
    String id = (String) session.getAttributes().remove(PLAYER_ID);
    if (id == null) {
      return;
    }
    sessions.remove(id);
    courseTimer.forget(id);
    lobby
        .leave(id)
        .ifPresent(channel -> broadcastToChannel(channel, Map.of("t", "leave", "id", id), id));
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.debug("[World] transport error: {}", exception.getMessage());
  }

  /** 채널마다 움직인 사람들을 한 메시지로 묶어 보낸다. 받는 쪽은 자기 id를 무시한다. 줄에 넣기만 하므로 느린 소켓이 있어도 바로 끝난다. */
  @Scheduled(fixedRate = TICK_MS)
  void flushMoves() {
    for (WorldLobby.ChannelKey channel : lobby.channelNumbers()) {
      List<WorldPlayer> moved = lobby.drainMoves(channel);
      if (!moved.isEmpty()) {
        broadcastToChannel(channel, Map.of("t", "moves", "ps", moved), null);
      }
    }
  }

  /** 아무것도 보내지 않고 자리만 차지하는 연결 정리 (IP당 제한만으로는 여러 IP로 정원을 채울 수 있다). */
  @Scheduled(fixedRate = IDLE_SWEEP_MS)
  void closeIdleSessions() {
    long now = now();
    sessions
        .values()
        .forEach(
            outbox -> {
              WebSocketSession session = outbox.session();
              if (isIdle(session.getAttributes(), now)) {
                disconnect(session, CloseStatus.POLICY_VIOLATION.withReason("idle"));
              }
            });
  }

  static boolean isIdle(Map<String, Object> attributes, long now) {
    Long lastSeen = (Long) attributes.get(LAST_SEEN);
    if (lastSeen == null) {
      Long connectedAt = (Long) attributes.get(CONNECTED_AT);
      return connectedAt != null && now - connectedAt > FIRST_MESSAGE_TIMEOUT_MS;
    }
    return now - lastSeen > IDLE_TIMEOUT_MS;
  }

  @Scheduled(fixedRate = ONLINE_MS)
  void broadcastOnlineCount() {
    if (sessions.isEmpty()) {
      return;
    }
    String json =
        jsonMapper.writeValueAsString(
            Map.of("t", "online", "n", lobby.totalPlayers(), "regions", lobby.playersPerRegion()));
    sessions.values().forEach(outbox -> outbox.offer(json));
  }

  private void broadcastToChannel(WorldLobby.ChannelKey channel, Object payload, String exceptId) {
    String json = jsonMapper.writeValueAsString(payload);
    for (WorldPlayer player : lobby.playersIn(channel)) {
      if (player.id().equals(exceptId)) {
        continue;
      }
      WorldOutbox outbox = sessions.get(player.id());
      if (outbox != null) {
        outbox.offer(json);
      }
    }
  }

  /** Tomcat이 한 번의 쓰기를 기다리는 시간을 줄인다 (기본 20초). Tomcat이 아니면 아무것도 하지 않는다. */
  private static void limitBlockingSend(WebSocketSession session) {
    if (session instanceof NativeWebSocketSession nativeSession) {
      jakarta.websocket.Session ws =
          nativeSession.getNativeSession(jakarta.websocket.Session.class);
      if (ws != null) {
        ws.getUserProperties().put(TOMCAT_BLOCKING_SEND_TIMEOUT, BLOCKING_SEND_TIMEOUT_MS);
      }
    }
  }

  // --- 애플리케이션 시작·종료 ---

  @Override
  public void start() {
    running = true;
  }

  /**
   * 웹 서버보다 먼저 멈춘다 (기본 phase가 웹 서버보다 높다). 모든 소켓을 1001(GOING_AWAY)로 닫아 클라이언트가 "서버가 내려간다"를 알고 곧 다시 붙게
   * 하고, 닫기가 끝나기를 잠깐 기다린다.
   */
  @Override
  public void stop() {
    running = false;
    List.copyOf(sessions.values())
        .forEach(
            outbox -> {
              WebSocketSession session = outbox.session();
              Runnable close =
                  () -> {
                    try {
                      session.close(CloseStatus.GOING_AWAY);
                    } catch (IOException | RuntimeException e) {
                      log.debug("[World] shutdown close failed: {}", e.getMessage());
                    }
                  };
              try {
                sendExecutor.execute(close);
              } catch (RejectedExecutionException e) {
                close.run();
              }
            });
    if (sendExecutor instanceof ExecutorService executor) {
      executor.shutdown();
      try {
        executor.awaitTermination(SHUTDOWN_CLOSE_WAIT_MS, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  /** 테스트용: 보내기를 맡을 실행기를 바꾼다 */
  void useSendExecutor(Executor executor) {
    this.sendExecutor = executor;
  }

  /**
   * 접속한 사람의 IP. 운영에서는 Caddy가 앞에 있어 X-Forwarded-For의 마지막 값(Caddy가 직접 본 주소)을 쓴다. 앞쪽 값은 클라이언트가 꾸밀 수
   * 있으므로 쓰지 않는다.
   */
  static String clientIp(WebSocketSession session) {
    var remote = session.getRemoteAddress();
    var address = remote == null ? null : remote.getAddress();
    if (address == null) {
      return "unknown";
    }
    // X-Forwarded-For is only believed from our own proxy (same host or docker network);
    // a client reaching the app directly could otherwise claim any address and dodge the limit
    boolean fromProxy = address.isLoopbackAddress() || address.isSiteLocalAddress();
    String forwarded = session.getHandshakeHeaders().getFirst("X-Forwarded-For");
    if (fromProxy && forwarded != null && !forwarded.isBlank()) {
      String[] hops = forwarded.split(",");
      return hops[hops.length - 1].strip();
    }
    return address.getHostAddress();
  }

  private static long now() {
    return System.currentTimeMillis();
  }

  private static String queryParam(WebSocketSession session, String key) {
    if (session.getUri() == null) {
      return null;
    }
    String raw =
        UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst(key);
    if (raw == null) {
      return null;
    }
    try {
      return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return null; // 깨진 인코딩이면 기본값
    }
  }
}
