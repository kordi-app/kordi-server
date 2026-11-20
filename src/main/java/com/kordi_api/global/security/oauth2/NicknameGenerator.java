package com.kordi_api.global.security.oauth2;

import com.kordi_api.user.repository.UserRepository;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 새로 가입하는 유저의 첫 닉네임. 구글 이름을 그대로 쓰면 같은 이름의 두 번째 사람이 유니크 제약에 걸려 영영 로그인하지 못하므로, 허용 문자(한글·영문· 숫자·밑줄)만
 * 남기고 길이를 맞춘 뒤 겹치면 숫자를 붙인다. 유저는 나중에 프로필에서 바꿀 수 있다.
 */
@Component
@RequiredArgsConstructor
public class NicknameGenerator {

  static final int MIN = 2;
  static final int MAX = 20;
  private static final String FALLBACK = "kordi";

  private final UserRepository userRepository;

  public String uniqueFrom(String name) {
    String base = sanitize(name);
    if (!userRepository.existsByNickname(base)) {
      return base;
    }
    for (int i = 0; i < 20; i++) {
      String suffix = "_" + ThreadLocalRandom.current().nextInt(1000, 10000);
      String candidate = base.substring(0, Math.min(base.length(), MAX - suffix.length())) + suffix;
      if (!userRepository.existsByNickname(candidate)) {
        return candidate;
      }
    }
    return FALLBACK + "_" + System.currentTimeMillis() % 100_000_000;
  }

  static String sanitize(String name) {
    String cleaned = name == null ? "" : name.replaceAll("[^0-9A-Za-z가-힣_]", "");
    if (cleaned.length() < MIN) {
      cleaned = FALLBACK;
    }
    return cleaned.length() > MAX ? cleaned.substring(0, MAX) : cleaned;
  }
}
