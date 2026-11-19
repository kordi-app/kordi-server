package com.kordi_api.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.user.entity.UserRole;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private static JwtProperties properties(long accessExpiryMs) {
    JwtProperties properties = new JwtProperties();
    properties.setSecret(Base64.getEncoder().encodeToString(new byte[32]));
    properties.setAccessTokenExpiry(accessExpiryMs);
    properties.setRefreshTokenExpiry(60_000);
    return properties;
  }

  @Test
  void 발급한_액세스_토큰에서_userId와_role을_꺼낸다() {
    JwtTokenProvider provider = new JwtTokenProvider(properties(60_000));

    String token = provider.createAccessToken(42L, UserRole.ADMIN);

    assertThat(provider.parseAccessToken(token))
        .hasValueSatisfying(
            claims -> {
              assertThat(claims.userId()).isEqualTo(42L);
              assertThat(claims.role()).isEqualTo(UserRole.ADMIN);
            });
  }

  @Test
  void 만료되었거나_변조된_토큰은_empty() {
    JwtTokenProvider provider = new JwtTokenProvider(properties(-1_000));
    String expired = provider.createAccessToken(1L, UserRole.USER);

    assertThat(provider.parseAccessToken(expired)).isEmpty();
    assertThat(provider.parseAccessToken("not-a-jwt")).isEmpty();
  }

  @Test
  void 리프레시_토큰은_매번_다른_값이다() {
    JwtTokenProvider provider = new JwtTokenProvider(properties(60_000));

    assertThat(provider.createRefreshToken()).isNotEqualTo(provider.createRefreshToken());
  }
}
