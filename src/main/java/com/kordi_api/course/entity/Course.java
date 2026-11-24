package com.kordi_api.course.entity;

import com.kordi_api.world.WorldContract;
import java.util.Arrays;
import java.util.Optional;

/**
 * 홈 월드의 점프 코스. 골 높이와 최소 기록은 world-contract.json의 course.courses에 있고, 클라이언트
 * widgets/piano-world/engine/course.ts의 COURSES가 같은 파일로 맞춘다.
 *
 * <p>goalY: 골 발판 윗면 높이 (여기까지 올라오면 완주). minMillis: 사람이 낼 수 있는 가장 빠른 기록 근처의 하한 (칸 수 × 한 칸 최소 ≈
 * 0.55초), 이보다 빠르면 조작으로 보고 버린다.
 */
public enum Course {
  /** 26칸, 0.7씩 상승 → 골 0.8 + 26 × 0.7 */
  CLOUDS("clouds"),
  /** 22칸, 0.75씩 상승 → 골 0.8 + 22 × 0.75 */
  NIGHT("night"),
  /** 폭풍의 신전: 30칸, 0.8씩 상승 → 골 0.8 + 30 × 0.8. 오르내리는 발판·종 발판·용의 불길 때문에 기다려야 하는 구간이 있다 */
  RUINS("ruins");

  private final String region;
  private final double goalY;
  private final long minMillis;

  Course(String region) {
    WorldContract.CourseGoal goal = WorldContract.RULES.course().courses().get(region);
    if (goal == null) {
      throw new IllegalStateException("world-contract.json has no course " + region);
    }
    this.region = region;
    this.goalY = goal.goalY();
    this.minMillis = goal.minMillis();
  }

  public String region() {
    return region;
  }

  public double goalY() {
    return goalY;
  }

  public long minMillis() {
    return minMillis;
  }

  public static Optional<Course> ofRegion(String region) {
    return Arrays.stream(values()).filter(c -> c.region.equals(region)).findFirst();
  }
}
