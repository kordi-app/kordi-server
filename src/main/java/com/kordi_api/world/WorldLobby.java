package com.kordi_api.world;

import com.kordi_api.character.entity.CharacterCatalog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * 홈 화면 3D 피아노 월드의 접속자 관리 (메모리 보관, 서버 1대 기준).
 *
 * <h2>많은 인원을 버티는 방식</h2>
 *
 * <ul>
 *   <li><b>채널 분할</b>: 한 채널 정원은 {@value #CHANNEL_CAPACITY}명. 서로 보이는 건 같은 채널뿐이라 한 사람이 받는 위치 정보가 인원수와
 *       무관하게 상한이 있다. 빈 자리가 있는 가장 낮은 번호 채널에 자동 배정한다.
 *   <li><b>전체 상한</b>: {@value #MAX_TOTAL}명을 넘으면 입장 거절 → 클라이언트는 혼자 모드로 동작.
 *   <li><b>묶음 전송</b>: 이동은 즉시 중계하지 않고 dirty로 표시만 해두고, 핸들러가 주기적으로 채널별 변경분을 한 메시지로 보낸다.
 * </ul>
 *
 * <p>지역(region)마다 채널 번호가 따로 매겨진다: 네온 거리 1채널과 성당 1채널은 서로 다른 방이다. 알 수 없는 지역 이름은 기본 지역({@value
 * #DEFAULT_REGION})으로 취급한다.
 *
 * <p>클라이언트가 보내는 값은 신뢰하지 않는다: 좌표·건반 범위를 벗어나거나 너무 잦은 메시지는 버린다.
 */
@Component
public class WorldLobby {

  static final int CHANNEL_CAPACITY = 30;
  static final int MAX_TOTAL = 1_000;
  static final int MAX_NAME_LENGTH = 12;
  static final long MIN_MOVE_INTERVAL_MS = 30;

  /**
   * 홈 월드의 섬(그랜드 홀)이 가장 넓다: 클라이언트 widgets/piano-world/engine/island.ts ISLAND_BOUNDS(|x| ≤ 98, -70 ≤
   * z ≤ 40)보다 여유 있게 (world-contract.json의 bounds)
   */
  static final double MAX_ABS_X = WorldContract.RULES.bounds().maxAbsX();

  static final double MAX_ABS_Z = WorldContract.RULES.bounds().maxAbsZ();
  static final double MAX_Y = WorldContract.RULES.bounds().maxY();
  static final int MIN_MIDI = 21;
  static final int MAX_MIDI = 108;
  static final String DEFAULT_NAME = "손님";
  static final String DEFAULT_REGION = "hall";

  /** 클라이언트 widgets/piano-world/engine/regions.ts의 REGION_IDS와 짝 (world-contract.json) */
  static final Set<String> REGIONS = Set.copyOf(WorldContract.RULES.regions());

  /**
   * 꿈의 바다(whale) 꼭대기 섬: 클라이언트 engine/dream-sea.ts의 summit (x 2, z -38, 높이 7.2). 고래가 노래했다는 알림은 여기 서
   * 있을 때만 믿는다
   */
  static final String WHALE_REGION = "whale";

  static final double WHALE_SUMMIT_X = WorldContract.RULES.whale().x();

  static final double WHALE_SUMMIT_Z = WorldContract.RULES.whale().z();
  static final double WHALE_SUMMIT_REACH = WorldContract.RULES.whale().reach();
  static final double WHALE_SUMMIT_MIN_Y = WorldContract.RULES.whale().minY();

  /**
   * 별이 잠든 동굴(cave)의 제단: 클라이언트 engine/cave.ts의 ALTAR (x 0, z -52, 제단 단 꼭대기 높이 1.2). 황금 목걸이를 가져갔다는
   * 알림은 여기 서 있고 우주의 왕관을 쓴 사람만 믿는다
   */
  static final String CAVE_REGION = "cave";

  static final double CAVE_ALTAR_X = WorldContract.RULES.cave().altar().x();
  static final double CAVE_ALTAR_Z = WorldContract.RULES.cave().altar().z();
  static final double CAVE_ALTAR_REACH = WorldContract.RULES.cave().altar().reach();
  static final double CAVE_ALTAR_MIN_Y = WorldContract.RULES.cave().altar().minY();

  /**
   * 자정의 종: 그랜드 홀 종탑 꼭대기의 종을 밤에 치면 그 채널에 하늘섬으로 가는 별 계단이 이만큼 열린다. 밤인지는 치는 사람의 시계로 클라이언트가 정한다 (섬의 하늘이
   * 각자의 현지 시각을 따른다).
   */
  static final long SKY_OPEN_MS = 10 * 60_000;

  /** 여러 사람이 연달아 쳐도 채널에 알리는 건 이 간격에 한 번 */
  static final long BELL_COOLDOWN_MS = 5_000;

  /**
   * 클라이언트 engine/island.ts의 BELL(종탑 지붕 위, 지붕 높이 15.4). 위치는 최대 100ms 늦게 오므로 클라이언트의 BELL_REACH(2)보다
   * 넉넉하게 본다
   */
  static final double BELL_X = WorldContract.RULES.bell().x();

  static final double BELL_Z = WorldContract.RULES.bell().z();
  static final double BELL_REACH = WorldContract.RULES.bell().reach();
  static final double BELL_MIN_Y = WorldContract.RULES.bell().minY();

  /**
   * 음표 공이 있는 지역 (클라이언트 engine/ball.ts의 ballSpawns: 점프 코스와 꿈의 바다에는 없다). 지역마다 최대 {@value #MAX_BALLS}개
   */
  static final Set<String> BALL_REGIONS = Set.copyOf(WorldContract.RULES.balls().regions());

  static final int MAX_BALLS = WorldContract.RULES.balls().max();

  /** 발로 찬 공이 낼 수 있는 속도보다 넉넉하게 (클라이언트 기준 보통 10 안팎) */
  static final double MAX_BALL_SPEED = 30;

  /**
   * 공을 찬 사람은 공 곁에 있어야 한다 (몸 반지름 0.42 + 공 반지름 0.35). 위치는 최대 100ms 늦게 오고, 찬 뒤 한 프레임 굴러간 상태가 오므로 넉넉하게
   */
  static final double BALL_REACH = 4;

  /** 한 사람이 공 하나에 대해 소식을 보내는 최소 간격 (찰 때 한 번, 공이 멈추면 한 번). 공마다 따로 센다 */
  static final long MIN_BALL_INTERVAL_MS = 100;

  /**
   * 공은 마지막으로 알려진 상태에서 굴러갈 수 있는 거리(수평 속력 × 지난 시간, 마찰과 벽은 줄이기만 한다)에 이만큼 더한 곳까지만 옮겨질 수 있다. 공을 멀리서 끌어오는
   * 조작을 막는다
   */
  static final double BALL_SLACK = 3;

  /** 클라이언트 감정 표현 목록과 짝 */
  static final Set<String> EMOTES = Set.copyOf(WorldContract.RULES.emotes());

  /** 지역 + 지역 안 채널 번호 */
  public record ChannelKey(String region, int number) {}

  /** 종이 울림: 그 채널과 별 계단이 열려 있을 시간 */
  public record Bell(ChannelKey channel, long openMs) {}

  /** 입장 결과: 배정된 채널과 내 플레이어 상태 */
  public record Seat(ChannelKey channel, WorldPlayer player) {}

  private static final class Channel {
    final Map<String, WorldPlayer> players = new ConcurrentHashMap<>();
    final Set<String> dirty = ConcurrentHashMap.newKeySet();
  }

  /** 채널의 자정 종 상태. 채널과 따로 두어, 잠깐 아무도 없던 채널(혼자 친 사람이 새로고침)도 열린 시간을 잃지 않는다. */
  private static final class Sky {
    long bellAt = Long.MIN_VALUE / 2;
    long until;
  }

  /**
   * 채널의 공 하나: 마지막으로 받은 상태, 그걸 보낸 사람(공이 멈추면 선 자리를 알려줄 사람), 받은 시각. 채널과 따로 두어 아무도 없는 채널에서도 공이 그 자리에
   * 남는다 (메모리에만 있어 서버가 다시 뜨면 처음 자리로 돌아간다).
   */
  private record Ball(WorldBall state, String owner, long at) {}

  /**
   * 보상이 걸린 지역(꿈의 바다, 동굴)에 들어와서부터의 여정. 지역을 옮기면 소켓을 새로 열어 새 id가 되므로 한 id의 여정은 한 지역 안의 한 번의 방문이다.
   *
   * @param clean 들어온 뒤로 계속 사람이 낼 수 있는 움직임이었다 (정해진 되살아나는 자리로의 순간이동은 괜찮다)
   * @param passedCheckpoint 동굴: 끊어진 다리 건너편의 별의 쉼터를 지났다
   */
  private static final class Journey {
    final MovementBudget budget;
    boolean clean = true;
    boolean passedCheckpoint;

    /** 동굴: 켠 별의 쉼터 (그 앞으로만 되살아날 수 있다) */
    final boolean[] restsLit = new boolean[WorldContract.RULES.cave().rests().size()];

    Journey(MovementBudget budget) {
      this.budget = budget;
    }
  }

  /** 여정을 보는 지역 */
  static final Set<String> JOURNEY_REGIONS = Set.of(WHALE_REGION, CAVE_REGION);

  private final Map<ChannelKey, Channel> channels = new ConcurrentHashMap<>();
  private final Map<String, Journey> journeys = new ConcurrentHashMap<>();
  private final Map<ChannelKey, Map<Integer, Ball>> balls = new ConcurrentHashMap<>();
  private final Map<String, Long> lastBallAt = new ConcurrentHashMap<>();
  private final Map<ChannelKey, Sky> skies = new ConcurrentHashMap<>();
  private final Map<String, ChannelKey> channelOf = new ConcurrentHashMap<>();
  private final Map<String, Long> lastMoveAt = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong();

  /** 입장. 전체 정원이 찼으면 empty. */
  public synchronized Optional<Seat> join(String rawRegion, String rawName) {
    if (channelOf.size() >= MAX_TOTAL) {
      return Optional.empty();
    }
    String region = normalizeRegion(rawRegion);
    int number = 1;
    while (isFull(new ChannelKey(region, number))) {
      number++;
    }
    ChannelKey channelNo = new ChannelKey(region, number);
    Channel channel = channels.computeIfAbsent(channelNo, n -> new Channel());

    String id = Long.toString(sequence.incrementAndGet(), 36);
    WorldPlayer player = new WorldPlayer(id, sanitizeName(rawName), 0, 0, 3.6, 0, null, null, null);
    channel.players.put(id, player);
    channelOf.put(id, channelNo);
    return Optional.of(new Seat(channelNo, player));
  }

  /** 이동 반영. 유효하지 않거나 너무 잦은 메시지면 false (무시). */
  public boolean move(String id, WorldMove move, long nowMillis) {
    ChannelKey channelNo = channelOf.get(id);
    if (channelNo == null || !isValid(move)) {
      return false;
    }
    Long last = lastMoveAt.get(id);
    if (last != null && nowMillis - last < MIN_MOVE_INTERVAL_MS) {
      return false;
    }
    Channel channel = channels.get(channelNo);
    if (channel == null) {
      return false;
    }
    double y = move.y() == null ? 0 : move.y();
    String emote = move.e() != null && EMOTES.contains(move.e()) ? move.e() : null;
    // computeIfPresent: a player who left (another thread) mid-move must not be put back as a ghost
    WorldPlayer updated =
        channel.players.computeIfPresent(
            id, (key, current) -> current.moved(move.x(), y, move.z(), move.h(), move.m(), emote));
    if (updated == null) {
      return false;
    }
    channel.dirty.add(id);
    // only while still seated: a leave racing this move must not leave an orphan entry
    channelOf.computeIfPresent(
        id,
        (key, seat) -> {
          lastMoveAt.put(id, nowMillis);
          if (JOURNEY_REGIONS.contains(seat.region())) {
            follow(id, seat.region(), updated, nowMillis);
          }
          return seat;
        });
    return true;
  }

  /**
   * 보상 지역에서의 여정을 따라간다: 사람이 낼 수 있는 움직임이었는지, 동굴이면 체크포인트를 지났는지. 낭떠러지에서 정해진 자리로 되살아나는 순간이동은 예산을 쓰지 않는다.
   */
  private void follow(String id, String region, WorldPlayer at, long nowMillis) {
    Journey journey = journeys.get(id);
    if (journey == null) {
      // 여정은 도착 지점에서 시작한다: 들어오자마자 엉뚱한 곳에 있다고 하면 처음부터 믿지 않는다
      journey =
          new Journey(
              new MovementBudget(at.x(), at.y(), at.z(), nowMillis, MovementBudget.JOURNEY_BURST));
      WorldContract.Spot arrival =
          CAVE_REGION.equals(region)
              ? WorldContract.RULES.cave().arrival()
              : WorldContract.RULES.whale().arrival();
      journey.clean =
          Math.hypot(at.x() - arrival.x(), at.z() - arrival.z())
              <= WorldContract.RULES.movement().arrivalReach();
      journeys.put(id, journey);
    }
    boolean cave = CAVE_REGION.equals(region);
    if (cave && atRespawn(journey, at)) {
      journey.budget.moveTo(at.x(), at.y(), at.z(), nowMillis);
    } else if (!journey.budget.step(at.x(), at.y(), at.z(), nowMillis)) {
      journey.clean = false;
    }
    if (!cave) {
      return;
    }
    WorldContract.Cave rules = WorldContract.RULES.cave();
    for (int i = 0; i < rules.rests().size(); i++) {
      WorldContract.Spot rest = rules.rests().get(i);
      if (Math.hypot(at.x() - rest.x(), at.z() - rest.z()) <= rules.restReach()) {
        journey.restsLit[i] = true;
      }
    }
    if (Math.hypot(at.x() - rules.checkpoint().x(), at.z() - rules.checkpoint().z())
        <= rules.checkpoint().reach()) {
      journey.passedCheckpoint = true;
    }
  }

  /** 낭떠러지에서 돌아오는 자리인가: 입구, 또는 이미 켠 쉼터 앞 (켜지 않은 쉼터로는 되살아날 수 없다) */
  private static boolean atRespawn(Journey journey, WorldPlayer at) {
    WorldContract.Cave rules = WorldContract.RULES.cave();
    double reach = WorldContract.RULES.movement().respawnReach();
    if (Math.hypot(at.x() - rules.arrival().x(), at.z() - rules.arrival().z()) <= reach) {
      return true;
    }
    for (int i = 0; i < rules.rests().size(); i++) {
      WorldContract.Spot rest = rules.rests().get(i);
      if (journey.restsLit[i]
          && Math.hypot(at.x() - rest.x(), at.z() - (rest.z() + rules.respawnOffsetZ())) <= reach) {
        return true;
      }
    }
    return false;
  }

  /** 들어온 뒤로 사람이 낼 수 있는 움직임으로만 왔나 (보상 지역 밖이거나 아직 움직이지 않았으면 false) */
  boolean journeyClean(String id) {
    Journey journey = journeys.get(id);
    return journey != null && journey.clean;
  }

  /** 모습 변경. 그 사람이 있는 채널과 바뀐 상태를 돌려준다 (없으면 empty). */
  public Optional<Seat> dress(String id, WorldLook look) {
    return update(id, p -> p.dressed(look));
  }

  /** 표시 이름 변경 (회원임이 밝혀지면 DB 닉네임으로). 입장 때와 같은 규칙으로 다듬는다. */
  public Optional<Seat> rename(String id, String rawName) {
    String name = sanitizeName(rawName);
    return update(id, p -> p.named(name));
  }

  private Optional<Seat> update(String id, UnaryOperator<WorldPlayer> change) {
    ChannelKey channelNo = channelOf.get(id);
    Channel channel = channelNo == null ? null : channels.get(channelNo);
    if (channel == null) {
      return Optional.empty();
    }
    WorldPlayer updated = channel.players.computeIfPresent(id, (k, p) -> change.apply(p));
    return updated == null ? Optional.empty() : Optional.of(new Seat(channelNo, updated));
  }

  /**
   * 자정의 종. 그랜드 홀에서 종 곁(서버가 마지막으로 받은 위치 기준)에 선 사람만 칠 수 있고, 채널마다 {@value #BELL_COOLDOWN_MS}ms에 한 번만
   * 받는다. 받으면 별 계단이 열린 시간을 새로 잡는다.
   */
  public Optional<Bell> ringBell(String id, long nowMillis) {
    ChannelKey channelNo = channelOf.get(id);
    if (channelNo == null || !DEFAULT_REGION.equals(channelNo.region())) {
      return Optional.empty();
    }
    Channel channel = channels.get(channelNo);
    WorldPlayer player = channel == null ? null : channel.players.get(id);
    if (player == null
        || player.y() < BELL_MIN_Y
        || Math.hypot(player.x() - BELL_X, player.z() - BELL_Z) > BELL_REACH) {
      return Optional.empty();
    }
    Sky sky = skies.computeIfAbsent(channelNo, key -> new Sky());
    synchronized (sky) {
      if (nowMillis - sky.bellAt < BELL_COOLDOWN_MS) {
        return Optional.empty();
      }
      sky.bellAt = nowMillis;
      sky.until = nowMillis + SKY_OPEN_MS;
    }
    return Optional.of(new Bell(channelNo, SKY_OPEN_MS));
  }

  /** 꿈의 바다 꼭대기 섬에 서 있는지 (서버가 마지막으로 받은 위치 기준). */
  public boolean atWhaleSummit(String id) {
    ChannelKey channelNo = channelOf.get(id);
    if (channelNo == null || !WHALE_REGION.equals(channelNo.region())) {
      return false;
    }
    Channel channel = channels.get(channelNo);
    WorldPlayer player = channel == null ? null : channel.players.get(id);
    return player != null
        && journeyClean(id)
        && player.y() >= WHALE_SUMMIT_MIN_Y
        && Math.hypot(player.x() - WHALE_SUMMIT_X, player.z() - WHALE_SUMMIT_Z)
            <= WHALE_SUMMIT_REACH;
  }

  /**
   * 제단의 목걸이를 가져갈 수 있는지와, 안 되면 왜인지 (서버가 마지막으로 받은 위치와 모습 기준). {@link CaveClaim#retryable()}인 이유는 잠시 뒤
   * 다시 물어볼 만하다.
   */
  public CaveClaim checkAltar(String id) {
    ChannelKey channelNo = channelOf.get(id);
    Channel channel = channelNo == null ? null : channels.get(channelNo);
    WorldPlayer player = channel == null ? null : channel.players.get(id);
    if (player == null || !CAVE_REGION.equals(channelNo.region())) {
      return CaveClaim.NOT_IN_CAVE;
    }
    if (player.look() == null || !CharacterCatalog.COSMIC_CROWN.equals(player.look().hat())) {
      return CaveClaim.NO_CROWN;
    }
    Journey journey = journeys.get(id);
    if (journey == null || !journey.clean) {
      return CaveClaim.MOVEMENT;
    }
    if (!journey.passedCheckpoint) {
      return CaveClaim.PATH;
    }
    boolean onAltar =
        player.y() >= CAVE_ALTAR_MIN_Y
            && Math.hypot(player.x() - CAVE_ALTAR_X, player.z() - CAVE_ALTAR_Z) <= CAVE_ALTAR_REACH;
    return onAltar ? CaveClaim.OK : CaveClaim.NOT_ON_ALTAR;
  }

  /** 그 채널의 별 계단이 앞으로 열려 있을 시간 (닫혔으면 0). 나중에 들어온 사람에게 알려준다. */
  public long skyLeft(ChannelKey channelNo, long nowMillis) {
    Sky sky = skies.get(channelNo);
    if (sky == null) {
      return 0;
    }
    synchronized (sky) {
      long left = sky.until - nowMillis;
      if (left <= 0 && nowMillis - sky.bellAt >= BELL_COOLDOWN_MS) {
        skies.remove(channelNo, sky); // 닫힌 지 오래: 정리
      }
      return Math.max(0, left);
    }
  }

  /**
   * 음표 공 소식. 굴러가는 공(찬 순간)은 공 곁에 선 사람만, 멈춘 공(선 자리)은 마지막으로 찬 사람만 알릴 수 있다. 받으면 채널의 공 상태를 바꾸고 그 채널을
   * 돌려준다 (같은 채널에 전할 것). 유효하지 않거나 너무 잦으면 empty.
   */
  public Optional<ChannelKey> moveBall(String id, WorldBall ball, long nowMillis) {
    ChannelKey channelNo = channelOf.get(id);
    if (channelNo == null || !BALL_REGIONS.contains(channelNo.region()) || !isValid(ball)) {
      return Optional.empty();
    }
    String rateKey = ballRateKey(id, ball.i());
    Long last = lastBallAt.get(rateKey);
    if (last != null && nowMillis - last < MIN_BALL_INTERVAL_MS) {
      return Optional.empty();
    }
    Channel channel = channels.get(channelNo);
    WorldPlayer player = channel == null ? null : channel.players.get(id);
    if (player == null) {
      return Optional.empty();
    }
    boolean resting = ball.resting();
    if (!resting && Math.hypot(player.x() - ball.x(), player.z() - ball.z()) > BALL_REACH) {
      return Optional.empty();
    }
    boolean[] taken = {false};
    balls
        .computeIfAbsent(channelNo, key -> new ConcurrentHashMap<>())
        .compute(
            ball.i(),
            (key, current) -> {
              if (resting && (current == null || !id.equals(current.owner()))) {
                return current; // 찬 사람만 공이 선 자리를 정한다
              }
              if (current != null && !reachable(current, ball, nowMillis)) {
                return current; // 공이 거기까지 굴러갈 수는 없다
              }
              taken[0] = true;
              return new Ball(ball, id, nowMillis);
            });
    if (!taken[0]) {
      return Optional.empty();
    }
    lastBallAt.put(rateKey, nowMillis);
    return Optional.of(channelNo);
  }

  private static String ballRateKey(String id, int ball) {
    return id + "#" + ball;
  }

  /** 마지막으로 알려진 상태에서 그 자리까지 굴러갈 수 있었나 */
  private static boolean reachable(Ball current, WorldBall next, long nowMillis) {
    WorldBall from = current.state();
    double seconds = Math.max(0, nowMillis - current.at()) / 1000.0;
    double rolled = Math.hypot(from.vx(), from.vz()) * seconds;
    return Math.hypot(next.x() - from.x(), next.z() - from.z()) <= rolled + BALL_SLACK;
  }

  /** 그 채널 공들의 마지막 상태. 나중에 들어온 사람에게 알려준다 (한 번도 차지 않은 공은 처음 자리이므로 빠진다). */
  public List<WorldBall.View> ballsIn(ChannelKey channelNo, long nowMillis) {
    Map<Integer, Ball> inChannel = balls.get(channelNo);
    if (inChannel == null) {
      return List.of();
    }
    return inChannel.values().stream()
        .map(
            b -> {
              WorldBall s = b.state();
              return new WorldBall.View(
                  s.i(), s.x(), s.y(), s.z(), s.vx(), s.vy(), s.vz(), nowMillis - b.at());
            })
        .toList();
  }

  /** 퇴장. 떠난 사람이 있던 채널 번호를 돌려준다. */
  public synchronized Optional<ChannelKey> leave(String id) {
    ChannelKey channelNo = channelOf.remove(id);
    lastMoveAt.remove(id);
    journeys.remove(id);
    for (int i = 0; i < MAX_BALLS; i++) {
      lastBallAt.remove(ballRateKey(id, i));
    }
    if (channelNo == null) {
      return Optional.empty();
    }
    Channel channel = channels.get(channelNo);
    if (channel != null) {
      channel.players.remove(id);
      channel.dirty.remove(id);
      if (channel.players.isEmpty()) {
        channels.remove(channelNo);
      }
    }
    return Optional.of(channelNo);
  }

  public Collection<WorldPlayer> playersIn(ChannelKey channelNo) {
    Channel channel = channels.get(channelNo);
    return channel == null ? List.of() : List.copyOf(channel.players.values());
  }

  public Set<ChannelKey> channelNumbers() {
    return Set.copyOf(channels.keySet());
  }

  /** 지난 호출 이후 움직인 플레이어들의 최신 상태를 꺼낸다 (꺼낸 뒤 비움). */
  public List<WorldPlayer> drainMoves(ChannelKey channelNo) {
    Channel channel = channels.get(channelNo);
    if (channel == null || channel.dirty.isEmpty()) {
      return List.of();
    }
    List<WorldPlayer> moved = new ArrayList<>();
    for (String id : List.copyOf(channel.dirty)) {
      channel.dirty.remove(id);
      WorldPlayer player = channel.players.get(id);
      if (player != null) {
        moved.add(player);
      }
    }
    return moved;
  }

  public Optional<ChannelKey> channelOf(String id) {
    return Optional.ofNullable(channelOf.get(id));
  }

  public int totalPlayers() {
    return channelOf.size();
  }

  /** 지역별 접속자 수 (아무도 없는 지역은 0) */
  public Map<String, Integer> playersPerRegion() {
    Map<String, Integer> counts = new HashMap<>();
    REGIONS.forEach(region -> counts.put(region, 0));
    channelOf.values().forEach(key -> counts.merge(key.region(), 1, Integer::sum));
    return counts;
  }

  static String normalizeRegion(String raw) {
    return raw != null && REGIONS.contains(raw) ? raw : DEFAULT_REGION;
  }

  private boolean isFull(ChannelKey key) {
    Channel channel = channels.get(key);
    return channel != null && channel.players.size() >= CHANNEL_CAPACITY;
  }

  static String sanitizeName(String raw) {
    if (raw == null) {
      return DEFAULT_NAME;
    }
    String cleaned = raw.replaceAll("[\\p{Cntrl}<>]", "").strip();
    if (cleaned.isEmpty()) {
      return DEFAULT_NAME;
    }
    return cleaned.codePointCount(0, cleaned.length()) > MAX_NAME_LENGTH
        ? cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_NAME_LENGTH))
        : cleaned;
  }

  private static boolean isValid(WorldBall ball) {
    if (ball == null
        || ball.i() == null
        || ball.x() == null
        || ball.y() == null
        || ball.z() == null
        || ball.vx() == null
        || ball.vy() == null
        || ball.vz() == null) {
      return false;
    }
    if (ball.i() < 0 || ball.i() >= MAX_BALLS) {
      return false;
    }
    double[] values = {ball.x(), ball.y(), ball.z(), ball.vx(), ball.vy(), ball.vz()};
    for (double value : values) {
      if (!Double.isFinite(value)) {
        return false;
      }
    }
    return Math.abs(ball.x()) <= MAX_ABS_X
        && Math.abs(ball.z()) <= MAX_ABS_Z
        && ball.y() >= 0
        && ball.y() <= MAX_Y
        && Math.sqrt(ball.vx() * ball.vx() + ball.vy() * ball.vy() + ball.vz() * ball.vz())
            <= MAX_BALL_SPEED;
  }

  private static boolean isValid(WorldMove move) {
    if (move == null || move.x() == null || move.z() == null || move.h() == null) {
      return false;
    }
    if (!Double.isFinite(move.x()) || !Double.isFinite(move.z()) || !Double.isFinite(move.h())) {
      return false;
    }
    if (Math.abs(move.x()) > MAX_ABS_X || Math.abs(move.z()) > MAX_ABS_Z) {
      return false;
    }
    if (move.y() != null && (!Double.isFinite(move.y()) || move.y() < 0 || move.y() > MAX_Y)) {
      return false;
    }
    return move.m() == null || (move.m() >= MIN_MIDI && move.m() <= MAX_MIDI);
  }
}
