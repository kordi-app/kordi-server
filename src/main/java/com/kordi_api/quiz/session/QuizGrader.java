package com.kordi_api.quiz.session;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.quiz.QuizRules;
import com.kordi_api.quiz.judge.ChordValidator;
import com.kordi_api.quiz.session.dto.QuizAnswerRequest;
import com.kordi_api.quiz.session.dto.QuizQuestionResult;
import java.util.ArrayList;
import java.util.List;

/**
 * 퀴즈 한 판 채점. 정답 판정({@link ChordValidator})과 점수는 서버가 정하고, 클라이언트가 보낸 것 중 믿는 것은 "무슨 음을 눌렀나"와 "몇 ms
 * 걸렸나"뿐이다. 그 시간이 서버가 잰 시간과 어긋나면 판 전체를 거부한다.
 *
 * <p>거부(400) 조건:
 *
 * <ul>
 *   <li>답 수 ≠ 문제 수
 *   <li>어떤 답의 elapsedMs가 [0, 제한 시간] 밖, 음이 MIDI 범위(0~127) 밖이거나 너무 많음
 *   <li>elapsedMs 합 + 카운트다운 > 세션 발급부터 제출까지 서버가 잰 시간 + {@link QuizRules#ELAPSED_SLACK_MS}
 * </ul>
 *
 * <p>{@link QuizRules#MIN_HUMAN_ANSWER_MS}보다 빠른 정답은 거부하지 않고 오답(0점)으로 친다.
 */
public final class QuizGrader {

  private QuizGrader() {}

  public record Graded(List<QuizQuestionResult> results, int totalScore, int correctCount) {}

  /**
   * @param chordNames 세션에 낸 문제의 코드 이름 (순서대로)
   * @param answers 클라이언트가 낸 답
   * @param wallMs 세션 발급부터 지금까지 서버가 잰 시간
   */
  public static Graded grade(
      List<String> chordNames, List<QuizAnswerRequest> answers, long wallMs) {
    if (answers == null || answers.size() != chordNames.size()) {
      throw invalid("answer count does not match the question count");
    }
    long elapsedSum = 0;
    for (QuizAnswerRequest answer : answers) {
      if (answer == null
          || answer.elapsedMs() < 0
          || answer.elapsedMs() > QuizRules.TIME_PER_QUESTION_MS) {
        throw invalid("elapsedMs out of range");
      }
      List<Integer> notes = answer.notes();
      if (notes == null
          || notes.size() > QuizRules.MAX_NOTES_PER_ANSWER
          || notes.stream().anyMatch(n -> n == null || n < 0 || n > 127)) {
        throw invalid("notes out of range");
      }
      elapsedSum += answer.elapsedMs();
    }
    if (elapsedSum + QuizRules.COUNTDOWN_MS > wallMs + QuizRules.ELAPSED_SLACK_MS) {
      throw invalid("answers took longer than the session has existed");
    }

    List<QuizQuestionResult> results = new ArrayList<>(answers.size());
    int total = 0;
    int correctCount = 0;
    for (int i = 0; i < answers.size(); i++) {
      QuizAnswerRequest answer = answers.get(i);
      boolean correct =
          answer.elapsedMs() >= QuizRules.MIN_HUMAN_ANSWER_MS
              && ChordValidator.validate(chordNames.get(i), answer.notes());
      int score =
          correct
              ? QuizRules.scoreForTimeLeft(QuizRules.TIME_PER_QUESTION_MS - answer.elapsedMs())
              : 0;
      results.add(new QuizQuestionResult(correct, score));
      total += score;
      if (correct) {
        correctCount++;
      }
    }
    return new Graded(List.copyOf(results), total, correctCount);
  }

  private static CoreException invalid(String detail) {
    return new CoreException(CoreExceptionCode.INVALID_REQUEST, detail);
  }
}
