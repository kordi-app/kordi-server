package com.kordi_api.adventure.dto;

/**
 * @param defeated 이번 판에 물리친 수
 * @param bestDefeated 저장된 최고 기록
 * @param rank 이번 기록이 역대 랭킹에서 차지할 순위
 * @param saved 최고 기록으로 저장됐는지 (첫 기록이거나 갱신했을 때)
 */
public record AdventureResult(int defeated, int bestDefeated, long rank, boolean saved) {}
