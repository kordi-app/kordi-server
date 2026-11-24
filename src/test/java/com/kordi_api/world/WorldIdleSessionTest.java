package com.kordi_api.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldIdleSessionTest {

  @Test
  void 접속_후_아무것도_보내지_않으면_30초_뒤_유휴다() {
    Map<String, Object> attrs = new HashMap<>(Map.of("connectedAt", 0L));
    assertThat(WorldSocketHandler.isIdle(attrs, 29_000)).isFalse();
    assertThat(WorldSocketHandler.isIdle(attrs, 31_000)).isTrue();
  }

  @Test
  void 한_번이라도_보냈으면_마지막_메시지_뒤_90초까지는_유휴가_아니다() {
    Map<String, Object> attrs = new HashMap<>(Map.of("connectedAt", 0L, "lastSeen", 1_000L));
    assertThat(WorldSocketHandler.isIdle(attrs, 1_000 + 89_000)).isFalse();
    assertThat(WorldSocketHandler.isIdle(attrs, 1_000 + 91_000)).isTrue();
  }
}
