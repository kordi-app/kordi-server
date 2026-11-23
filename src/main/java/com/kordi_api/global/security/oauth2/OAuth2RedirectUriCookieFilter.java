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
  public static final String LOGIN_NONCE_COOKIE_NAME = "oauth2_login_nonce";
  public static final String LOGIN_NONCE_PARAM = "login_nonce";

  /** 프론트가 만든 nonce: base64url 문자만, 충분히 길게 */
  private static final java.util.regex.Pattern NONCE_FORM =
      java.util.regex.Pattern.compile("[A-Za-z0-9_-]{32,128}");

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

      // the browser's login nonce rides through Google like the redirect URI; the login code is
      // bound to it (LoginCodeService). No valid nonce: clear any left from an earlier attempt.
      String nonce = request.getParameter(LOGIN_NONCE_PARAM);
      boolean validNonce = nonce != null && NONCE_FORM.matcher(nonce).matches();
      response.addHeader(
          HttpHeaders.SET_COOKIE,
          ResponseCookie.from(LOGIN_NONCE_COOKIE_NAME, validNonce ? nonce : "")
              .path("/")
              .httpOnly(true)
              .secure(request.isSecure())
              .sameSite("Lax")
              .maxAge(validNonce ? COOKIE_MAX_AGE_SECONDS : 0)
              .build()
              .toString());
    }

    filterChain.doFilter(request, response);
  }
}
