package com.kordi_api.adventure.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.global.store.InMemoryExpiringStoreFactory;
import com.kordi_api.support.MutableClock;
import org.junit.jupiter.api.Test;

class AdventureRunServiceTest {

  private static final long MAX_RUN_MS = AdventureRunService.MAX_RUN.toMillis();

  private final MutableClock clock = new MutableClock();
  private final InMemoryExpiringStoreFactory stores = new InMemoryExpiringStoreFactory(clock);
  private final AdventureRunService runs = new AdventureRunService(stores, clock);

  @Test
  void 판_길이는_서버가_시작부터_잰다() {
    clock.set(1_000);
    String id = runs.start(7L);
    clock.set(31_000);
    assertThat(runs.finish(7L, id)).contains(30_000L);
  }

  @Test
  void 한_판의_결과는_한_번만_낼_수_있다() {
    String id = runs.start(7L);
    clock.set(10_000);
    assertThat(runs.finish(7L, id)).isPresent();
    clock.set(20_000);
    assertThat(runs.finish(7L, id)).isEmpty();
  }

  @Test
  void 다른_유저의_판이나_이전_판으로는_낼_수_없다() {
    String old = runs.start(7L);
    clock.set(100);
    String current = runs.start(7L);
    clock.set(10_000);
    assertThat(runs.finish(8L, current)).isEmpty();
    assertThat(runs.finish(7L, old)).isEmpty();
    assertThat(runs.finish(7L, current)).isPresent();
  }

  @Test
  void 최대_길이까지는_인정한다() {
    String id = runs.start(7L);
    clock.set(MAX_RUN_MS);
    assertThat(runs.finish(7L, id)).contains(MAX_RUN_MS);
  }

  @Test
  void 너무_오래된_판은_무효다() {
    String id = runs.start(7L);
    clock.set(MAX_RUN_MS + 1);
    stores.sweep();
    clock.set(MAX_RUN_MS + 2);
    assertThat(runs.finish(7L, id)).isEmpty();
  }
}
