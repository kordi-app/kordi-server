package com.kordi_api.global.security.oauth2;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OAuth2RedirectUriValidator {

  private final List<String> allowedOrigins;

  public OAuth2RedirectUriValidator(
      @Value("${app.oauth2.allowed-redirect-origins}") List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  public boolean isAllowed(String redirectUri) {
    if (redirectUri == null || redirectUri.isBlank()) {
      return false;
    }
    return allowedOrigins.stream()
        .map(String::trim)
        .anyMatch(origin -> redirectUri.equals(origin) || redirectUri.startsWith(origin + "/"));
  }
}
