package com.kordi_api.global.security.oauth2;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class OAuth2RedirectUriCookieFilter extends OncePerRequestFilter {

  public static final String REDIRECT_URI_COOKIE_NAME = "oauth2_redirect_uri";
  public static final String REDIRECT_URI_PARAM = "redirect_uri";

  private static final String AUTHORIZATION_PATH_PREFIX = "/oauth2/authorization/";
  private static final int COOKIE_MAX_AGE_SECONDS = 180;

  private final OAuth2RedirectUriValidator redirectUriValidator;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (request.getRequestURI().startsWith(AUTHORIZATION_PATH_PREFIX)) {
      String redirectUri = request.getParameter(REDIRECT_URI_PARAM);

      if (redirectUriValidator.isAllowed(redirectUri)) {
        // Secure follows the real scheme (behind the proxy, forward headers make isSecure() true);
        // SameSite=Lax still lets it ride along on the top-level redirect back from Google
        ResponseCookie cookie =
            ResponseCookie.from(REDIRECT_URI_COOKIE_NAME, redirectUri)
                .path("/")
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .maxAge(COOKIE_MAX_AGE_SECONDS)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
      }
    }

    filterChain.doFilter(request, response);
  }
}
