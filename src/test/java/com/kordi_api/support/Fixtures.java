package com.kordi_api.support;

import com.kordi_api.user.entity.OAuthProvider;
import com.kordi_api.user.entity.User;
import org.springframework.test.util.ReflectionTestUtils;

public final class Fixtures {

  private Fixtures() {}

  public static User user(long id) {
    User user =
        User.builder()
            .email("user" + id + "@example.com")
            .nickname("user" + id)
            .oauthProvider(OAuthProvider.GOOGLE)
            .oauthId("oauth-" + id)
            .build();
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
