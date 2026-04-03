package com.kordi_api.auth.service;

import com.kordi_api.auth.dto.TokenResponse;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.global.security.jwt.JwtTokenProvider;
import com.kordi_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final JwtTokenProvider jwtTokenProvider;
  private final UserRepository userRepository;

  public TokenResponse reissueToken(String refreshToken) {
    if (!jwtTokenProvider.validateToken(refreshToken)) {
      throw new CoreException(CoreExceptionCode.INVALID_REFRESH_TOKEN);
    }

    Long userId = jwtTokenProvider.getUserId(refreshToken);

    userRepository
        .findById(userId)
        .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));

    String newAccessToken = jwtTokenProvider.createAccessToken(userId);
    String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

    return new TokenResponse(newAccessToken, newRefreshToken);
  }
}
