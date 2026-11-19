package com.kordi_api.world;

/** 접속 중인 플레이어의 현재 상태. 클라이언트에 그대로 직렬화된다. */
public record WorldPlayer(String id, String name, double x, double z, double h, Integer m) {

  public WorldPlayer moved(double x, double z, double h, Integer m) {
    return new WorldPlayer(id, name, x, z, h, m);
  }
}
