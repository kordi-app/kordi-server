package com.kordi_api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 통합 테스트에서 서버 시계를 테스트가 옮길 수 있게 한다 (지금 시각에서 시작). */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

  @Bean
  @Primary
  MutableClock testClock() {
    MutableClock clock = new MutableClock();
    clock.set(System.currentTimeMillis());
    return clock;
  }
}
