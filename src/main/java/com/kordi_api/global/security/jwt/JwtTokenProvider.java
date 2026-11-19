package com.kordi_api.global.security.jwt;

import com.kordi_api.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  private static final String ROLE_CLAIM = "role";
  private static final int REFRESH_TOKEN_BYTES = 32;

  private final JwtProperties jwtProperties;
  private final SecretKey signingKey;
  private final JwtParser parser;
  private final SecureRandom secureRandom = new SecureRandom();

  public JwtTokenProvider(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
    this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtProperties.getSecret()));
    this.parser = Jwts.parser().verifyWith(signingKey).build();
  }

  public String createAccessToken(Long userId, UserRole role) {
    Date now = new Date();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim(ROLE_CLAIM, role.name())
        .issuedAt(now)
        .expiration(new Date(now.getTime() + jwtProperties.getAccessTokenExpiry()))
        .signWith(signingKey)
        .compact();
  }

  /** 리프레시 토큰은 서버(DB)에서 상태를 관리하므로 JWT가 아닌 불투명한 난수 문자열을 사용한다. */
  public String createRefreshToken() {
    byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** 서명·만료를 검증하고 인증 정보를 꺼낸다. 유효하지 않으면 empty. */
  public Optional<TokenClaims> parseAccessToken(String token) {
    try {
      Claims claims = parser.parseSignedClaims(token).getPayload();
      Long userId = Long.valueOf(claims.getSubject());
      String role = claims.get(ROLE_CLAIM, String.class);
      // role 클레임 도입 이전에 발급된 토큰은 일반 유저로 취급
      return Optional.of(
          new TokenClaims(userId, role == null ? UserRole.USER : UserRole.valueOf(role)));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public record TokenClaims(Long userId, UserRole role) {}
}
