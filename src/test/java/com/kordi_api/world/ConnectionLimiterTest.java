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

  @Test
  void IPv6는_같은_64_대역을_한_IP로_센다() {
    ConnectionLimiter limiter = new ConnectionLimiter();
    for (int i = 0; i < ConnectionLimiter.MAX_PER_IP; i++) {
      assertThat(limiter.tryAcquire("2001:db8:1:2:" + Integer.toHexString(i) + "::1")).isTrue();
    }
    assertThat(limiter.tryAcquire("2001:db8:1:2:ffff::9")).isFalse();
    assertThat(limiter.tryAcquire("2001:db8:1:3::1")).isTrue();

    limiter.release("2001:0db8:0001:0002:0000:0000:0000:0001");
    assertThat(limiter.tryAcquire("2001:db8:1:2::abcd")).isTrue();
  }

  @Test
  void IPv4와_알아볼_수_없는_값은_그대로_센다() {
    assertThat(ConnectionLimiter.key("1.2.3.4")).isEqualTo("1.2.3.4");
    assertThat(ConnectionLimiter.key("::ffff:1.2.3.4")).isEqualTo("1.2.3.4");
    assertThat(ConnectionLimiter.key("unknown")).isEqualTo("unknown");
    assertThat(ConnectionLimiter.key("not:an:ip")).isEqualTo("not:an:ip");
  }
}
