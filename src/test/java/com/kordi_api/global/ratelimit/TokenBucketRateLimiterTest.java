package com.kordi_api.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.global.store.InMemoryExpiringStoreFactory;
import com.kordi_api.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

  private final MutableClock clock = new MutableClock();
  private final TokenBucketRateLimiter limiter =
      new TokenBucketRateLimiter(
          new InMemoryExpiringStoreFactory(clock), clock, "test", 3, Duration.ofSeconds(10));

  @Test
  void 가득_찬_만큼_몰아_쓰고_나면_거절한다() {
    assertThat(limiter.tryAcquire("a")).isTrue();
    assertThat(limiter.tryAcquire("a")).isTrue();
    assertThat(limiter.tryAcquire("a")).isTrue();
    assertThat(limiter.tryAcquire("a")).isFalse();
    // 다른 키는 따로 센다
    assertThat(limiter.tryAcquire("b")).isTrue();
  }

  @Test
  void 시간이_지나면_하나씩_다시_찬다() {
    for (int i = 0; i < 3; i++) {
      limiter.tryAcquire("a");
    }
    clock.set(9_999);
    assertThat(limiter.tryAcquire("a")).isFalse();
    clock.set(10_000);
    assertThat(limiter.tryAcquire("a")).isTrue();
    assertThat(limiter.tryAcquire("a")).isFalse();
  }

  @Test
  void 거절된_요청은_토큰을_쓰지_않는다() {
    for (int i = 0; i < 3; i++) {
      limiter.tryAcquire("a");
    }
    clock.set(5_000);
    assertThat(limiter.tryAcquire("a")).isFalse(); // half a token, not spent
    clock.set(10_000);
    assertThat(limiter.tryAcquire("a")).isTrue();
  }

  @Test
  void 오래_쉬어도_용량보다_많이_차지_않는다() {
    limiter.tryAcquire("a");
    clock.set(3_600_000);
    for (int i = 0; i < 3; i++) {
      assertThat(limiter.tryAcquire("a")).isTrue();
    }
    assertThat(limiter.tryAcquire("a")).isFalse();
  }
}
