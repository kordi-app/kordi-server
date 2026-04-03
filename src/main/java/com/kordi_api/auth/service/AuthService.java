package com.kordi_api.auth.service;

import com.kordi_api.auth.dto.TokenResponse;
import com.kordi_api.auth.entity.RefreshToken;
import com.kordi_api.auth.repository.RefreshTokenRepository;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.global.security.jwt.JwtProperties;
import com.kordi_api.global.security.jwt.JwtTokenProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;

  @Transactional
  public TokenResponse reissueToken(String refreshToken) {

    RefreshToken savedToken =
        refreshTokenRepository
            .findByToken(refreshToken)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.INVALID_REFRESH_TOKEN));

    if (savedToken.isExpired()) {
      refreshTokenRepository.delete(savedToken);
      throw new CoreException(CoreExceptionCode.INVALID_REFRESH_TOKEN);
    }

    Long userId = savedToken.getUser().getId();

    String newAccessToken = jwtTokenProvider.createAccessToken(userId);
    String newRefreshToken = jwtTokenProvider.createRefreshToken();

    savedToken.updateToken(
        newRefreshToken,
        LocalDateTime.now().plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiry())));
    return new TokenResponse(newAccessToken, newRefreshToken);
  }

  @Transactional
  public void logout(Long userId) {
    refreshTokenRepository.deleteAllByUserId(userId);
  }
}
