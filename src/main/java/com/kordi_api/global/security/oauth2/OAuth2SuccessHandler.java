package com.kordi_api.global.security.oauth2;

import com.kordi_api.auth.model.OAuthProvider;
import com.kordi_api.global.security.jwt.JwtTokenProvider;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final JwtTokenProvider jwtTokenProvider;
  private final UserRepository userRepository;

  @Value("${app.oauth2.redirect-url}")
  private String redirectUrl;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {

    OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

    String registrationId =
        ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();
    OAuthProvider provider = OAuthProvider.from(registrationId);

    OAuth2UserInfo userInfo =
        OAuth2UserInfoFactory.getOAuth2UserInfo(provider, oAuth2User.getAttributes());

    User user =
        userRepository.findByOauthProviderAndOauthId(provider, userInfo.getOauthId()).orElseThrow();

    String accessToken = jwtTokenProvider.createAccessToken(user.getId());
    String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

    String url = redirectUrl + "?accessToken=" + accessToken + "&refreshToken=" + refreshToken;

    getRedirectStrategy().sendRedirect(request, response, url);
  }
}
