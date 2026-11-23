package com.kordi_api.world;

import com.kordi_api.character.service.AppearanceChanged;
import com.kordi_api.character.service.CharacterService;
import com.kordi_api.course.dto.CourseResult;
import com.kordi_api.course.service.CourseRecordService;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
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
 *         <li>{@code welcome}: 내 id, 지역·채널 번호, 같은 채널 접속자, 전체·지역별 접속자 수
 *         <li>{@code join} / {@code leave}: 같은 채널 입·퇴장 (즉시)
 *         <li>{@code moves}: 같은 채널에서 움직인 사람들의 최신 위치 ({@value #TICK_MS}ms마다 묶어서)
 *         <li>{@code online}: 전체·지역별 접속자 수 ({@value #ONLINE_MS}ms마다)
 *       </ul>
 *   <li>{@code look}: 같은 채널 누군가의 꾸민 모습이 바뀜 (회원이 로그인을 밝혔을 때, 꾸미기를 저장했을 때)
 *   <li>{@code record}: 점프 코스 완주 기록 (서버가 잰 시간, 역대 순위, 저장 여부)
 *   <li>클라이언트 → 서버
 *       <ul>
 *         <li>{@link WorldMove} (x, y, z, h, m, e)
 *         <li>{@code {"t":"auth","ticket":...}}: 로그인 유저임을 밝힘 ({@link WorldTicketService})
 *       </ul>
 * </ul>
 *
 * 인증 없이 누구나 입장한다(홈 화면이 공개 페이지). 이름은 표시용이며 검증하지 않는다. 입장권으로 로그인을 밝힌 소켓만 코스 기록이 랭킹에 저장된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorldSocketHandler extends TextWebSocketHandler {

  static final long TICK_MS = 100;
  static final long ONLINE_MS = 5_000;

  private static final String PLAYER_ID = "playerId";
  private static final String USER_ID = "userId";
  private static final String CLIENT_IP = "clientIp";
  private static final String CONNECTED_AT = "connectedAt";
  private static final String LAST_SEEN = "lastSeen";

  /** 정상 클라이언트는 접속하자마자 자기 위치를 보낸다. 이 안에 아무것도 안 보내면 자리만 차지하는 연결로 보고 닫는다. */
  static final long FIRST_MESSAGE_TIMEOUT_MS = 30_000;

  /** 마지막 메시지 이후 이만큼 조용하면 닫는다. 클라이언트는 위치가 바뀔 때만 보내므로 가만히 서 있는 사람도 해당되지만, 끊기면 자동으로 다시 붙는다. */
  static final long IDLE_TIMEOUT_MS = 30 * 60_000;

  static final long IDLE_SWEEP_MS = 10_000;
  private static final int SEND_TIME_LIMIT_MS = 2_000;
  private static final int BUFFER_SIZE_LIMIT = 64 * 1024;
  private static final int MAX_INCOMING_LENGTH = 256;

  private final WorldLobby lobby;
  private final CourseTimer courseTimer;
  private final WorldTicketService ticketService;
  private final CourseRecordService courseRecordService;
  private final CharacterService characterService;
  private final ConnectionLimiter connectionLimiter;
  private final JsonMapper jsonMapper;
  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession rawSession) throws IOException {
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
      rawSession.close(CloseStatus.SERVICE_OVERLOAD.withReason("full"));
      return;
    }
    WorldPlayer me = seat.get().player();
    WorldLobby.ChannelKey channel = seat.get().channel();

    WebSocketSession session =
        new ConcurrentWebSocketSessionDecorator(rawSession, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
    rawSession.getAttributes().put(PLAYER_ID, me.id());
    sessions.put(me.id(), session);

    send(
        session,
        Map.of(
            "t", "welcome",
            "id", me.id(),
            "region", channel.region(),
            "ch", channel.number(),
            "players", List.copyOf(lobby.playersIn(channel)),
            "online", lobby.totalPlayers(),
            "regions", lobby.playersPerRegion()));
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
      if ("auth".equals(node.path("t").asString(""))) {
        ticketService
            .redeem(node.path("ticket").asString(null), now())
            .ifPresent(
                userId -> {
                  session.getAttributes().put(USER_ID, userId);
                  showLook(id, userId);
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

  /** 회원임이 밝혀지면 저장된 모습을 입혀 같은 채널에 알린다. */
  private void showLook(String playerId, Long userId) {
    WorldLook look;
    try {
      look = WorldLook.of(characterService.get(userId).appearance());
    } catch (RuntimeException e) {
      log.debug("[World] look load failed: {}", e.getMessage());
      return;
    }
    dress(playerId, look);
  }

  private void dress(String playerId, WorldLook look) {
    lobby
        .dress(playerId, look)
        .ifPresent(
            channel ->
                broadcastToChannel(
                    channel, Map.of("t", "look", "id", playerId, "look", look), null));
  }

  /**
   * 꾸미기 저장·보상 장착: 그 유저가 지금 월드에 있으면 바로 모습을 바꾼다. DB에 확정된 뒤에만 알린다 (롤백된 변경을 보여주지 않도록, 그리고 소켓 전송 동안 DB
   * 연결을 붙잡지 않도록).
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  void onAppearanceChanged(AppearanceChanged event) {
    WorldLook look = WorldLook.of(event.appearance());
    sessions.forEach(
        (playerId, session) -> {
          if (event.userId().equals(session.getAttributes().get(USER_ID))) {
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
              WebSocketSession session = sessions.get(id);
              if (session == null) {
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
              send(session, payload);
            });
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String ip = (String) session.getAttributes().remove(CLIENT_IP);
    if (ip != null) {
      connectionLimiter.release(ip);
    }
    String id = (String) session.getAttributes().get(PLAYER_ID);
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

  /** 채널마다 움직인 사람들을 한 메시지로 묶어 보낸다. 받는 쪽은 자기 id를 무시한다. */
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
            session -> {
              if (isIdle(session.getAttributes(), now)) {
                try {
                  session.close(CloseStatus.POLICY_VIOLATION.withReason("idle"));
                } catch (IOException | IllegalStateException e) {
                  log.debug("[World] idle close failed: {}", e.getMessage());
                }
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
    sessions.values().forEach(session -> sendRaw(session, json));
  }

  private void broadcastToChannel(WorldLobby.ChannelKey channel, Object payload, String exceptId) {
    String json = jsonMapper.writeValueAsString(payload);
    for (WorldPlayer player : lobby.playersIn(channel)) {
      if (player.id().equals(exceptId)) {
        continue;
      }
      WebSocketSession session = sessions.get(player.id());
      if (session != null) {
        sendRaw(session, json);
      }
    }
  }

  private void send(WebSocketSession session, Object payload) {
    sendRaw(session, jsonMapper.writeValueAsString(payload));
  }

  private void sendRaw(WebSocketSession session, String json) {
    if (!session.isOpen()) {
      return;
    }
    try {
      session.sendMessage(new TextMessage(json));
    } catch (IOException | IllegalStateException e) {
      // 느리거나 끊긴 클라이언트: 버퍼 초과 시 데코레이터가 세션을 닫고, 정리는 afterConnectionClosed가 맡는다
      log.debug("[World] send failed: {}", e.getMessage());
    }
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
