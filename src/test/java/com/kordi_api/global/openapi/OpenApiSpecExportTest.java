package com.kordi_api.global.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kordi_api.support.MySqlContainerConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 서버를 띄우지 않고 OpenAPI 스펙을 {@code build/openapi.json}에 쓴다. 클라이언트는 이 파일로 타입을 만든다 ({@code pnpm
 * gen:api}). 스펙은 테스트에서만 켠다 — 운영 기본값은 꺼짐 (application.yaml {@code springdoc.api-docs.enabled}).
 *
 * <p>다시 만들기: {@code ./gradlew test --tests '*OpenApiSpecExportTest'}
 */
@SpringBootTest(
    properties = {
      "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "spring.security.oauth2.client.registration.google.client-id=test",
      "spring.security.oauth2.client.registration.google.client-secret=test",
      "springdoc.api-docs.enabled=true",
      "springdoc.writer-with-order-by-keys=true",
      "springdoc.writer-with-default-pretty-printer=true"
    })
@Import(MySqlContainerConfig.class)
class OpenApiSpecExportTest {

  static final Path OUTPUT = Path.of("build", "openapi.json");

  @Autowired WebApplicationContext context;

  @Test
  void exportsSpec() throws Exception {
    MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    String spec =
        mvc.perform(get("/v3/api-docs").with(user("openapi-export")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(spec).contains("\"" + OpenApiConfig.ERROR_CODE_SCHEMA + "\"", "/api/friendships");
    Files.createDirectories(OUTPUT.getParent());
    Files.writeString(OUTPUT, spec + "\n", StandardCharsets.UTF_8);
  }

  @Test
  void docsRequireSignIn() throws Exception {
    MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
  }
}
