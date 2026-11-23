package com.kordi_api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/** 통합 테스트용 MySQL (운영과 같은 8.0). 로컬 DB나 .env 없이 테스트가 돌도록. */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlContainerConfig {

  @Bean
  @ServiceConnection
  MySQLContainer mysql() {
    return new MySQLContainer("mysql:8.0")
        .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
  }
}
