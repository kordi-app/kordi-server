package com.kordi_api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.auth.dto.TokenResponse;
import org.junit.jupiter.api.Test;

class LoginCodeServiceTest {

  private final LoginCodeService codes = new LoginCodeService();
  private final TokenResponse tokens = new TokenResponse("access", "refresh");

  @Test
  void 코드는_한_번만_토큰으로_바뀐다() {
    String code = codes.issue(tokens, 0);
    assertThat(codes.redeem(code, 1_000)).contains(tokens);
    assertThat(codes.redeem(code, 1_000)).isEmpty();
  }

  @Test
  void 만료된_코드는_거절한다() {
    String code = codes.issue(tokens, 0);
    assertThat(codes.redeem(code, LoginCodeService.TTL_MS + 1)).isEmpty();
    assertThat(codes.redeem(null, 0)).isEmpty();
  }
}
