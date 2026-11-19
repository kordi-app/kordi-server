package com.kordi_api.score.entity;

import static com.kordi_api.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.quiz.QuizRules;
import com.kordi_api.quiz.entity.Difficulty;
import org.junit.jupiter.api.Test;

class ScoreTest {

  @Test
  void 규칙_범위_안의_점수는_기록된다() {
    int max = 3 * QuizRules.MAX_SCORE_PER_QUESTION;
    Score score = Score.record(user(1L), Difficulty.EASY, max, 3, 10);

    assertThat(score.getTotalScore()).isEqualTo(max);
  }

  @Test
  void 맞힌_개수가_전체보다_많으면_거부된다() {
    assertThatThrownBy(() -> Score.record(user(1L), Difficulty.EASY, 0, 5, 4))
        .isInstanceOf(CoreException.class);
  }

  @Test
  void 맞힌_개수로_얻을_수_있는_최대_점수를_넘으면_거부된다() {
    int overMax = 2 * QuizRules.MAX_SCORE_PER_QUESTION + 1;
    assertThatThrownBy(() -> Score.record(user(1L), Difficulty.EASY, overMax, 2, 10))
        .isInstanceOf(CoreException.class);
  }
}
