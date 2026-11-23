package com.kordi_api.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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

  /** 홈 월드의 섬(그랜드 홀)이 가장 넓다: 클라이언트 widgets/piano-world/engine/island.ts ISLAND_BOUNDS(|x| ≤ 98, -70 ≤ z ≤ 40)보다 여유 있게 */
  static final double MAX_ABS_X = 110;

  static final double MAX_ABS_Z = 85;
  static final double MAX_Y = 40;
  static final int MIN_MIDI = 21;
  static final int MAX_MIDI = 108;
  static final String DEFAULT_NAME = "손님";
  static final String DEFAULT_REGION = "hall";

  /** 클라이언트 widgets/piano-world/engine/regions.ts의 REGION_IDS와 짝 */
  static final Set<String> REGIONS =
      Set.of("hall", "neon", "cathedral", "lounge", "clouds", "night");

  /** 클라이언트 감정 표현 목록과 짝 */
  static final Set<String> EMOTES = Set.of("wave", "clap", "dance", "sit");

  /** 지역 + 지역 안 채널 번호 */
  public record ChannelKey(String region, int number) {}

  /** 입장 결과: 배정된 채널과 내 플레이어 상태 */
  public record Seat(ChannelKey channel, WorldPlayer player) {}

  private static final class Channel {
    final Map<String, WorldPlayer> players = new ConcurrentHashMap<>();
    final Set<String> dirty = ConcurrentHashMap.newKeySet();
  }

  private final Map<ChannelKey, Channel> channels = new ConcurrentHashMap<>();
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
          return seat;
        });
    return true;
  }

  /** 모습 변경. 그 사람이 있는 채널을 돌려준다 (없으면 empty). */
  public Optional<ChannelKey> dress(String id, WorldLook look) {
    ChannelKey channelNo = channelOf.get(id);
    Channel channel = channelNo == null ? null : channels.get(channelNo);
    if (channel == null) {
      return Optional.empty();
    }
    WorldPlayer updated = channel.players.computeIfPresent(id, (k, p) -> p.dressed(look));
    return updated == null ? Optional.empty() : Optional.of(channelNo);
  }

  /** 퇴장. 떠난 사람이 있던 채널 번호를 돌려준다. */
  public synchronized Optional<ChannelKey> leave(String id) {
    ChannelKey channelNo = channelOf.remove(id);
    lastMoveAt.remove(id);
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
