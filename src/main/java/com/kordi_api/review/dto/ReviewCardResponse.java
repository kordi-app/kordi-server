package com.kordi_api.review.dto;

import com.kordi_api.quiz.dto.QuizChordResponse;
import com.kordi_api.review.entity.ChordReview;
import java.time.Instant;

/**
 * 복습 카드 하나.
 *
 * @param chord 복습할 코드
 * @param box 라이트너 상자 (1~5, 낮을수록 약한 코드)
 * @param dueAt 다음 복습 시각
 * @param due 지금 복습할 때가 됐는지 (아니면 자주 틀려서 채워 넣은 카드)
 */
public record ReviewCardResponse(
    QuizChordResponse chord,
    int correctCount,
    int wrongCount,
    int box,
    Instant dueAt,
    Instant lastSeenAt,
    boolean due) {

  public static ReviewCardResponse of(ChordReview review, Instant now) {
    return new ReviewCardResponse(
        QuizChordResponse.from(review.getChord()),
        review.getCorrectCount(),
        review.getWrongCount(),
        review.getBox(),
        review.getDueAt(),
        review.getLastSeenAt(),
        review.isDue(now));
  }
}
