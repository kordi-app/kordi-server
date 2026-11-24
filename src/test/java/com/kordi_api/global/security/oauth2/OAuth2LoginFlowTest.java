package com.kordi_api.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kordi_api.auth.service.LoginCodeService;
import com.kordi_api.support.MySqlContainerConfig;
import com.kordi_api.user.entity.OAuthProvider;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 구글 로그인 시작 → 콜백 → 1회용 코드까지. 인가 요청(state)은 서명 쿠키로만 오가고 HttpSession은 한 번도 생기지 않는다. 구글 토큰 엔드포인트와 유저
 * 정보 조회만 가짜로 바꾼다.
 */
@SpringBootTest(
    properties = {
      "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "spring.security.oauth2.client.registration.google.client-id=test",
      "spring.security.oauth2.client.registration.google.client-secret=test"
    })
@Import({MySqlContainerConfig.class, OAuth2LoginFlowTest.FakeGoogle.class})
class OAuth2LoginFlowTest {

  private static final String CALLBACK = "https://www.kordi.online/api/auth/callback";
  private static final String NONCE = "n".repeat(43);

  @TestConfiguration(proxyBeanMethods = false)
  static class FakeGoogle {
    /** 구글 토큰 엔드포인트 대신 (Spring Security가 이 타입의 빈을 집어 쓴다) */
    @Bean
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> fakeTokenClient() {
      return request ->
          OAuth2AccessTokenResponse.withToken("google-access-token")
              .tokenType(OAuth2AccessToken.TokenType.BEARER)
              .expiresIn(3600)
              .build();
    }
  }

  @MockitoBean CustomOAuth2UserService userService;
  @Autowired WebApplicationContext context;
  @Autowired UserRepository userRepository;
  @Autowired LoginCodeService loginCodeService;

  private MockMvc mvc;
  private String sub;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    sub = "google-" + UUID.randomUUID();
    userRepository.save(
        User.builder()
            .email(sub + "@example.com")
            .nickname("n" + sub.substring(sub.length() - 12))
            .oauthProvider(OAuthProvider.GOOGLE)
            .oauthId(sub)
            .build());
    given(userService.loadUser(any()))
        .willReturn(
            new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", sub, "email", sub + "@example.com", "name", "tester"),
                "sub"));
  }

  @Test
  void 로그인_시작은_세션을_만들지_않고_서명된_쿠키에_state를_담는다() throws Exception {
    MvcResult start = start();

    assertThat(start.getRequest().getSession(false)).isNull();
    String location = start.getResponse().getRedirectedUrl();
    assertThat(location).startsWith("https://accounts.google.com/");
    String authCookie =
        setCookies(start.getResponse()).stream()
            .filter(c -> c.startsWith(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME + "="))
            .findFirst()
            .orElseThrow();
    assertThat(authCookie).contains("HttpOnly").contains("SameSite=Lax").contains("Max-Age=180");
  }

  @Test
  void 콜백까지_왕복하면_1회용_코드가_nonce와_함께_돌아오고_쿠키는_지워진다() throws Exception {
    MvcResult start = start();
    String state = state(start);

    MvcResult callback =
        mvc.perform(
                get("/login/oauth2/code/google")
                    .param("code", "google-code")
                    .param("state", state)
                    .cookie(cookies(start.getResponse())))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    assertThat(callback.getRequest().getSession(false)).isNull();
    String redirected = callback.getResponse().getRedirectedUrl();
    assertThat(redirected).startsWith(CALLBACK + "?code=");
    String code =
        UriComponentsBuilder.fromUriString(redirected).build().getQueryParams().getFirst("code");
    assertThat(loginCodeService.redeem(code, NONCE)).isPresent();
    assertThat(setCookies(callback.getResponse()))
        .anyMatch(
            c ->
                c.startsWith(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME + "=")
                    && c.contains("Max-Age=0"));
  }

  @Test
  void 쿠키를_고치거나_state가_다르면_로그인_실패로_돌려보낸다() throws Exception {
    MvcResult start = start();
    String state = state(start);
    List<Cookie> tampered = new ArrayList<>();
    for (Cookie c : cookies(start.getResponse())) {
      if (c.getName().equals(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME)) {
        String v = c.getValue();
        c = new Cookie(c.getName(), (v.charAt(0) == 'A' ? "B" : "A") + v.substring(1));
      }
      tampered.add(c);
    }

    assertFailed(
        mvc.perform(
                get("/login/oauth2/code/google")
                    .param("code", "google-code")
                    .param("state", state)
                    .cookie(tampered.toArray(Cookie[]::new)))
            .andReturn());
    assertFailed(
        mvc.perform(
                get("/login/oauth2/code/google")
                    .param("code", "google-code")
                    .param("state", state + "x")
                    .cookie(cookies(start.getResponse())))
            .andReturn());
  }

  private void assertFailed(MvcResult result) {
    assertThat(result.getRequest().getSession(false)).isNull();
    assertThat(result.getResponse().getRedirectedUrl()).contains("error=login_failed");
  }

  private MvcResult start() throws Exception {
    return mvc.perform(
            get("/oauth2/authorization/google")
                .param("redirect_uri", CALLBACK)
                .param("login_nonce", NONCE))
        .andExpect(status().is3xxRedirection())
        .andReturn();
  }

  private static String state(MvcResult start) {
    return Optional.ofNullable(
            UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl())
                .build()
                .getQueryParams()
                .getFirst("state"))
        .map(s -> java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8))
        .orElseThrow();
  }

  private static List<String> setCookies(MockHttpServletResponse response) {
    return response.getHeaders(HttpHeaders.SET_COOKIE);
  }

  /** 브라우저처럼: 응답이 심은 쿠키 중 지우라는 것(Max-Age=0)은 빼고 돌려보낸다. */
  private static Cookie[] cookies(MockHttpServletResponse response) {
    List<Cookie> out = new ArrayList<>();
    for (String header : setCookies(response)) {
      for (HttpCookie c : HttpCookie.parse(header)) {
        if (c.getMaxAge() != 0 && !c.getValue().isEmpty()) {
          out.add(new Cookie(c.getName(), c.getValue()));
        }
      }
    }
    return out.toArray(Cookie[]::new);
  }
}
