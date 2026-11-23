package com.kordi_api.quiz;

import java.time.Duration;

/**
 * 코드 퀴즈 게임 규칙 — 서버가 기준이다. 문제 수·제한 시간·카운트다운은 퀴즈 세션 응답({@code QuizSessionResponse})으로 클라이언트에 내려가고,
 * 점수·정답 판정은 서버가 한다.
 *
 * <p>점수 = 정답 시 남은 시간(ms) × {@link #POINTS_PER_SECOND} / 1000, 내림 (문제당 최대 {@link
 * #MAX_SCORE_PER_QUESTION}점)
 */
public final class QuizRules {

  public static final int QUESTIONS_PER_QUIZ = 10;
  public static final int SECONDS_PER_QUESTION = 20;
  public static final int TIME_PER_QUESTION_MS = SECONDS_PER_QUESTION * 1000;
  public static final int POINTS_PER_SECOND = 10;
  public static final int MAX_SCORE_PER_QUESTION = SECONDS_PER_QUESTION * POINTS_PER_SECOND;
  public static final int MAX_TOTAL_SCORE = MAX_SCORE_PER_QUESTION * QUESTIONS_PER_QUIZ;

  /** 첫 문제 전에 클라이언트가 보여주는 카운트다운 (3, 2, 1). 세션 발급 후 이만큼은 문제 시간이 흐르지 않는다. */
  public static final int COUNTDOWN_MS = 3000;

  /**
   * 사람이 문제를 보고 코드를 짚기까지의 하한. 이보다 빠른 정답은 인정하지 않는다.
   *
   * <p>근거: 시각 자극에 대한 단순 반응 시간이 200~250ms이고, 코드 이름을 읽고 3음 이상을 동시에 누르는 선택 반응은 그보다 길다. 게다가 클라이언트는 손이
   * 자리 잡을 때까지 150ms를 기다린 뒤에야 판정하므로(use-quiz-chord-check debounce), 정직한 플레이로는 250ms 미만이 나오기 어렵다.
   */
  public static final int MIN_HUMAN_ANSWER_MS = 250;

  /** 시계 오차·렌더 지연을 위한 여유: 응답의 경과 시간 합이 서버가 잰 시간을 이만큼까지는 넘어도 된다. */
  public static final int ELAPSED_SLACK_MS = 2000;

  /** 한 문제에 기록할 수 있는 최대 음 수 (열 손가락 + 서스테인으로 남은 음) */
  public static final int MAX_NOTES_PER_ANSWER = 24;

  /** 세션 수명: 카운트다운 + 모든 문제 + 여유 (문제 사이 연출, 탭 전환, 제출 재시도) */
  public static final Duration SESSION_TTL =
      Duration.ofMillis(COUNTDOWN_MS + (long) QUESTIONS_PER_QUIZ * TIME_PER_QUESTION_MS)
          .plusMinutes(5);

  private QuizRules() {}

  /** 남은 시간에 대한 정답 점수 (클라이언트의 예전 scoreForTimeLeft와 같은 식) */
  public static int scoreForTimeLeft(long timeLeftMs) {
    if (timeLeftMs <= 0) {
      return 0;
    }
    return (int) (Math.min(timeLeftMs, TIME_PER_QUESTION_MS) * POINTS_PER_SECOND / 1000);
  }
}
