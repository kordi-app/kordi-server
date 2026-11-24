package com.kordi_api.review.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.quiz.entity.QuizChord;
import com.kordi_api.support.Fixtures;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LeitnerScheduleTest {

  private static final Instant T0 = Instant.parse("2026-11-24T00:00:00Z");

  @Test
  void 처음_맞히면_3번_상자에서_시작하고_처음_틀리면_1번_상자다() {
    assertThat(LeitnerSchedule.nextBox(LeitnerSchedule.NEW_BOX, true)).isEqualTo(3);
    assertThat(LeitnerSchedule.nextBox(LeitnerSchedule.NEW_BOX, false)).isEqualTo(1);
  }

  @Test
  void 맞히면_한_칸_오르고_5번에서_멈추며_틀리면_1번으로_돌아간다() {
    assertThat(LeitnerSchedule.nextBox(1, true)).isEqualTo(2);
    assertThat(LeitnerSchedule.nextBox(4, true)).isEqualTo(5);
    assertThat(LeitnerSchedule.nextBox(5, true)).isEqualTo(5);
    assertThat(LeitnerSchedule.nextBox(5, false)).isEqualTo(1);
  }

  @Test
  void 상자가_높을수록_다음_복습이_멀다() {
    assertThat(LeitnerSchedule.dueAt(1, T0)).isEqualTo(T0);
    assertThat(LeitnerSchedule.interval(2)).isEqualTo(Duration.ofDays(1));
    assertThat(LeitnerSchedule.interval(3)).isEqualTo(Duration.ofDays(3));
    assertThat(LeitnerSchedule.interval(4)).isEqualTo(Duration.ofDays(7));
    assertThat(LeitnerSchedule.interval(5)).isEqualTo(Duration.ofDays(21));
  }

  @Test
  void 카드는_답할_때마다_횟수와_일정을_바꾼다() {
    ChordReview card =
        ChordReview.start(Fixtures.user(1), QuizChord.builder().chordKey("C").build(), T0);

    card.answer(false, T0);
    assertThat(card.getWrongCount()).isEqualTo(1);
    assertThat(card.getBox()).isEqualTo(1);
    assertThat(card.isDue(T0)).isTrue();

    Instant later = T0.plusSeconds(60);
    card.answer(true, later);
    assertThat(card.getCorrectCount()).isEqualTo(1);
    assertThat(card.getBox()).isEqualTo(2);
    assertThat(card.getDueAt()).isEqualTo(later.plus(Duration.ofDays(1)));
    assertThat(card.getLastSeenAt()).isEqualTo(later);
    assertThat(card.isDue(later.plus(Duration.ofHours(23)))).isFalse();
    assertThat(card.isDue(later.plus(Duration.ofDays(1)))).isTrue();
  }
}
