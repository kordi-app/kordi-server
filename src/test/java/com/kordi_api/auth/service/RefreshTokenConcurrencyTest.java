package com.kordi_api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.auth.dto.TokenResponse;
import com.kordi_api.auth.repository.RefreshTokenRepository;
import com.kordi_api.global.store.ExpiringStore;
import com.kordi_api.support.MySqlContainerConfig;
import com.kordi_api.user.entity.OAuthProvider;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** 서버 재시작으로 "방금 회전된 토큰 쌍" 메모리가 비었을 때, 직전 토큰으로 동시에 들어온 재발급들이 서로의 새 토큰을 덮어쓰지 않는지 실제 MySQL에서 본다. */
@SpringBootTest(
    properties = {
      "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "spring.security.oauth2.client.registration.google.client-id=test",
      "spring.security.oauth2.client.registration.google.client-secret=test"
    })
@Import(MySqlContainerConfig.class)
class RefreshTokenConcurrencyTest {

  private static final int CALLERS = 4;

  @Autowired AuthService authService;
  @Autowired UserRepository userRepository;
  @Autowired RefreshTokenRepository refreshTokenRepository;

  @Test
  void 재시작_뒤_직전_토큰으로_동시에_재발급해도_모두_같은_유효한_토큰을_받는다() throws Exception {
    for (int round = 0; round < 5; round++) {
      User user = newUser();
      String original = authService.issueTokens(user).refreshToken();
      authService.reissueToken(original); // rotated: `original` is now the previous token
      forgetRecentRotations(original); // as if the server restarted

      List<TokenResponse> results = reissueInParallel(original);

      String winner = results.get(0).refreshToken();
      assertThat(results).allSatisfy(r -> assertThat(r.refreshToken()).isEqualTo(winner));
      assertThat(refreshTokenRepository.findByUserId(user.getId()).orElseThrow().getToken())
          .isEqualTo(TokenHasher.hash(winner));
      // and that token keeps working
      assertThat(authService.reissueToken(winner).refreshToken()).isNotEqualTo(winner);
    }
  }

  private List<TokenResponse> reissueInParallel(String token) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(CALLERS);
    try {
      CountDownLatch go = new CountDownLatch(1);
      List<Future<TokenResponse>> futures = new ArrayList<>();
      for (int i = 0; i < CALLERS; i++) {
        futures.add(
            pool.submit(
                () -> {
                  go.await();
                  return authService.reissueToken(token);
                }));
      }
      go.countDown();
      List<TokenResponse> out = new ArrayList<>();
      for (Future<TokenResponse> f : futures) {
        out.add(f.get(30, TimeUnit.SECONDS));
      }
      return out;
    } finally {
      pool.shutdownNow();
    }
  }

  @SuppressWarnings("unchecked")
  private void forgetRecentRotations(String previousRaw) {
    AuthService target = AopTestUtils.getTargetObject(authService);
    ((ExpiringStore<TokenResponse>) ReflectionTestUtils.getField(target, "recentRotations"))
        .remove(TokenHasher.hash(previousRaw));
  }

  private User newUser() {
    String id = UUID.randomUUID().toString().substring(0, 12);
    return userRepository.save(
        User.builder()
            .email("rt-" + id + "@example.com")
            .nickname("rt" + id)
            .oauthProvider(OAuthProvider.GOOGLE)
            .oauthId("rt-" + id)
            .build());
  }
}
