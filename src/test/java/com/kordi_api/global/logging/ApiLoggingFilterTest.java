package com.kordi_api.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiLoggingFilterTest {

  @Test
  void 코드와_토큰_값은_로그에서_가린다() {
    assertThat(ApiLoggingFilter.mask("code=abc&state=xyz&page=2"))
        .isEqualTo("code=***&state=***&page=2");
    assertThat(ApiLoggingFilter.mask("refreshToken=t")).isEqualTo("refreshToken=***");
    assertThat(ApiLoggingFilter.mask(null)).isNull();
  }
}
