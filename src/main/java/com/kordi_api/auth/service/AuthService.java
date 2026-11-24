package com.kordi_api.auth.service;

import com.kordi_api.auth.dto.TokenResponse;
import com.kordi_api.auth.entity.RefreshToken;
import com.kordi_api.auth.repository.RefreshTokenRepository;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.global.security.jwt.JwtProperties;
import com.kordi_api.global.security.jwt.JwtTokenProvider;
import com.kordi_api.global.store.ExpiringStore;
import com.kordi_api.global.store.ExpiringStoreFactory;
import com.kordi_api.user.entity.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  /** 회전 직후 직전 토큰을 계속 받아주는 시간. 동시 요청(멀티 탭, RSC 병렬 요청)의 재발급 경합을 흡수한다. */
  static final Duration ROTATION_GRACE_PERIOD = Duration.ofSeconds(30);

  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;
  private final Clock clock;

  /**
   * 방금 회전된 토큰 쌍, 직전 토큰의 해시로 찾는다. DB에는 해시만 있어 유예 시간 안의 동시 요청에게 같은 새 토큰을 돌려주려면 여기서 꺼내야 한다 (유예 시간만큼만
   * 보관).
   */
  private final ExpiringStore<TokenResponse> recentRotations;

  public AuthService(
      JwtTokenProvider jwtTokenProvider,
      RefreshTokenRepository refreshTokenRepository,
      JwtProperties jwtProperties,
      ExpiringStoreFactory stores,
      Clock clock) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.refreshTokenRepository = refreshTokenRepository;
    this.jwtProperties = jwtProperties;
    this.clock = clock;
    this.recentRotations = stores.create("recent-rotation", TokenResponse.class);
  }

  /** 로그인 성공 시 토큰 쌍을 발급한다. 유저의 기존 리프레시 토큰은 대체된다. */
  @Transactional
  public TokenResponse issueTokens(User user) {
    String refreshToken = jwtTokenProvider.createRefreshToken();
    String stored = TokenHasher.hash(refreshToken);
    Instant expiredAt = refreshTokenExpiredAt(clock.instant());

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

  // 만료 토큰 삭제 후 예외를 던지므로, 삭제가 롤백되지 않도록 CoreException은 롤백 대상에서 제외.
  // READ_COMMITTED: 잠금 조회가 찾은 행만 잠그고 빈 구간(gap)은 잠그지 않는다. REPEATABLE READ에서는 현재 토큰 조회가 빗나간 두
  // 요청이 서로의 gap 잠금에 막혀(앞 요청의 새 토큰 쓰기 vs 뒤 요청의 행 잠금 대기) 교착에 빠진다.
  @Transactional(noRollbackFor = CoreException.class, isolation = Isolation.READ_COMMITTED)
  public TokenResponse reissueToken(String refreshToken) {
    Instant now = clock.instant();

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

    // row-locked: parallel callers holding the same previous token queue up here, so the check of
    // recentRotations below sees what the one before it did
    var rotatedAway = refreshTokenRepository.findByPreviousTokenForUpdate(hashed);
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
    var recent = recentRotations.get(hashed);
    if (recent.isPresent()) {
      return new TokenResponse(createAccessToken(saved), recent.get().refreshToken());
    }
    // (that pair is gone — e.g. the server restarted in between): rotate once more for this caller,
    // and remember the new pair so the callers queued behind on the row lock get this same pair
    // instead of each rotating again (last write would win and log the others out). Put before
    // commit on purpose: the next caller can only read it after this commit releases the lock.
    String newRefreshToken = jwtTokenProvider.createRefreshToken();
    saved.rotateKeepingPrevious(TokenHasher.hash(newRefreshToken), refreshTokenExpiredAt(now));
    TokenResponse tokens = new TokenResponse(createAccessToken(saved), newRefreshToken);
    recentRotations.put(hashed, tokens, remainingGrace(saved, now));
    return tokens;
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

  private TokenResponse rotate(RefreshToken saved, Instant now) {
    if (saved.isExpired(now)) {
      refreshTokenRepository.delete(saved);
      throw new CoreException(CoreExceptionCode.INVALID_REFRESH_TOKEN);
    }
    String previousHash = saved.getToken();
    String newRefreshToken = jwtTokenProvider.createRefreshToken();
    saved.rotate(TokenHasher.hash(newRefreshToken), refreshTokenExpiredAt(now), now);
    TokenResponse tokens = new TokenResponse(createAccessToken(saved), newRefreshToken);
    recentRotations.put(previousHash, tokens, ROTATION_GRACE_PERIOD);
    return tokens;
  }

  /** 유예 시간이 끝나는 시각까지만 보관 (그 뒤엔 직전 토큰 자체를 받아주지 않는다). */
  private static Duration remainingGrace(RefreshToken saved, Instant now) {
    Duration left = Duration.between(now, saved.getRotatedAt().plus(ROTATION_GRACE_PERIOD));
    return left.isNegative() || left.isZero() ? Duration.ofMillis(1) : left;
  }

  private String createAccessToken(RefreshToken saved) {
    User user = saved.getUser();
    return jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
  }

  private Instant refreshTokenExpiredAt(Instant now) {
    return now.plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiry()));
  }
}
