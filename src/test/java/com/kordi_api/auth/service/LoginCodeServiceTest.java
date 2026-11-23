package com.kordi_api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.auth.dto.TokenResponse;
import com.kordi_api.global.store.InMemoryExpiringStoreFactory;
import com.kordi_api.support.MutableClock;
import org.junit.jupiter.api.Test;

class LoginCodeServiceTest {

  private final MutableClock clock = new MutableClock();
  private final LoginCodeService codes =
      new LoginCodeService(new InMemoryExpiringStoreFactory(clock));
  private final TokenResponse tokens = new TokenResponse("access", "refresh");

  @Test
  void 코드는_한_번만_토큰으로_바뀐다() {
    String code = codes.issue(tokens, "nonce");
    clock.set(1_000);
    assertThat(codes.redeem(code, "nonce")).contains(tokens);
    assertThat(codes.redeem(code, "nonce")).isEmpty();
  }

  @Test
  void 만료된_코드는_거절한다() {
    String code = codes.issue(tokens, "nonce");
    clock.set(LoginCodeService.TTL.toMillis() + 1);
    assertThat(codes.redeem(code, "nonce")).isEmpty();
    assertThat(codes.redeem(null, "nonce")).isEmpty();
  }

  @Test
  void 만료_직전까지는_바꿀_수_있다() {
    String code = codes.issue(tokens, "nonce");
    clock.set(LoginCodeService.TTL.toMillis());
    assertThat(codes.redeem(code, "nonce")).contains(tokens);
  }

  @Test
  void 다른_브라우저의_nonce로는_바꿀_수_없고_코드도_사라진다() {
    String code = codes.issue(tokens, "attacker"); // 공격자가 자기 계정으로 받은 코드
    clock.set(1_000);
    assertThat(codes.redeem(code, "victim")).isEmpty();
    assertThat(codes.redeem(code, "attacker")).isEmpty();
  }

  @Test
  void nonce_없이_시작한_로그인의_코드는_바꿀_수_없다() {
    String code = codes.issue(tokens, null);
    clock.set(1_000);
    assertThat(codes.redeem(code, null)).isEmpty();
  }
}
