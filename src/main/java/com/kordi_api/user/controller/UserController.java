package com.kordi_api.user.controller;

import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.CustomUserDetails;
import com.kordi_api.user.dto.UserResponse;
import com.kordi_api.user.dto.UserUpdateRequest;
import com.kordi_api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public ApiResponse<UserResponse> getUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
    return ApiResponse.success(userService.getUser(userDetails.getUser().getId()));
  }

  @PatchMapping("/me/profile")
  public ApiResponse<UserResponse> updateProfile(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestBody UserUpdateRequest request) {
    return ApiResponse.success(userService.updateProfile(userDetails.getUser().getId(), request));
  }

  @GetMapping("/check-nickname")
  public ApiResponse<Boolean> checkNickname(@RequestParam String nickname) {
    return ApiResponse.success(userService.isNicknameDuplicated(nickname));
  }
}
