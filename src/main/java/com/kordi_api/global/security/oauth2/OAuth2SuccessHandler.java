package com.kordi_api.global.security.oauth2;

import com.kordi_api.auth.entity.RefreshToken;
import com.kordi_api.auth.model.OAuthProvider;
import com.kordi_api.auth.repository.RefreshTokenRepository;
import com.kordi_api.global.security.jwt.JwtProperties;
import com.kordi_api.global.security.jwt.JwtTokenProvider;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final JwtTokenProvider jwtTokenProvider;
  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;
  private final OAuth2RedirectUriValidator redirectUriValidator;

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
    String refreshToken = jwtTokenProvider.createRefreshToken();

    RefreshToken savedToken =
        refreshTokenRepository
            .findByUserId(user.getId())
            .map(
                token -> {
                  token.updateToken(
                      refreshToken,
                      LocalDateTime.now()
                          .plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiry())));
                  return token;
                })
            .orElseGet(
                () ->
                    RefreshToken.builder()
                        .token(refreshToken)
                        .user(user)
                        .expiredAt(
                            LocalDateTime.now()
                                .plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiry())))
                        .build());

    refreshTokenRepository.save(savedToken);

    String url =
        resolveRedirectUrl(request, response)
            + "?accessToken="
            + accessToken
            + "&refreshToken="
            + refreshToken;

    getRedirectStrategy().sendRedirect(request, response, url);
  }

  private String resolveRedirectUrl(HttpServletRequest request, HttpServletResponse response) {
    Cookie cookie =
        WebUtils.getCookie(request, OAuth2RedirectUriCookieFilter.REDIRECT_URI_COOKIE_NAME);
    if (cookie == null) {
      return redirectUrl;
    }

    expireCookie(response, cookie.getName());

    String requestedUrl = cookie.getValue();
    return redirectUriValidator.isAllowed(requestedUrl) ? requestedUrl : redirectUrl;
  }

  private void expireCookie(HttpServletResponse response, String name) {
    Cookie expired = new Cookie(name, "");
    expired.setPath("/");
    expired.setMaxAge(0);
    response.addCookie(expired);
  }
}
