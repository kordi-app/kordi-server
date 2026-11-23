package com.kordi_api.score.dto;

/**
 * 퀴즈 한 판을 기록한 결과.
 *
 * @param best 이 난이도의 저장된 최고 점수 (이번 판 반영 후, 기록이 없으면 0)
 * @param rank 이번 점수가 랭킹에서 차지할 순위
 * @param improved 최고 기록으로 저장됐는지 (첫 기록이거나 갱신했을 때)
 */
public record QuizRecordResult(int best, long rank, boolean improved) {}
