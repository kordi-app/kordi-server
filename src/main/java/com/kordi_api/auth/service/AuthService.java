package com.kordi_api.auth.service;

import com.kordi_api.auth.dto.TokenResponse;
import com.kordi_api.auth.entity.RefreshToken;
import com.kordi_api.auth.repository.RefreshTokenRepository;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.global.security.jwt.JwtProperties;
import com.kordi_api.global.security.jwt.JwtTokenProvider;
import com.kordi_api.user.entity.User;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  /** 회전 직후 직전 토큰을 계속 받아주는 시간. 동시 요청(멀티 탭, RSC 병렬 요청)의 재발급 경합을 흡수한다. */
  static final Duration ROTATION_GRACE_PERIOD = Duration.ofSeconds(30);

  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;

  /**
   * 방금 회전된 토큰 쌍, 직전 토큰의 해시로 찾는다. DB에는 해시만 있어 유예 시간 안의 동시 요청에게 같은 새 토큰을 돌려주려면 여기서 꺼내야 한다 (유예 시간만큼만
   * 보관, 서버 1대 기준).
   */
  private final Map<String, RecentRotation> recentRotations = new ConcurrentHashMap<>();

  private record RecentRotation(TokenResponse tokens, LocalDateTime until) {}

  /** 로그인 성공 시 토큰 쌍을 발급한다. 유저의 기존 리프레시 토큰은 대체된다. */
  @Transactional
  public TokenResponse issueTokens(User user) {
    String refreshToken = jwtTokenProvider.createRefreshToken();
    String stored = TokenHasher.hash(refreshToken);
    LocalDateTime expiredAt = refreshTokenExpiredAt(LocalDateTime.now());

    refreshTokenRepository
        .findByUserId(user.getId())
        .ifPresentOrElse(
            saved -> saved.replace(stored, expiredAt),
            () ->
                refreshTokenRepository.save(
                    RefreshToken.builder().token(stored).user(user).expiredAt(expiredAt).build()));

    return new TokenResponse(
        jwtTokenProvider.createAccessToken(user.getId(), user.getRole()), refreshToken);
  }

  // 만료 토큰 삭제 후 예외를 던지므로, 삭제가 롤백되지 않도록 CoreException은 롤백 대상에서 제외
  @Transactional(noRollbackFor = CoreException.class)
  public TokenResponse reissueToken(String refreshToken) {
    LocalDateTime now = LocalDateTime.now();

    String hashed = TokenHasher.hash(refreshToken);

    // stored as a hash; rows written before hashing hold the raw token (accepted once, re-stored
    // hashed). A value shaped like a stored hash never goes to that raw lookup: otherwise a leaked
    // hash column could be replayed as a token.
    // TODO: drop the raw lookup once refresh tokens issued before 2026-11-20 have expired (14 days)
    var current =
        refreshTokenRepository
            .findByTokenForUpdate(hashed)
            .or(
                () ->
                    TokenHasher.looksLikeHash(refreshToken)
                        ? java.util.Optional.empty()
                        : refreshTokenRepository.findByTokenForUpdate(refreshToken));
    if (current.isPresent()) {
      return rotate(current.get(), now);
    }

    var rotatedAway = refreshTokenRepository.findByPreviousToken(hashed);
    if (rotatedAway.isEmpty()) {
      throw new CoreException(CoreExceptionCode.INVALID_REFRESH_TOKEN);
    }
    RefreshToken saved = rotatedAway.get();
    if (saved.isExpired(now)) {
      throw new CoreException(CoreExceptionCode.INVALID_REFRESH_TOKEN);
    }
    if (!saved.isPreviousTokenUsable(now, ROTATION_GRACE_PERIOD)) {
      // an old token replayed after its grace window: likely stolen — end the session
      refreshTokenRepository.delete(saved);
      throw new CoreException(CoreExceptionCode.INVALID_REFRESH_TOKEN);
    }
    // a parallel request rotated it a moment ago: hand back the same new pair it got
    RecentRotation recent = recentRotations.get(hashed);
    if (recent != null && !now.isAfter(recent.until())) {
      return new TokenResponse(createAccessToken(saved), recent.tokens().refreshToken());
    }
    // (that pair is gone — e.g. the server restarted in between): rotate once more for this caller
    String newRefreshToken = jwtTokenProvider.createRefreshToken();
    saved.rotateKeepingPrevious(TokenHasher.hash(newRefreshToken), refreshTokenExpiredAt(now));
    return new TokenResponse(createAccessToken(saved), newRefreshToken);
  }

  /** 로그아웃: 로그인 상태면 그 유저의 세션을, 아니면(액세스 토큰 만료) 넘겨받은 리프레시 토큰의 세션을 끝낸다. 둘 다 없으면 할 일이 없다. */
  @Transactional
  public void logout(Long userId, String refreshToken) {
    if (userId != null) {
      refreshTokenRepository.deleteByUserId(userId);
      return;
    }
    if (refreshToken != null && !refreshToken.isBlank()) {
      refreshTokenRepository.deleteByToken(TokenHasher.hash(refreshToken));
      if (!TokenHasher.looksLikeHash(refreshToken)) {
        refreshTokenRepository.deleteByToken(refreshToken); // a row from before hashing
      }
    }
  }

  private TokenResponse rotate(RefreshToken saved, LocalDateTime now) {
    if (saved.isExpired(now)) {
      refreshTokenRepository.delete(saved);
      throw new CoreException(CoreExceptionCode.INVALID_REFRESH_TOKEN);
    }
    String previousHash = saved.getToken();
    String newRefreshToken = jwtTokenProvider.createRefreshToken();
    saved.rotate(TokenHasher.hash(newRefreshToken), refreshTokenExpiredAt(now), now);
    TokenResponse tokens = new TokenResponse(createAccessToken(saved), newRefreshToken);
    recentRotations.values().removeIf(r -> now.isAfter(r.until()));
    recentRotations.put(previousHash, new RecentRotation(tokens, now.plus(ROTATION_GRACE_PERIOD)));
    return tokens;
  }

  private String createAccessToken(RefreshToken saved) {
    User user = saved.getUser();
    return jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
  }

  private LocalDateTime refreshTokenExpiredAt(LocalDateTime now) {
    return now.plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiry()));
  }
}
