package com.kordi_api.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 리프레시 토큰은 SHA-256 해시로만 저장한다. 조회는 같음 비교뿐이라 해시로 충분하고, DB나 백업이 새도 살아 있는 세션을 넘겨주지 않는다. */
final class TokenHasher {

  private TokenHasher() {}

  static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
