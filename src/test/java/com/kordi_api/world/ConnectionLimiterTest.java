package com.kordi_api.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConnectionLimiterTest {

  @Test
  void IP당_정해진_수까지만_접속하고_끊기면_자리가_난다() {
    ConnectionLimiter limiter = new ConnectionLimiter();
    for (int i = 0; i < ConnectionLimiter.MAX_PER_IP; i++) {
      assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
    }
    assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
    assertThat(limiter.tryAcquire("5.6.7.8")).isTrue();

    limiter.release("1.2.3.4");
    assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
  }
}
