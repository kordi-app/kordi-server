package com.kordi_api.adventure.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdventureRunServiceTest {

  private final AdventureRunService runs = new AdventureRunService();

  @Test
  void 판_길이는_서버가_시작부터_잰다() {
    String id = runs.start(7L, 1_000);
    assertThat(runs.finish(7L, id, 31_000)).contains(30_000L);
  }

  @Test
  void 한_판의_결과는_한_번만_낼_수_있다() {
    String id = runs.start(7L, 0);
    assertThat(runs.finish(7L, id, 10_000)).isPresent();
    assertThat(runs.finish(7L, id, 20_000)).isEmpty();
  }

  @Test
  void 다른_유저의_판이나_이전_판으로는_낼_수_없다() {
    String old = runs.start(7L, 0);
    String current = runs.start(7L, 100);
    assertThat(runs.finish(8L, current, 10_000)).isEmpty();
    assertThat(runs.finish(7L, old, 10_000)).isEmpty();
    assertThat(runs.finish(7L, current, 10_000)).isPresent();
  }

  @Test
  void 너무_오래된_판은_무효다() {
    String id = runs.start(7L, 0);
    runs.sweep(AdventureRunService.MAX_RUN_MS + 1);
    assertThat(runs.finish(7L, id, AdventureRunService.MAX_RUN_MS + 2)).isEmpty();
  }
}
