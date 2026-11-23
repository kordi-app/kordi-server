package com.kordi_api.global.security.oauth2;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 로그인 후 1회용 코드를 들고 갈 주소 검증. 허용 오리진 아래라도 아무 경로나 받으면 코드가 엉뚱한 페이지(로그·Referer)로 새므로, 콜백 경로까지 정확히 같아야
 * 한다.
 */
@Component
public class OAuth2RedirectUriValidator {

  private final List<String> allowedOrigins;
  private final String callbackPath;

  public OAuth2RedirectUriValidator(
      @Value("${app.oauth2.allowed-redirect-origins}") List<String> allowedOrigins,
      @Value("${app.oauth2.redirect-url}") String redirectUrl) {
    this.allowedOrigins = allowedOrigins.stream().map(String::trim).toList();
    this.callbackPath = URI.create(redirectUrl).getPath();
  }

  public boolean isAllowed(String redirectUri) {
    if (redirectUri == null || redirectUri.isBlank()) {
      return false;
    }
    URI uri;
    try {
      uri = new URI(redirectUri);
    } catch (URISyntaxException e) {
      return false;
    }
    if (uri.getScheme() == null
        || uri.getRawAuthority() == null
        || uri.getRawUserInfo() != null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null
        || !callbackPath.equals(uri.getPath())) {
      return false;
    }
    String origin = uri.getScheme() + "://" + uri.getRawAuthority();
    return allowedOrigins.contains(origin);
  }
}
