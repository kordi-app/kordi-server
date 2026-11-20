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
 * <p>정상 판정: 이동량을 "예산"으로 센다. 시간이 흐른 만큼 낼 수 있는 속도로 예산이 차고(최대 {@value #BURST_SECONDS}초분), 움직인 만큼 깎인다.
 * 그래서 네트워크가 잠깐 멈췄다 몰려 오는 건 괜찮지만, 계속 빠르게 움직이거나 한 번에 멀리 나타나면(순간이동) 예산이 바닥나 그 시도는 무효가 된다. 코스 영역 밖으로
 * 나가도 무효. 끝으로 코스별 최소 시간보다 빠르면 버린다.
 *
 * <p>한계: 서버는 발판 모양을 모르므로, 사람이 낼 수 있는 속도 안에서 꾸민 위치(예: 공중에 떠서 오르기)까지는 막지 못한다. 그래서 최소 시간을 사람의 최고 기록
 * 근처로 잡아 둔다.
 */
@Component
public class CourseTimer {

  /** 걷기 4.2 + 미끄러지는 발판 + 여유 */
  static final double MAX_HORIZONTAL_SPEED = 6;

  /** 오르는 속도: 점프 7.5, 튕기는 발판 12 (내려가는 건 제한 없음: 떨어지면 어차피 리셋) */
  static final double MAX_RISE_SPEED = 12;

  /** 예산은 최대 이만큼의 시간분만 쌓인다 (네트워크 지연 허용치) */
  static final double BURST_SECONDS = 0.6;

  /** 예산이 이보다 더 마이너스면 무효 (소수점·프레임 오차 여유) */
  static final double SLACK = 0.3;

  /** 코스가 놓인 영역 (클라이언트 course.ts의 bounds보다 약간 넓게) */
  static final double AREA_X = 12.5;

  static final double AREA_Z_MIN = -7;
  static final double AREA_Z_MAX = 4.5;

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
    double horizontalBudget;
    double riseBudget;
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

    double dt = Math.max(0, now - run.at) / 1000.0;
    run.horizontalBudget =
        Math.min(
                MAX_HORIZONTAL_SPEED * BURST_SECONDS,
                run.horizontalBudget + MAX_HORIZONTAL_SPEED * dt)
            - Math.hypot(x - run.x, z - run.z);
    run.riseBudget =
        Math.min(MAX_RISE_SPEED * BURST_SECONDS, run.riseBudget + MAX_RISE_SPEED * dt)
            - Math.max(0, y - run.y);
    boolean plausible =
        run.horizontalBudget >= -SLACK
            && run.riseBudget >= -SLACK
            && Math.abs(x) <= AREA_X
            && z >= AREA_Z_MIN
            && z <= AREA_Z_MAX;
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
        run.horizontalBudget = MAX_HORIZONTAL_SPEED * BURST_SECONDS;
        run.riseBudget = MAX_RISE_SPEED * BURST_SECONDS;
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
