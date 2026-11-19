package com.kordi_api.auth.service;

import static com.kordi_api.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kordi_api.auth.dto.TokenResponse;
import com.kordi_api.auth.entity.RefreshToken;
import com.kordi_api.auth.repository.RefreshTokenRepository;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.security.jwt.JwtProperties;
import com.kordi_api.global.security.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

  private RefreshTokenRepository repository;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret(Base64.getEncoder().encodeToString(new byte[32]));
    properties.setAccessTokenExpiry(60_000);
    properties.setRefreshTokenExpiry(60_000);
    repository = mock(RefreshTokenRepository.class);
    authService = new AuthService(new JwtTokenProvider(properties), repository, properties);
  }

  private RefreshToken savedToken(String token, LocalDateTime expiredAt) {
    return RefreshToken.builder().token(token).user(user(1L)).expiredAt(expiredAt).build();
  }

  @Test
  void 재발급하면_토큰이_회전되고_직전_토큰이_보관된다() {
    RefreshToken saved = savedToken("old", LocalDateTime.now().plusDays(1));
    given(repository.findByTokenForUpdate("old")).willReturn(Optional.of(saved));

    TokenResponse response = authService.reissueToken("old");

    assertThat(response.refreshToken()).isNotEqualTo("old");
    assertThat(saved.getToken()).isEqualTo(response.refreshToken());
    assertThat(saved.getPreviousToken()).isEqualTo("old");
  }

  @Test
  void 유예_시간_안에_직전_토큰으로_재발급하면_최신_토큰을_돌려준다() {
    RefreshToken saved = savedToken("old", LocalDateTime.now().plusDays(1));
    saved.rotate("new", LocalDateTime.now().plusDays(1), LocalDateTime.now());
    given(repository.findByTokenForUpdate("old")).willReturn(Optional.empty());
    given(repository.findByPreviousToken("old")).willReturn(Optional.of(saved));

    TokenResponse response = authService.reissueToken("old");

    assertThat(response.refreshToken()).isEqualTo("new");
    assertThat(saved.getToken()).isEqualTo("new");
  }

  @Test
  void 유예_시간이_지난_직전_토큰은_거부된다() {
    RefreshToken saved = savedToken("old", LocalDateTime.now().plusDays(1));
    LocalDateTime longAgo =
        LocalDateTime.now().minus(AuthService.ROTATION_GRACE_PERIOD).minusSeconds(1);
    saved.rotate("new", LocalDateTime.now().plusDays(1), longAgo);
    given(repository.findByTokenForUpdate(anyString())).willReturn(Optional.empty());
    given(repository.findByPreviousToken("old")).willReturn(Optional.of(saved));

    assertThatThrownBy(() -> authService.reissueToken("old")).isInstanceOf(CoreException.class);
  }

  @Test
  void 만료된_토큰은_삭제하고_거부한다() {
    RefreshToken saved = savedToken("old", LocalDateTime.now().minusSeconds(1));
    given(repository.findByTokenForUpdate("old")).willReturn(Optional.of(saved));

    assertThatThrownBy(() -> authService.reissueToken("old")).isInstanceOf(CoreException.class);
    verify(repository).delete(saved);
  }

  @Test
  void 로그인하면_기존_토큰을_대체하고_유예_정보를_지운다() {
    RefreshToken saved = savedToken("old", LocalDateTime.now().plusDays(1));
    saved.rotate("rotated", LocalDateTime.now().plusDays(1), LocalDateTime.now());
    ReflectionTestUtils.setField(saved, "id", 10L);
    given(repository.findByUserId(1L)).willReturn(Optional.of(saved));

    TokenResponse response = authService.issueTokens(user(1L));

    assertThat(saved.getToken()).isEqualTo(response.refreshToken());
    assertThat(saved.getPreviousToken()).isNull();
  }
}
