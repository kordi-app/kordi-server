package com.kordi_api.global.security.oauth2;

import com.kordi_api.auth.model.OAuthProvider;
import java.util.Map;

public class OAuth2UserInfoFactory {

  public static OAuth2UserInfo getOAuth2UserInfo(
      OAuthProvider provider, Map<String, Object> attributes) {
    return switch (provider) {
      case GOOGLE -> new GoogleOAuth2UserInfo(attributes);
      case NAVER -> throw new IllegalArgumentException("네이버 미구현");
      case KAKAO -> throw new IllegalArgumentException("카카오 미구현");
    };
  }
}
