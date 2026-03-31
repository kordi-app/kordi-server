package com.kordi_api.user.controller;

import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.user.dto.UserResponse;
import com.kordi_api.user.dto.UserUpdateRequest;
import com.kordi_api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/{userid}")
  public ApiResponse<UserResponse> getUser(@PathVariable long userId) {
    return ApiResponse.success(userService.getUser(userId));
  }

  @PatchMapping("/{userid}/profile")
  public ApiResponse<UserResponse> updateProfile(
      @PathVariable Long userId, @RequestBody UserUpdateRequest request) {
    return ApiResponse.success(userService.updateProfile(userId, request));
  }

  @GetMapping("/check-nickname")
  public ApiResponse<Boolean> checkNickname(@RequestParam String nickname) {
    return ApiResponse.success(userService.isNicknameDuplicated(nickname));
  }
}
