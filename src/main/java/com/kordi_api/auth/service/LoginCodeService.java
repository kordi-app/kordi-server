package com.kordi_api.auth.service;

import com.kordi_api.auth.dto.TokenResponse;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 로그인 직후 프론트로 넘길 1회용 코드. 토큰을 리다이렉트 URL에 실으면 브라우저 기록·접근 로그·Referer에 남으므로, URL에는 짧게 사는 코드만 싣고 프론트 서버가
 * 이 코드를 서버 대 서버로 토큰과 바꾼다. 한 번 쓰면 사라지고 1분 뒤 만료된다. 메모리 보관 (서버 1대 기준).
 */
@Component
public class LoginCodeService {

  static final long TTL_MS = 60_000;

  private record Entry(TokenResponse tokens, long expiresAt) {}

  private final Map<String, Entry> codes = new ConcurrentHashMap<>();

  public String issue(TokenResponse tokens, long nowMillis) {
    String code = UUID.randomUUID().toString();
    codes.put(code, new Entry(tokens, nowMillis + TTL_MS));
    return code;
  }

  public Optional<TokenResponse> redeem(String code, long nowMillis) {
    if (code == null) {
      return Optional.empty();
    }
    Entry entry = codes.remove(code);
    return entry == null || entry.expiresAt() < nowMillis
        ? Optional.empty()
        : Optional.of(entry.tokens());
  }

  @Scheduled(fixedRate = TTL_MS)
  void sweep() {
    sweep(System.currentTimeMillis());
  }

  void sweep(long nowMillis) {
    codes.values().removeIf(e -> e.expiresAt() < nowMillis);
  }
}
