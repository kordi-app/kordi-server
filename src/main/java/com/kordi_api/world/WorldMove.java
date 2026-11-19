package com.kordi_api.world;

/**
 * 클라이언트가 보내는 이동 메시지.
 *
 * @param x 바닥 좌표 x
 * @param z 바닥 좌표 z
 * @param h 바라보는 방향(라디안)
 * @param m 밟고 있는 건반의 MIDI 번호, 건반 밖이면 null
 */
public record WorldMove(Double x, Double z, Double h, Integer m) {}
