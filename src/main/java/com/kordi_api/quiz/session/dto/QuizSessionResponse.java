package com.kordi_api.quiz.session.dto;

import com.kordi_api.quiz.QuizRules;
import com.kordi_api.quiz.dto.QuizChordResponse;
import java.util.List;

/**
 * 새 퀴즈 판. 게임 규칙 값은 서버({@link QuizRules})가 정하고 클라이언트는 이 값을 쓴다.
 *
 * @param sessionId 결과를 낼 때 쓰는 id
 * @param timePerQuestionMs 문제당 제한 시간 ({@link QuizRules#TIME_PER_QUESTION_MS})
 * @param countdownMs 첫 문제 전 카운트다운 길이 ({@link QuizRules#COUNTDOWN_MS})
 * @param maxTotalScore 이 판의 만점 ({@link QuizRules#MAX_SCORE_PER_QUESTION} × 문제 수)
 * @param questions 순서대로 낼 문제
 */
public record QuizSessionResponse(
    String sessionId,
    int timePerQuestionMs,
    int countdownMs,
    int maxTotalScore,
    List<QuizChordResponse> questions) {

  public static QuizSessionResponse of(String sessionId, List<QuizChordResponse> questions) {
    return new QuizSessionResponse(
        sessionId,
        QuizRules.TIME_PER_QUESTION_MS,
        QuizRules.COUNTDOWN_MS,
        QuizRules.MAX_SCORE_PER_QUESTION * questions.size(),
        questions);
  }
}
