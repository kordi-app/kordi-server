package com.kordi_api.auth.controller;

import com.kordi_api.auth.dto.TokenResponse;
import com.kordi_api.auth.service.AuthService;
import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.global.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  private static final String BEARER_PREFIX = "Bearer ";

  /** 리프레시 토큰은 {@code Authorization: Bearer <refreshToken>} 헤더로 받는다. */
  @PostMapping("/reissue")
  public ApiResponse<TokenResponse> reissue(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    if (authorization == null
        || !authorization.startsWith(BEARER_PREFIX)
        || authorization.length() == BEARER_PREFIX.length()) {
      throw new CoreException(CoreExceptionCode.INVALID_REFRESH_TOKEN);
    }
    String refreshToken = authorization.substring(BEARER_PREFIX.length());
    return ApiResponse.success(authService.reissueToken(refreshToken));
  }

  @DeleteMapping("/logout")
  public ApiResponse<Void> logout(@LoginUser Long userId) {
    authService.logout(userId);
    return ApiResponse.success();
  }
}
