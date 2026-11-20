package com.kordi_api.world;

import com.kordi_api.course.entity.Course;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 점프 코스 기록을 서버가 직접 잰다. 클라이언트가 보낸 "기록"은 믿지 않고, 소켓으로 들어오는 위치만 본다.
 *
 * <ul>
 *   <li>시작: 바닥(y ≈ 0)에서 발판 높이로 올라선 순간
 *   <li>리셋: 다시 바닥으로 떨어지면
 *   <li>완주: 골 높이에 닿으면 (시작 이후 계속 정상적인 움직임이었을 때만)
 * </ul>
 *
 * <p>정상 판정: 메시지 사이 이동 거리가 낼 수 있는 속도를 넘으면(순간이동) 그 시도는 무효. 네트워크가 잠깐 멈췄다 몰려 오는 경우를 감안해 짧은 간격은 {@value
 * #MIN_WINDOW_MS}ms로 쳐주고, 긴 공백은 {@value #MAX_WINDOW_MS}ms까지만 쳐준다(클라이언트는 움직이는 동안 매 프레임 보내므로, 오래
 * 조용하다가 멀리 나타나는 건 순간이동이다). 끝으로 코스별 최소 시간보다 빠르면 버린다.
 *
 * <p>한계: 사람이 낼 수 있는 속도 안에서 정교하게 꾸민 위치를 보내는 것까지는 막지 못한다.
 */
@Component
public class CourseTimer {

  /** 걷기 4.2 + 미끄러지는 발판 + 여유 */
  static final double MAX_HORIZONTAL_SPEED = 9;

  /** 점프 7.5, 튕기는 발판 12, 낙하 포함 여유 */
  static final double MAX_VERTICAL_SPEED = 20;

  /** 한 메시지 사이에 올라갈 수 있는 최대 높이: 튕기는 발판 최고점(≈ 3.3)보다 약간 위 */
  static final double MAX_RISE_PER_MESSAGE = 3.5;

  static final long MIN_WINDOW_MS = 250;
  static final long MAX_WINDOW_MS = 1_000;
  static final double FLOOR_Y = 0.3;
  static final double START_Y = 0.5;

  public record Finish(Course course, long millis) {}

  private static final class Run {
    double x;
    double y;
    double z;
    long at;
    Long startedAt;
    boolean valid;
  }

  private final Map<String, Run> runs = new ConcurrentHashMap<>();

  /** 위치 하나를 반영한다. 이번 위치로 완주했으면 결과를 돌려준다. */
  public Optional<Finish> track(
      String playerId, String region, double x, double y, double z, long now) {
    Optional<Course> course = Course.ofRegion(region);
    if (course.isEmpty()) {
      return Optional.empty();
    }
    Run run = runs.get(playerId);
    if (run == null) {
      run = new Run();
      run.x = x;
      run.y = y;
      run.z = z;
      run.at = now;
      runs.put(playerId, run);
      return Optional.empty();
    }

    double window = Math.clamp(now - run.at, MIN_WINDOW_MS, MAX_WINDOW_MS) / 1000.0;
    boolean plausible =
        Math.hypot(x - run.x, z - run.z) <= MAX_HORIZONTAL_SPEED * window
            && Math.abs(y - run.y) <= MAX_VERTICAL_SPEED * window
            && y - run.y <= MAX_RISE_PER_MESSAGE;
    double previousY = run.y;
    run.x = x;
    run.y = y;
    run.z = z;
    run.at = now;

    if (y < FLOOR_Y) {
      run.startedAt = null; // 떨어지면 처음부터
      return Optional.empty();
    }
    if (run.startedAt == null) {
      if (previousY < FLOOR_Y && y >= START_Y) {
        run.startedAt = now;
        run.valid = true;
      }
      return Optional.empty();
    }
    if (!plausible) {
      run.valid = false;
    }
    if (y < course.get().goalY() - 0.05) {
      return Optional.empty();
    }
    long millis = now - run.startedAt;
    boolean counted = run.valid && millis >= course.get().minMillis();
    run.startedAt = null; // 한 번 완주하면 바닥을 다시 밟기 전까지 끝
    return counted ? Optional.of(new Finish(course.get(), millis)) : Optional.empty();
  }

  public void forget(String playerId) {
    runs.remove(playerId);
  }
}
