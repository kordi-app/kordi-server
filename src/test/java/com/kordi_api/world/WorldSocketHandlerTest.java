package com.kordi_api.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kordi_api.character.dto.AppearanceDto;
import com.kordi_api.character.dto.CharacterResponse;
import com.kordi_api.character.service.CharacterService;
import com.kordi_api.course.service.CourseRecordService;
import com.kordi_api.global.store.InMemoryExpiringStoreFactory;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.SessionLimitExceededException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 네트워크 없이 가짜 소켓으로 핸들러의 보내기·닫기·정리 동작을 확인한다. */
class WorldSocketHandlerTest {

  private final JsonMapper json = JsonMapper.builder().build();
  private final WorldLobby lobby = new WorldLobby();
  private final ConnectionLimiter limiter = new ConnectionLimiter();
  private final WorldTicketService tickets =
      new WorldTicketService(new InMemoryExpiringStoreFactory(Clock.systemUTC()));
  private final CharacterService characterService = mock(CharacterService.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private WorldSocketHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new WorldSocketHandler(
            lobby,
            new CourseTimer(),
            tickets,
            mock(CourseRecordService.class),
            characterService,
            limiter,
            json,
            userRepository);
    handler.useSendExecutor(Runnable::run); // 보내기를 부른 자리에서 바로 (결정적)
    handler.start();
  }

  @AfterEach
  void tearDown() {
    handler.useSendExecutor(Runnable::run);
  }

  @Test
  void 버퍼_한도_초과로_전송이_실패한_소켓만_닫히고_자리와_IP몫이_반납되며_퇴장이_알려진다() throws Exception {
    FakeSession alice = connect("1.1.1.1", "alice");
    FakeSession slow = connect("2.2.2.2", "slow");
    slow.failWith = new SessionLimitExceededException("buffer", CloseStatus.SESSION_NOT_RELIABLE);
    String slowId = idOf(slow);

    // 느린 소켓에게도 가는 방송이 예외를 던지지 않고, 다른 사람은 계속 받는다
    lobby.move(idOf(alice), new WorldMove(1.0, 0.0, 0.0, 0.0, null, null), 1_000);
    handler.flushMoves();

    assertThat(slow.closedWith).isNotNull();
    assertThat(lobby.totalPlayers()).isEqualTo(1);
    assertThat(alice.types()).contains("moves", "leave");
    assertThat(alice.last("leave").path("id").asString()).isEqualTo(slowId);

    // IP 몫이 반납되어 같은 IP로 정원까지 다시 들어올 수 있다
    for (int i = 0; i < ConnectionLimiter.MAX_PER_IP; i++) {
      assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();
    }
    assertThat(limiter.tryAcquire("2.2.2.2")).isFalse();

    // 컨테이너가 나중에 닫힘을 알려도 두 번 정리하지 않는다
    int leaves = alice.count("leave");
    handler.afterConnectionClosed(slow, CloseStatus.SESSION_NOT_RELIABLE);
    assertThat(alice.count("leave")).isEqualTo(leaves);
  }

  @Test
  void 받지_않는_클라이언트가_있어도_이동_묶음_타이머는_막히지_않는다() throws Exception {
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    handler.useSendExecutor(executor);
    try {
      FakeSession alice = connect("1.1.1.1", "alice");
      FakeSession stuck = connect("2.2.2.2", "stuck");
      awaitType(alice, "welcome");
      stuck.block = new CountDownLatch(1);

      String a = idOf(alice);
      long started = System.nanoTime();
      for (int i = 0; i < 20; i++) {
        lobby.move(a, new WorldMove((double) i, 0.0, 0.0, 0.0, null, null), 1_000 + i * 100L);
        handler.flushMoves();
      }
      long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

      assertThat(tookMs).isLessThan(1_000);
      awaitCount(alice, "moves", 20);
      stuck.block.countDown();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void 너무_많이_밀린_소켓은_닫히고_정리된다() throws Exception {
    FakeSession alice = connect("1.1.1.1", "alice");
    // 보내기 작업이 한 번도 돌지 못한 채(받지 않는 클라이언트) 쌓이기만 하는 상황
    List<Runnable> parked = new CopyOnWriteArrayList<>();
    handler.useSendExecutor(parked::add);
    FakeSession stuck = connect("2.2.2.2", "stuck");

    // 접속자 수 알림(5초마다 모두에게)만으로도 몇 분이면 한도를 넘는다
    for (int i = 0; i < 2_000 && lobby.totalPlayers() == 2; i++) {
      handler.broadcastOnlineCount();
    }
    // 자리는 곧바로 비우고, 닫기(네트워크 쓰기)는 보내기 실행기에 맡겨져 있다
    assertThat(lobby.totalPlayers()).isEqualTo(1);
    parked.forEach(Runnable::run);

    assertThat(stuck.closedWith).isNotNull();
    assertThat(stuck.closedWith.getCode()).isEqualTo(CloseStatus.SESSION_NOT_RELIABLE.getCode());
    assertThat(lobby.totalPlayers()).isEqualTo(1);
    assertThat(alice.closedWith).isNull();
  }

  @Test
  void 입장권으로_로그인을_밝히면_이름이_DB_닉네임으로_바뀌어_채널에_알려진다() throws Exception {
    FakeSession alice = connect("1.1.1.1", "alice");
    FakeSession faker = connect("2.2.2.2", "alice");
    User user = User.builder().nickname("진짜닉네임이아주아주긴이름").build();
    given(userRepository.findById(7L)).willReturn(Optional.of(user));
    given(characterService.get(anyLong()))
        .willReturn(
            new CharacterResponse(new AppearanceDto("cat", null, "smile", null, null), List.of()));

    String ticket = tickets.issue(7L);
    handler.handleTextMessage(
        faker, new TextMessage("{\"t\":\"auth\",\"ticket\":\"" + ticket + "\"}"));

    JsonNode look = alice.last("look");
    assertThat(look.path("id").asString()).isEqualTo(idOf(faker));
    assertThat(look.path("name").asString()).isEqualTo("진짜닉네임이아주아주긴이");
    assertThat(look.path("look").path("body").asString()).isEqualTo("cat");
    assertThat(faker.last("look").path("name").asString()).isEqualTo("진짜닉네임이아주아주긴이");
    assertThat(lobby.playersIn(new WorldLobby.ChannelKey("hall", 1)))
        .filteredOn(p -> p.id().equals(idOf(faker)))
        .singleElement()
        .extracting(WorldPlayer::name)
        .isEqualTo("진짜닉네임이아주아주긴이");
  }

  @Test
  void 가짜_입장권이면_이름이_바뀌지_않는다() throws Exception {
    FakeSession alice = connect("1.1.1.1", "alice");
    handler.handleTextMessage(alice, new TextMessage("{\"t\":\"auth\",\"ticket\":\"nope\"}"));
    assertThat(alice.types()).doesNotContain("look");
  }

  @Test
  void 핑을_보내면_유휴로_닫히지_않는다() throws Exception {
    FakeSession pinger = connect("1.1.1.1", "pinger");
    FakeSession silent = connect("2.2.2.2", "silent");
    long now = System.currentTimeMillis();
    // 둘 다 접속한 지 한참 지났고 마지막 메시지도 오래전
    for (FakeSession s : List.of(pinger, silent)) {
      s.getAttributes().put("connectedAt", now - 10 * 60_000);
      s.getAttributes().put("lastSeen", now - WorldSocketHandler.IDLE_TIMEOUT_MS - 1_000);
    }
    String silentId = idOf(silent);
    handler.handleTextMessage(pinger, new TextMessage("{\"t\":\"ping\"}"));

    handler.closeIdleSessions();

    assertThat(pinger.closedWith).isNull();
    assertThat(silent.closedWith).isNotNull();
    assertThat(lobby.totalPlayers()).isEqualTo(1);
    assertThat(pinger.last("leave").path("id").asString()).isEqualTo(silentId);
  }

  @Test
  void 멈출_때_모든_소켓을_1001로_닫고_새_접속은_받지_않는다() throws Exception {
    FakeSession alice = connect("1.1.1.1", "alice");
    FakeSession bob = connect("2.2.2.2", "bob");

    handler.stop();

    assertThat(alice.closedWith.getCode()).isEqualTo(CloseStatus.GOING_AWAY.getCode());
    assertThat(bob.closedWith.getCode()).isEqualTo(CloseStatus.GOING_AWAY.getCode());
    FakeSession late = connect("3.3.3.3", "late");
    assertThat(late.closedWith.getCode()).isEqualTo(CloseStatus.GOING_AWAY.getCode());
    assertThat(handler.isRunning()).isFalse();
  }

  // --- helpers ---

  private FakeSession connect(String ip, String name) throws Exception {
    FakeSession session = new FakeSession(ip, name);
    handler.afterConnectionEstablished(session);
    return session;
  }

  private static String idOf(FakeSession session) {
    return (String) session.getAttributes().get("playerId");
  }

  private static void awaitType(FakeSession s, String type) throws InterruptedException {
    awaitCount(s, type, 1);
  }

  private static void awaitCount(FakeSession s, String type, int n) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5_000;
    while (s.count(type) < n && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
    assertThat(s.count(type)).isGreaterThanOrEqualTo(n);
  }

  /** 받은 메시지를 모아 두는 가짜 소켓. 전송을 실패시키거나 멈춰 세울 수 있다. */
  private final class FakeSession implements WebSocketSession {
    final Map<String, Object> attributes = new ConcurrentHashMap<>();
    final List<String> sent = new CopyOnWriteArrayList<>();
    final String ip;
    final String name;
    volatile RuntimeException failWith;
    volatile CountDownLatch block;
    volatile CloseStatus closedWith;

    FakeSession(String ip, String name) {
      this.ip = ip;
      this.name = name;
    }

    List<String> types() {
      return sent.stream().map(m -> json.readTree(m).path("t").asString()).toList();
    }

    int count(String type) {
      return (int) types().stream().filter(type::equals).count();
    }

    JsonNode last(String type) {
      return sent.reversed().stream()
          .map(json::readTree)
          .filter(n -> type.equals(n.path("t").asString()))
          .findFirst()
          .orElseThrow(() -> new AssertionError("no " + type + " in " + sent));
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) {
      if (failWith != null) {
        throw failWith;
      }
      CountDownLatch latch = block;
      if (latch != null) {
        try {
          latch.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      sent.add(((TextMessage) message).getPayload());
    }

    @Override
    public void close(CloseStatus status) {
      closedWith = status;
    }

    @Override
    public void close() {
      close(CloseStatus.NORMAL);
    }

    @Override
    public boolean isOpen() {
      return closedWith == null;
    }

    @Override
    public Map<String, Object> getAttributes() {
      return attributes;
    }

    @Override
    public URI getUri() {
      return URI.create("ws://localhost/ws/world?region=hall&name=" + name);
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
      return new InetSocketAddress(ip, 40_000);
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
      return new HttpHeaders();
    }

    @Override
    public String getId() {
      return name + "@" + ip;
    }

    @Override
    public Principal getPrincipal() {
      return null;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
      return null;
    }

    @Override
    public String getAcceptedProtocol() {
      return null;
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {}

    @Override
    public int getTextMessageSizeLimit() {
      return 0;
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {}

    @Override
    public int getBinaryMessageSizeLimit() {
      return 0;
    }

    @Override
    public List<WebSocketExtension> getExtensions() {
      return List.of();
    }
  }
}
