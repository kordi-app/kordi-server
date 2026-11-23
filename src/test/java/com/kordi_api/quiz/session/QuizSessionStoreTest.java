package com.kordi_api.quiz.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.global.store.InMemoryExpiringStoreFactory;
import com.kordi_api.quiz.QuizRules;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.quiz.session.dto.QuizAnswerRequest;
import com.kordi_api.quiz.session.dto.QuizResultResponse;
import com.kordi_api.support.MutableClock;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuizSessionStoreTest {

  private final MutableClock clock = new MutableClock();
  private final InMemoryExpiringStoreFactory stores = new InMemoryExpiringStoreFactory(clock);
  private final QuizSessionStore store = new QuizSessionStore(stores, clock);

  @Test
  void 판은_연_유저만_한_번_꺼낼_수_있다() {
    var session = store.open(7L, Difficulty.EASY, List.of(1L), List.of("C"));

    assertThat(store.take(8L, session.id())).isEmpty();
    assertThat(store.take(7L, "other")).isEmpty();
    assertThat(store.take(7L, session.id())).contains(session);
    assertThat(store.take(7L, session.id())).isEmpty();
  }

  @Test
  void 새_판을_열면_이전_판은_무효다() {
    var old = store.open(7L, Difficulty.EASY, List.of(1L), List.of("C"));
    var current = store.open(7L, Difficulty.HARD, List.of(2L), List.of("C13"));

    assertThat(store.take(7L, old.id())).isEmpty();
    assertThat(store.take(7L, current.id())).isPresent();
  }

  @Test
  void 수명이_지난_판은_꺼낼_수_없다() {
    var session = store.open(7L, Difficulty.EASY, List.of(1L), List.of("C"));
    clock.set(QuizRules.SESSION_TTL.toMillis() + 1);

    assertThat(store.take(7L, session.id())).isEmpty();
  }

  @Test
  void 발급_후_지난_시간은_서버_시계로_잰다() {
    clock.set(1_000);
    var session = store.open(7L, Difficulty.EASY, List.of(1L), List.of("C"));
    clock.set(41_000);

    assertThat(store.elapsedSinceIssue(session)).isEqualTo(40_000);
  }

  @Test
  void 같은_답을_다시_내면_채점한_결과를_돌려준다() {
    var session = store.open(7L, Difficulty.EASY, List.of(1L), List.of("C"));
    store.take(7L, session.id());
    var answers = List.of(new QuizAnswerRequest(List.of(60, 64, 67), 1_000));
    var response = new QuizResultResponse(190, 1, 1, List.of(), 190, 1, true);
    store.complete(7L, session.id(), answers, response);

    assertThat(store.completedResult(7L, session.id(), answers)).contains(response);
    assertThat(
            store.completedResult(
                7L, session.id(), List.of(new QuizAnswerRequest(List.of(60, 64, 67), 500))))
        .isEmpty();
    assertThat(store.completedResult(8L, session.id(), answers)).isEmpty();
  }

  @Test
  void 저장에_실패한_판은_되돌려_다시_낼_수_있다() {
    var session = store.open(7L, Difficulty.EASY, List.of(1L), List.of("C"));
    store.take(7L, session.id());
    store.restore(session);

    assertThat(store.take(7L, session.id())).contains(session);
  }
}
