package com.kordi_api.global.store;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시계와 만료 보관소 빈. 서버를 여러 대로 늘리면 {@link #expiringStoreFactory}를 Redis 구현으로 바꾸면 되고, 보관소를 쓰는 서비스는 그대로다.
 */
@Configuration
public class ExpiringStoreConfig {

  /** 서비스가 "지금"을 읽는 시계. 테스트는 고정·이동 가능한 시계를 넣는다. */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public ExpiringStoreFactory expiringStoreFactory(Clock clock) {
    return new InMemoryExpiringStoreFactory(clock);
  }
}
