package com.kordi_api.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

  private final JwtProperties jwtProperties;

  private SecretKey getSigningKey() {
    byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
    return Keys.hmacShaKeyFor(keyBytes);
  }

  public String createAccessToken(Long userId) {
    return createToken(userId, jwtProperties.getAccessTokenExpiry());
  }

  public String createRefreshToken(Long userId) {
    return createToken(userId, jwtProperties.getRefreshTokenExpiry());
  }

  private String createToken(Long userId, long expiry) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + expiry);

    return Jwts.builder()
        .subject(String.valueOf(userId))
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(getSigningKey())
        .compact();
  }

  public Long getUserId(String token) {
    Claims claims = parseClaims(token);
    return Long.valueOf(claims.getSubject());
  }

  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
  }
}
