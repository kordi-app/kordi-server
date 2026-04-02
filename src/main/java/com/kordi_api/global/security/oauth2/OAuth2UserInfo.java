package com.kordi_api.global.security.oauth2;

public interface OAuth2UserInfo {

  String getOauthId();

  String getEmail();

  String getName();

  String getProfileImageUrl();
}
