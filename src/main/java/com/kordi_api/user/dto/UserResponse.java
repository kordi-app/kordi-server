package com.kordi_api.user.dto;

import com.kordi_api.auth.model.OAuthProvider;
import com.kordi_api.user.entity.User;

public record UserResponse(
    Long id, String email, String nickname, String profileImageUrl, OAuthProvider oAuthProvider) {
  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getNickname(),
        user.getProfileImageUrl(),
        user.getOauthProvider());
  }
}
