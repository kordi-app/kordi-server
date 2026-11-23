package com.kordi_api.global.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** 구글 동의 취소나 가입 처리 실패 시 API 도메인의 기본 로그인 페이지 대신 프론트 콜백으로 돌려보낸다. 코드가 없으니 프론트는 로그인 화면으로 보낸다. */
@Slf4j
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

  @Value("${app.oauth2.redirect-url}")
  private String redirectUrl;

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    log.warn("[OAuth2LoginFailure] {}", exception.getMessage());
    String url =
        UriComponentsBuilder.fromUriString(redirectUrl)
            .queryParam("error", "login_failed")
            .build()
            .toUriString();
    getRedirectStrategy().sendRedirect(request, response, url);
  }
}
