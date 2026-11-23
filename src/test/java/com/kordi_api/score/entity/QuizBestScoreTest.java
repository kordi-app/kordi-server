package com.kordi_api.score.entity;

import static com.kordi_api.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.quiz.entity.Difficulty;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuizBestScoreTest {

  private static final Instant T0 = Instant.parse("2026-11-23T00:00:00Z");
  private static final Instant T1 = T0.plusSeconds(60);

  @Test
  void 더_높은_점수만_갱신한다() {
    QuizBestScore best = QuizBestScore.first(user(1L), Difficulty.EASY, 500, 4, 10, T0);

    assertThat(best.improve(400, 5, 10, T1)).isFalse();
    assertThat(best.improve(600, 5, 10, T1)).isTrue();
    assertThat(best.getTotalScore()).isEqualTo(600);
    assertThat(best.getCorrectCount()).isEqualTo(5);
    assertThat(best.getAchievedAt()).isEqualTo(T1);
  }

  @Test
  void 동점이면_먼저_낸_기록을_지킨다() {
    QuizBestScore best = QuizBestScore.first(user(1L), Difficulty.EASY, 500, 4, 10, T0);

    assertThat(best.improve(500, 6, 10, T1)).isFalse();
    assertThat(best.getAchievedAt()).isEqualTo(T0);
  }
}
