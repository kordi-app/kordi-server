package com.kordi_api.quiz.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.quiz.QuizRules;
import com.kordi_api.quiz.session.dto.QuizAnswerRequest;
import com.kordi_api.quiz.session.dto.QuizQuestionResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuizGraderTest {

  private static final List<String> CHORDS = List.of("C", "Am", "G7");
  private static final List<Integer> C = List.of(60, 64, 67);
  private static final List<Integer> AM = List.of(57, 60, 64);
  private static final List<Integer> G7 = List.of(55, 59, 62, 65);
  private static final long PLENTY = 600_000;

  @Test
  void 서버가_정답을_판정하고_남은_시간으로_점수를_매긴다() {
    var graded =
        QuizGrader.grade(
            CHORDS, List.of(answer(C, 1_000), answer(C, 2_000), answer(List.of(), 20_000)), PLENTY);

    assertThat(graded.results())
        .containsExactly(
            new QuizQuestionResult(true, QuizRules.scoreForTimeLeft(19_000)),
            new QuizQuestionResult(false, 0), // C played for Am
            new QuizQuestionResult(false, 0)); // timeout
    assertThat(graded.results().get(0).score()).isEqualTo(190);
    assertThat(graded.totalScore()).isEqualTo(190);
    assertThat(graded.correctCount()).isEqualTo(1);
  }

  @Test
  void 점수는_예전_클라이언트_식과_같다() {
    // client: Math.floor((timeLeftMs / 1000) * 10)
    assertThat(QuizRules.scoreForTimeLeft(20_000)).isEqualTo(200);
    assertThat(QuizRules.scoreForTimeLeft(12_345)).isEqualTo(123);
    assertThat(QuizRules.scoreForTimeLeft(99)).isZero();
    assertThat(QuizRules.scoreForTimeLeft(0)).isZero();
  }

  @Test
  void 사람보다_빠른_정답은_인정하지_않는다() {
    var graded =
        QuizGrader.grade(
            CHORDS,
            List.of(
                answer(C, QuizRules.MIN_HUMAN_ANSWER_MS - 1),
                answer(AM, QuizRules.MIN_HUMAN_ANSWER_MS),
                answer(G7, 5_000)),
            PLENTY);

    assertThat(graded.results().get(0)).isEqualTo(new QuizQuestionResult(false, 0));
    assertThat(graded.results().get(1).correct()).isTrue();
    assertThat(graded.correctCount()).isEqualTo(2);
  }

  @Test
  void 답_수가_문제_수와_다르면_거부한다() {
    assertThatThrownBy(() -> QuizGrader.grade(CHORDS, List.of(answer(C, 1_000)), PLENTY))
        .isInstanceOf(CoreException.class);
  }

  @Test
  void 제한_시간_밖의_경과_시간은_거부한다() {
    assertThatThrownBy(
            () ->
                QuizGrader.grade(
                    CHORDS, List.of(answer(C, -1), answer(AM, 1_000), answer(G7, 1_000)), PLENTY))
        .isInstanceOf(CoreException.class);
    assertThatThrownBy(
            () ->
                QuizGrader.grade(
                    CHORDS,
                    List.of(
                        answer(C, QuizRules.TIME_PER_QUESTION_MS + 1),
                        answer(AM, 1_000),
                        answer(G7, 1_000)),
                    PLENTY))
        .isInstanceOf(CoreException.class);
  }

  @Test
  void MIDI_범위_밖의_음은_거부한다() {
    assertThatThrownBy(
            () ->
                QuizGrader.grade(
                    CHORDS,
                    List.of(answer(List.of(60, 128), 1_000), answer(AM, 1_000), answer(G7, 1_000)),
                    PLENTY))
        .isInstanceOf(CoreException.class);
  }

  @Test
  void 경과_시간_합은_카운트다운을_빼고_서버가_잰_시간을_넘을_수_없다() {
    List<QuizAnswerRequest> answers =
        List.of(answer(C, 10_000), answer(AM, 10_000), answer(G7, 10_000));
    long justEnough = 30_000 + QuizRules.COUNTDOWN_MS - QuizRules.ELAPSED_SLACK_MS;

    assertThat(QuizGrader.grade(CHORDS, answers, justEnough).correctCount()).isEqualTo(3);
    assertThatThrownBy(() -> QuizGrader.grade(CHORDS, answers, justEnough - 1))
        .isInstanceOf(CoreException.class);
  }

  private static QuizAnswerRequest answer(List<Integer> notes, int elapsedMs) {
    return new QuizAnswerRequest(notes, elapsedMs);
  }
}
