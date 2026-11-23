package com.kordi_api.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  record Body(@NotNull Integer value, @Max(10) Integer limited) {}

  @RestController
  static class TestController {
    @PostMapping("/test")
    String accept(@Valid @RequestBody Body body) {
      return "ok";
    }
  }

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void 지원하지_않는_미디어_타입은_500이_아니라_415다() throws Exception {
    mvc.perform(post("/test").contentType(MediaType.TEXT_PLAIN).content("x"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value(1003));
  }

  @Test
  void 값이_빠지면_1001이다() throws Exception {
    mvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(1001));
  }

  @Test
  void 범위를_넘으면_1003이다() throws Exception {
    mvc.perform(
            post("/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":1,\"limited\":99}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(1003));
  }
}
