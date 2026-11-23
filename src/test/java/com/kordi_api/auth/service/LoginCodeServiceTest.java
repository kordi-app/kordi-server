package com.kordi_api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.auth.dto.TokenResponse;
import org.junit.jupiter.api.Test;

class LoginCodeServiceTest {

  private final LoginCodeService codes = new LoginCodeService();
  private final TokenResponse tokens = new TokenResponse("access", "refresh");

  @Test
  void 코드는_한_번만_토큰으로_바뀐다() {
    String code = codes.issue(tokens, "nonce", 0);
    assertThat(codes.redeem(code, "nonce", 1_000)).contains(tokens);
    assertThat(codes.redeem(code, "nonce", 1_000)).isEmpty();
  }

  @Test
  void 만료된_코드는_거절한다() {
    String code = codes.issue(tokens, "nonce", 0);
    assertThat(codes.redeem(code, "nonce", LoginCodeService.TTL_MS + 1)).isEmpty();
    assertThat(codes.redeem(null, "nonce", 0)).isEmpty();
  }

  @Test
  void 다른_브라우저의_nonce로는_바꿀_수_없고_코드도_사라진다() {
    String code = codes.issue(tokens, "attacker", 0); // 공격자가 자기 계정으로 받은 코드
    assertThat(codes.redeem(code, "victim", 1_000)).isEmpty();
    assertThat(codes.redeem(code, "attacker", 1_000)).isEmpty();
  }

  @Test
  void nonce_없이_시작한_로그인의_코드는_바꿀_수_없다() {
    String code = codes.issue(tokens, null, 0);
    assertThat(codes.redeem(code, null, 1_000)).isEmpty();
  }
}
