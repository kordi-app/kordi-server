package com.kordi_api.world;

/**
 * 음표 공 하나의 상태. 찬 사람이 보내고({@code {"t":"ball",...}}), 서버가 채널별로 기억했다가 같은 채널에 그대로 전한다. 공의 물리는 각 클라이언트가
 * 같은 공식으로 굴리므로 서버는 계산하지 않는다 (클라이언트 widgets/piano-world/engine/ball.ts).
 *
 * @param i 지역 안 공 번호 (0부터)
 * @param x 위치 x
 * @param y 위치 y (공 중심 높이)
 * @param z 위치 z
 * @param vx 속도 x
 * @param vy 속도 y
 * @param vz 속도 z
 */
public record WorldBall(Integer i, Double x, Double y, Double z, Double vx, Double vy, Double vz) {

  /** 멈춰 있는 공 (굴러가던 공이 선 자리를 알리는 보고) */
  boolean resting() {
    return vx == 0 && vy == 0 && vz == 0;
  }

  /** 입장한 사람에게 보내는 모습: {@code age}는 서버가 이 상태를 받은 지 지난 시간(ms). 그만큼 앞당겨 굴린다 */
  public record View(
      int i, double x, double y, double z, double vx, double vy, double vz, long age) {}
}
