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
 * <p>정상 판정: 이동량을 "예산"으로 센다. 시간이 흐른 만큼 낼 수 있는 속도로 예산이 차고(최대 몇백 ms분), 움직인 만큼 깎인다 ({@link
 * MovementBudget}). 그래서 네트워크가 잠깐 멈췄다 몰려 오는 건 괜찮지만, 계속 빠르게 움직이거나 한 번에 멀리 나타나면(순간이동) 예산이 바닥나 그 시도는
 * 무효가 된다. 코스 영역 밖으로 나가도 무효. 끝으로 코스별 최소 시간보다 빠르면 버린다.
 *
 * <p>한계: 서버는 발판 모양을 모르므로, 사람이 낼 수 있는 속도 안에서 꾸민 위치(예: 공중에 떠서 오르기)까지는 막지 못한다. 그래서 최소 시간을 사람의 최고 기록
 * 근처로 잡아 둔다.
 */
@Component
public class CourseTimer {

  /** 코스가 놓인 영역 (클라이언트 course.ts의 bounds보다 약간 넓게) */
  static final double AREA_X = WorldContract.RULES.course().areaX();

  static final double AREA_Z_MIN = WorldContract.RULES.course().areaZMin();
  static final double AREA_Z_MAX = WorldContract.RULES.course().areaZMax();

  static final double FLOOR_Y = WorldContract.RULES.course().floorY();
  static final double START_Y = WorldContract.RULES.course().startY();

  /** 골 발판 높이보다 이만큼 낮아도 완주 */
  static final double GOAL_TOLERANCE = WorldContract.RULES.course().goalTolerance();

  public record Finish(Course course, long millis) {}

  private static final class Run {
    /** 사람이 낼 수 있는 움직임인지 (걷기·점프·튕기는 발판 한도, world-contract.json) */
    final MovementBudget budget;

    Long startedAt;

    /** 시작 전에 바닥을 밟았는지. 직전 샘플만 보면 0.3~0.5 사이 샘플 하나로 시작을 놓친다. */
    boolean touchedFloor;

    boolean valid;

    Run(MovementBudget budget) {
      this.budget = budget;
    }
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
      run = new Run(new MovementBudget(x, y, z, now, MovementBudget.COURSE_BURST));
      run.touchedFloor = y < FLOOR_Y;
      runs.put(playerId, run);
      return Optional.empty();
    }

    boolean plausible =
        run.budget.step(x, y, z, now)
            && Math.abs(x) <= AREA_X
            && z >= AREA_Z_MIN
            && z <= AREA_Z_MAX;

    if (y < FLOOR_Y) {
      run.startedAt = null; // 떨어지면 처음부터
      run.touchedFloor = true;
      return Optional.empty();
    }
    if (run.startedAt == null) {
      if (run.touchedFloor && y >= START_Y) {
        run.touchedFloor = false;
        run.startedAt = now;
        run.valid = true;
        run.budget.refill();
      }
      return Optional.empty();
    }
    if (!plausible) {
      run.valid = false;
    }
    if (y < course.get().goalY() - GOAL_TOLERANCE) {
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
