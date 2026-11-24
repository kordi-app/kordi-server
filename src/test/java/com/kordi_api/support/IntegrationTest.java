package com.kordi_api.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 실제 MySQL(Testcontainers)과 테스트가 옮기는 시계로 띄우는 통합 테스트. 같은 설정이라 테스트 클래스끼리 컨텍스트를 함께 쓴다. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    properties = {
      "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "spring.security.oauth2.client.registration.google.client-id=test",
      "spring.security.oauth2.client.registration.google.client-secret=test"
    })
@Import({MySqlContainerConfig.class, TestClockConfig.class})
public @interface IntegrationTest {}
