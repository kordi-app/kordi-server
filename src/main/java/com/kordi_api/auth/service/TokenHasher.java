package com.kordi_api.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 리프레시 토큰은 SHA-256 해시로만 저장한다. 조회는 같음 비교뿐이라 해시로 충분하고, DB나 백업이 새도 살아 있는 세션을 넘겨주지 않는다. */
final class TokenHasher {

  private TokenHasher() {}

  private static final java.util.regex.Pattern HASH_FORM =
      java.util.regex.Pattern.compile("[0-9a-f]{64}");

  /** 저장된 해시와 같은 모양인지. 원본 토큰은 43자 base64url이라 이 모양이 될 수 없다. */
  static boolean looksLikeHash(String token) {
    return token != null && HASH_FORM.matcher(token).matches();
  }

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
