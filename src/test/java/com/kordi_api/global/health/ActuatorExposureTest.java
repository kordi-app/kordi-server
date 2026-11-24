package com.kordi_api.global.health;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kordi_api.support.MySqlContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 헬스체크는 누구나 (상태만, DB 포함), 그 밖의 actuator는 아무도 못 본다. */
@SpringBootTest(
    properties = {
      "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "spring.security.oauth2.client.registration.google.client-id=test",
      "spring.security.oauth2.client.registration.google.client-secret=test"
    })
@Import(MySqlContainerConfig.class)
class ActuatorExposureTest {

  @Autowired WebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void 헬스체크는_인증_없이_상태만_보여준다() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist())
        .andExpect(jsonPath("$.details").doesNotExist());
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    mvc.perform(get("/health-check")).andExpect(status().isOk());
  }

  @Test
  void 그_밖의_actuator_엔드포인트는_열려_있지_않다() throws Exception {
    for (String path :
        new String[] {
          "/actuator/env", "/actuator/metrics", "/actuator/beans", "/actuator/heapdump"
        }) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      mvc.perform(get(path).with(user("someone"))).andExpect(status().isNotFound());
    }
  }
}
