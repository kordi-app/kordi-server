package com.kordi_api.global.security.oauth2;

import com.kordi_api.auth.dto.TokenResponse;
import com.kordi_api.auth.service.AuthService;
import com.kordi_api.auth.service.LoginCodeService;
import com.kordi_api.user.entity.OAuthProvider;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
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
import org.springframework.web.util.WebUtils;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final AuthService authService;
  private final LoginCodeService loginCodeService;
  private final UserRepository userRepository;
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

    TokenResponse tokens = authService.issueTokens(user);

    // only a short-lived one-time code goes in the URL (history, logs, Referer);
    // the front end's server trades it for the tokens via POST /api/auth/exchange
    String code = loginCodeService.issue(tokens, System.currentTimeMillis());
    String url = resolveRedirectUrl(request, response) + "?code=" + code;

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
