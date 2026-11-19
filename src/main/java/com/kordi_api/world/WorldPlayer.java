package com.kordi_api.world;

/**
 * 접속 중인 플레이어의 현재 상태. 클라이언트에 그대로 직렬화된다.
 *
 * @param e 진행 중인 감정 표현(wave/clap/dance/sit), 없으면 null
 */
public record WorldPlayer(
    String id, String name, double x, double y, double z, double h, Integer m, String e) {

  public WorldPlayer moved(double x, double y, double z, double h, Integer m, String e) {
    return new WorldPlayer(id, name, x, y, z, h, m, e);
  }
}
