package com.kordi_api.quiz.session.dto;

import java.util.List;

/**
 * 서버가 채점한 한 판의 결과.
 *
 * @param results 문제마다 하나씩, 문제 순서대로
 * @param best 이 난이도의 저장된 최고 점수 (이번 판 반영 후, 없으면 0)
 * @param rank 이번 점수가 랭킹에서 차지할 순위
 * @param improved 이번 판으로 최고 기록이 저장(첫 기록 또는 갱신)됐는지
 */
public record QuizResultResponse(
    int totalScore,
    int correctCount,
    int totalCount,
    List<QuizQuestionResult> results,
    int best,
    long rank,
    boolean improved) {}
