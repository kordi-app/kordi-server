package com.kordi_api.auth.service;

import com.kordi_api.auth.dto.TokenResponse;
import com.kordi_api.auth.entity.RefreshToken;
import com.kordi_api.auth.repository.RefreshTokenRepository;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.global.security.jwt.JwtProperties;
import com.kordi_api.global.security.jwt.JwtTokenProvider;
import com.kordi_api.user.entity.User;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  /** 회전 직후 직전 토큰을 계속 받아주는 시간. 동시 요청(멀티 탭, RSC 병렬 요청)의 재발급 경합을 흡수한다. */
  static final Duration ROTATION_GRACE_PERIOD = Duration.ofSeconds(30);

  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;

  /** 로그인 성공 시 토큰 쌍을 발급한다. 유저의 기존 리프레시 토큰은 대체된다. */
  @Transactional
  public TokenResponse issueTokens(User user) {
    String refreshToken = jwtTokenProvider.createRefreshToken();
    LocalDateTime expiredAt = refreshTokenExpiredAt(LocalDateTime.now());

    refreshTokenRepository
        .findByUserId(user.getId())
        .ifPresentOrElse(
            saved -> saved.replace(refreshToken, expiredAt),
            () ->
                refreshTokenRepository.save(
                    RefreshToken.builder()
                        .token(refreshToken)
                        .user(user)
                        .expiredAt(expiredAt)
                        .build()));

    return new TokenResponse(
        jwtTokenProvider.createAccessToken(user.getId(), user.getRole()), refreshToken);
  }

  // 만료 토큰 삭제 후 예외를 던지므로, 삭제가 롤백되지 않도록 CoreException은 롤백 대상에서 제외
  @Transactional(noRollbackFor = CoreException.class)
  public TokenResponse reissueToken(String refreshToken) {
    LocalDateTime now = LocalDateTime.now();

    var current = refreshTokenRepository.findByTokenForUpdate(refreshToken);
    if (current.isPresent()) {
      return rotate(current.get(), now);
    }

    // 방금 다른 요청이 회전시킨 토큰이면, 유예 시간 안에서는 회전된 최신 토큰을 그대로 돌려준다
    return refreshTokenRepository
        .findByPreviousToken(refreshToken)
        .filter(saved -> saved.isPreviousTokenUsable(now, ROTATION_GRACE_PERIOD))
        .filter(saved -> !saved.isExpired(now))
        .map(saved -> new TokenResponse(createAccessToken(saved), saved.getToken()))
        .orElseThrow(() -> new CoreException(CoreExceptionCode.INVALID_REFRESH_TOKEN));
  }

  @Transactional
  public void logout(Long userId) {
    refreshTokenRepository.deleteByUserId(userId);
  }

  private TokenResponse rotate(RefreshToken saved, LocalDateTime now) {
    if (saved.isExpired(now)) {
      refreshTokenRepository.delete(saved);
      throw new CoreException(CoreExceptionCode.INVALID_REFRESH_TOKEN);
    }
    String newRefreshToken = jwtTokenProvider.createRefreshToken();
    saved.rotate(newRefreshToken, refreshTokenExpiredAt(now), now);
    return new TokenResponse(createAccessToken(saved), newRefreshToken);
  }

  private String createAccessToken(RefreshToken saved) {
    User user = saved.getUser();
    return jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
  }

  private LocalDateTime refreshTokenExpiredAt(LocalDateTime now) {
    return now.plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiry()));
  }
}
