package com.kordi_api.world;

/**
 * 사람이 낼 수 있는 움직임인지 보는 예산 (world-contract.json의 movement). 수평 이동과 오르기에 각각 초당 한도만큼 예산이 쌓이고(최대 {@code
 * burstSeconds}분), 움직인 만큼 쓴다. 예산이 {@code slack}보다 더 마이너스가 되면 순간이동이나 비행처럼 사람이 낼 수 없는 움직임이다. 내려가는 건
 * 제한하지 않는다 (떨어지는 건 누구나 빠르다).
 *
 * <p>점프 코스 판정({@link CourseTimer})과 보상 지역의 여정 검증({@link WorldLobby})이 같이 쓴다. 한 사람에 하나, 한 스레드에서만 쓴다.
 */
final class MovementBudget {

  private static final WorldContract.Movement LIMITS = WorldContract.RULES.movement();

  /** 점프 코스: 짧게 (네트워크 지연만큼) */
  static final double COURSE_BURST = LIMITS.burstSeconds();

  /** 보상 지역의 여정: 길게 (끊겼던 연결이 풀리며 몰려 오는 위치까지) */
  static final double JOURNEY_BURST = LIMITS.journeyBurstSeconds();

  private final double burst;

  private double x;
  private double y;
  private double z;
  private long at;
  private double horizontal;
  private double rise;

  /**
   * @param burst 예산이 최대 몇 초분 쌓이나 ({@link #COURSE_BURST}, {@link #JOURNEY_BURST})
   */
  MovementBudget(double x, double y, double z, long now, double burst) {
    this.burst = burst;
    moveTo(x, y, z, now);
    refill();
  }

  /** 예산을 가득 채운다 (판정을 새로 시작할 때) */
  void refill() {
    horizontal = LIMITS.maxHorizontalSpeed() * burst;
    rise = LIMITS.maxRiseSpeed() * burst;
  }

  /** 예산을 쓰지 않고 옮긴다 (정해진 자리로 되살아날 때) */
  void moveTo(double x, double y, double z, long now) {
    this.x = x;
    this.y = y;
    this.z = z;
    this.at = now;
  }

  /** 이번 위치까지 예산 안에서 왔나. 위치는 어느 쪽이든 이번 것으로 바뀐다. */
  boolean step(double nx, double ny, double nz, long now) {
    double dt = Math.max(0, now - at) / 1000.0;
    horizontal =
        Math.min(LIMITS.maxHorizontalSpeed() * burst, horizontal + LIMITS.maxHorizontalSpeed() * dt)
            - Math.hypot(nx - x, nz - z);
    rise =
        Math.min(LIMITS.maxRiseSpeed() * burst, rise + LIMITS.maxRiseSpeed() * dt)
            - Math.max(0, ny - y);
    moveTo(nx, ny, nz, now);
    return horizontal >= -LIMITS.slack() && rise >= -LIMITS.slack();
  }
}
