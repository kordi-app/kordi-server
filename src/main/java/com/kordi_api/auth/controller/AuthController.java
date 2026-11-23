package com.kordi_api.auth.controller;

import com.kordi_api.auth.dto.TokenResponse;
import com.kordi_api.auth.service.AuthService;
import com.kordi_api.auth.service.LoginCodeService;
import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.global.security.LoginUser;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final LoginCodeService loginCodeService;

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

  /** 로그아웃. 액세스 토큰이 만료된 뒤에도 세션을 끝낼 수 있도록 리프레시 토큰을 {@code X-Refresh-Token} 헤더로 함께 받는다. */
  @DeleteMapping("/logout")
  public ApiResponse<Void> logout(
      @LoginUser Long userId,
      @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
    authService.logout(userId, refreshToken);
    return ApiResponse.success();
  }

  /**
   * 로그인 직후 받은 1회용 코드를 토큰 쌍으로 바꾼다 (프론트 서버가 서버 대 서버로 호출). {@code nonce}는 로그인을 시작한 브라우저의 것이어야 한다
   * ({@link LoginCodeService}).
   */
  @PostMapping("/exchange")
  public ApiResponse<TokenResponse> exchange(@RequestBody Map<String, String> body) {
    return ApiResponse.success(
        loginCodeService
            .redeem(body == null ? null : body.get("code"), body == null ? null : body.get("nonce"))
            .orElseThrow(() -> new CoreException(CoreExceptionCode.INVALID_LOGIN_CODE)));
  }
}
