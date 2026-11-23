package com.kordi_api.course.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 홈 월드의 점프 코스. 클라이언트 widgets/piano-world/engine/course.ts의 COURSES와 짝
 * (engine/course-contract.test.ts가 이 값들을 고정).
 *
 * @param region 코스가 있는 월드 지역 이름 (WorldLobby.REGIONS)
 * @param goalY 골 발판 윗면 높이: 여기까지 올라오면 완주
 * @param minMillis 사람이 낼 수 있는 가장 빠른 기록 근처의 하한 (칸 수 × 한 칸 최소 ≈ 0.55초). 이보다 빠르면 조작으로 보고 버린다
 */
public enum Course {
  /** 26칸, 0.7씩 상승 → 골 0.8 + 26 × 0.7 */
  CLOUDS("clouds", 19.0, 15_000),
  /** 22칸, 0.75씩 상승 → 골 0.8 + 22 × 0.75 */
  NIGHT("night", 17.3, 13_000);

  private final String region;
  private final double goalY;
  private final long minMillis;

  Course(String region, double goalY, long minMillis) {
    this.region = region;
    this.goalY = goalY;
    this.minMillis = minMillis;
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
