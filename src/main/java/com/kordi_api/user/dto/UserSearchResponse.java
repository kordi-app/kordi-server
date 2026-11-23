package com.kordi_api.user.dto;

import com.kordi_api.user.entity.User;
import jakarta.annotation.Nullable;

/** 다른 유저에게 노출되는 공개 프로필. 이메일 등 개인정보는 포함하지 않는다. */
public record UserSearchResponse(Long id, String nickname, @Nullable String profileImageUrl) {

  public static UserSearchResponse from(User user) {
    return new UserSearchResponse(user.getId(), user.getNickname(), user.getProfileImageUrl());
  }
}
