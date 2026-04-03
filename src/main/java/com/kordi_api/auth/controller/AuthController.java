package com.kordi_api.auth.controller;

import com.kordi_api.auth.dto.TokenResponse;
import com.kordi_api.auth.service.AuthService;
import com.kordi_api.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
