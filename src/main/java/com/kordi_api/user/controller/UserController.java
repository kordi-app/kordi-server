package com.kordi_api.user.controller;

import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.LoginUser;
import com.kordi_api.user.dto.UserResponse;
import com.kordi_api.user.dto.UserSearchResponse;
import com.kordi_api.user.dto.UserUpdateRequest;
import com.kordi_api.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public ApiResponse<UserResponse> getUser(@LoginUser Long userId) {
    return ApiResponse.success(userService.getUser(userId));
  }

  @PatchMapping("/me/profile")
  public ApiResponse<UserResponse> updateProfile(
      @LoginUser Long userId, @Valid @RequestBody UserUpdateRequest request) {
    return ApiResponse.success(userService.updateProfile(userId, request));
  }

  @GetMapping("/check-nickname")
  public ApiResponse<Boolean> checkNickname(@RequestParam String nickname) {
    return ApiResponse.success(userService.isNicknameDuplicated(nickname));
  }

  @GetMapping("/search")
  public ApiResponse<UserSearchResponse> searchUser(@RequestParam String nickname) {
    return ApiResponse.success(userService.searchByNickname(nickname));
  }
}
