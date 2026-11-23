package com.kordi_api.auth.service;

import com.kordi_api.auth.dto.TokenResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 로그인 직후 프론트로 넘길 1회용 코드. 토큰을 리다이렉트 URL에 실으면 브라우저 기록·접근 로그·Referer에 남으므로, URL에는 짧게 사는 코드만 싣고 프론트 서버가
 * 이 코드를 서버 대 서버로 토큰과 바꾼다. 한 번 쓰면 사라지고 1분 뒤 만료된다. 메모리 보관 (서버 1대 기준).
 *
 * <p>코드는 로그인을 시작한 브라우저의 nonce에 묶인다. 프론트가 로그인 시작 때 nonce를 자기 쿠키에 심고 여기로 넘기며, 교환할 때 그 쿠키의 nonce를 함께
 * 보낸다. 그래서 공격자가 자기 계정으로 받은 코드를 피해자에게 열게 해도(로그인 CSRF) 피해자 브라우저의 nonce와 맞지 않아 교환되지 않는다.
 */
@Component
public class LoginCodeService {

  static final long TTL_MS = 60_000;

  private record Entry(TokenResponse tokens, String nonce, long expiresAt) {}

  private final Map<String, Entry> codes = new ConcurrentHashMap<>();

  /**
   * @param nonce 로그인을 시작한 브라우저의 nonce. 없으면(프론트를 거치지 않고 시작한 로그인) 이 코드는 교환되지 않는다.
   */
  public String issue(TokenResponse tokens, String nonce, long nowMillis) {
    String code = UUID.randomUUID().toString();
    codes.put(code, new Entry(tokens, nonce, nowMillis + TTL_MS));
    return code;
  }

  public Optional<TokenResponse> redeem(String code, String nonce, long nowMillis) {
    if (code == null) {
      return Optional.empty();
    }
    Entry entry = codes.remove(code); // a wrong nonce burns the code too: no guessing
    if (entry == null || entry.expiresAt() < nowMillis || !sameNonce(entry.nonce(), nonce)) {
      return Optional.empty();
    }
    return Optional.of(entry.tokens());
  }

  private static boolean sameNonce(String expected, String actual) {
    return expected != null
        && actual != null
        && MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }

  @Scheduled(fixedRate = TTL_MS)
  void sweep() {
    sweep(System.currentTimeMillis());
  }

  void sweep(long nowMillis) {
    codes.values().removeIf(e -> e.expiresAt() < nowMillis);
  }
}
