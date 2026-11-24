package com.kordi_api.global.security.oauth2;

import com.kordi_api.global.security.jwt.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * OAuth2 인가 요청(state 등)을 HttpSession 대신 서명한 쿠키에 담는다.
 *
 * <p>기본 구현(HttpSessionOAuth2AuthorizationRequestRepository)은 {@code /oauth2/authorization/google}을
 * 칠 때마다 세션을 하나씩 만든다 (30분 유지). 인증 없이 누구나 부를 수 있는 경로라 반복 호출만으로 힙이 차오른다. 쿠키에 두면 서버는 아무것도 들고 있지 않는다.
 *
 * <p>쿠키 값은 {@code base64url(JSON).base64url(HMAC-SHA256)}. 키는 JWT 비밀키에서 용도를 구분해 파생하므로 따로 설정할 env는
 * 없다. 서명이 맞지 않거나, 만들어진 지 {@link #MAX_AGE}가 지났거나, 콜백의 state와 다르면 없는 것으로 본다. 콜백에서 한 번 읽으면 지운다.
 */
@Slf4j
@Component
public class CookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

  public static final String COOKIE_NAME = "oauth2_auth_request";

  /** 구글 동의 화면을 거쳐 돌아오기까지의 여유. 리다이렉트 URI·nonce 쿠키와 같은 길이 */
  static final Duration MAX_AGE = Duration.ofSeconds(180);

  /** 브라우저 쿠키 한 개 한도(4KB)보다 넉넉히 작게 */
  private static final int MAX_COOKIE_LENGTH = 3800;

  private static final String HMAC = "HmacSHA256";
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder B64D = Base64.getUrlDecoder();

  /** 쿠키에 싣는 모양. grant type은 항상 authorization_code. */
  record Stored(
      String authorizationUri,
      String clientId,
      String redirectUri,
      Set<String> scopes,
      String state,
      Map<String, Object> additionalParameters,
      Map<String, Object> attributes,
      String authorizationRequestUri,
      long issuedAt) {}

  private final SecretKeySpec key;
  private final Clock clock;
  private final JsonMapper json = JsonMapper.builder().build();

  public CookieOAuth2AuthorizationRequestRepository(JwtProperties jwtProperties, Clock clock) {
    this.key = deriveKey(jwtProperties.getSecret());
    this.clock = clock;
  }

  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    String state = request.getParameter(OAuth2ParameterNames.STATE);
    if (state == null) {
      return null;
    }
    Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
    if (cookie == null) {
      return null;
    }
    Stored stored = verify(cookie.getValue());
    if (stored == null || !state.equals(stored.state())) {
      return null;
    }
    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri(stored.authorizationUri())
        .clientId(stored.clientId())
        .redirectUri(stored.redirectUri())
        .scopes(stored.scopes())
        .state(stored.state())
        .additionalParameters(stored.additionalParameters())
        .attributes(stored.attributes())
        .authorizationRequestUri(stored.authorizationRequestUri())
        .build();
  }

  @Override
  public void saveAuthorizationRequest(
      OAuth2AuthorizationRequest authorizationRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (authorizationRequest == null) {
      expire(request, response);
      return;
    }
    Stored stored =
        new Stored(
            authorizationRequest.getAuthorizationUri(),
            authorizationRequest.getClientId(),
            authorizationRequest.getRedirectUri(),
            new LinkedHashSet<>(authorizationRequest.getScopes()),
            authorizationRequest.getState(),
            new LinkedHashMap<>(authorizationRequest.getAdditionalParameters()),
            new LinkedHashMap<>(authorizationRequest.getAttributes()),
            authorizationRequest.getAuthorizationRequestUri(),
            clock.millis());
    String value = sign(stored);
    if (value.length() > MAX_COOKIE_LENGTH) {
      log.warn("[OAuth2AuthorizationRequestCookie] too large: {} chars", value.length());
    }
    response.addHeader(
        HttpHeaders.SET_COOKIE, cookie(request, value, MAX_AGE.toSeconds()).toString());
  }

  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    OAuth2AuthorizationRequest loaded = loadAuthorizationRequest(request);
    if (WebUtils.getCookie(request, COOKIE_NAME) != null) {
      expire(request, response); // one use only, valid or not
    }
    return loaded;
  }

  String sign(Stored stored) {
    String payload = B64.encodeToString(json.writeValueAsBytes(stored));
    return payload + "." + B64.encodeToString(mac(payload));
  }

  /** 서명과 나이가 맞으면 내용, 아니면 null. 서명을 먼저 본 뒤에야 JSON을 읽는다. */
  Stored verify(String value) {
    if (value == null) {
      return null;
    }
    int dot = value.indexOf('.');
    if (dot <= 0) {
      return null;
    }
    String payload = value.substring(0, dot);
    try {
      byte[] signature = B64D.decode(value.substring(dot + 1));
      if (!MessageDigest.isEqual(mac(payload), signature)) {
        return null;
      }
      Stored stored = json.readValue(B64D.decode(payload), Stored.class);
      long age = clock.millis() - stored.issuedAt();
      return age >= 0 && age <= MAX_AGE.toMillis() ? stored : null;
    } catch (RuntimeException e) {
      return null; // malformed base64 / JSON
    }
  }

  private void expire(HttpServletRequest request, HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, "", 0).toString());
  }

  // Secure follows the real scheme like the redirect-uri cookie (behind Caddy, forward headers make
  // isSecure() true); SameSite=Lax still sends it on the top-level redirect back from Google
  private static ResponseCookie cookie(HttpServletRequest request, String value, long maxAge) {
    return ResponseCookie.from(COOKIE_NAME, value)
        .path("/")
        .httpOnly(true)
        .secure(request.isSecure())
        .sameSite("Lax")
        .maxAge(maxAge)
        .build();
  }

  private byte[] mac(String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(key);
      return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** JWT 서명키를 그대로 쓰지 않고 용도 라벨로 HMAC해 따로 쓴다 (한 키를 두 용도에 섞지 않기). */
  private static SecretKeySpec deriveKey(String jwtSecret) {
    if (jwtSecret == null || jwtSecret.isBlank()) {
      throw new IllegalStateException("jwt.secret is required");
    }
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC));
      byte[] derived =
          mac.doFinal("kordi-oauth2-authorization-request".getBytes(StandardCharsets.UTF_8));
      return new SecretKeySpec(derived, HMAC);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
