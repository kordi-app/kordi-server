package com.kordi_api;

import com.kordi_api.support.MySqlContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 실제 MySQL(Testcontainers)에 Flyway 마이그레이션을 올리고 Hibernate validate까지 통과해 컨텍스트가 뜨는지 본다. 외부 env 없이 돈다.
 */
@SpringBootTest(
    properties = {
      "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "spring.security.oauth2.client.registration.google.client-id=test",
      "spring.security.oauth2.client.registration.google.client-secret=test"
    })
@Import(MySqlContainerConfig.class)
class KordiApiApplicationTests {

  @Test
  void contextLoads() {}
}
