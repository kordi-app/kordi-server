package com.kordi_api.auth.controller;

import com.kordi_api.auth.dto.TokenResponse;
import com.kordi_api.auth.service.AuthService;
import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("reissue")
  public ApiResponse<TokenResponse> reissue(@RequestHeader("Authorization") String bearerToken) {
    String refreshToken = bearerToken.substring(7);
    return ApiResponse.success(authService.reissueToken(refreshToken));
  }

  @DeleteMapping("/logout")
  public ApiResponse<Void> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
    authService.logout(userDetails.getUser().getId());
    return ApiResponse.success();
  }
}
