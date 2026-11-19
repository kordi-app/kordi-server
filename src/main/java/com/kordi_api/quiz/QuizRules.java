package com.kordi_api.quiz;

/**
 * 코드 퀴즈 게임 규칙. 클라이언트(features/chord-quiz/model/quiz-rules.ts)와 값이 짝을 이뤄야 한다.
 *
 * <p>점수 = 정답 시 남은 초 × {@link #POINTS_PER_SECOND} (문제당 최대 {@link #MAX_SCORE_PER_QUESTION}점)
 */
public final class QuizRules {

  public static final int QUESTIONS_PER_QUIZ = 10;
  public static final int SECONDS_PER_QUESTION = 20;
  public static final int POINTS_PER_SECOND = 10;
  public static final int MAX_SCORE_PER_QUESTION = SECONDS_PER_QUESTION * POINTS_PER_SECOND;
  public static final int MAX_TOTAL_SCORE = MAX_SCORE_PER_QUESTION * QUESTIONS_PER_QUIZ;

  private QuizRules() {}
}
