package com.kordi_api.global.ratelimit;

import com.kordi_api.global.store.ExpiringStore;
import com.kordi_api.global.store.ExpiringStoreFactory;
import java.time.Clock;
import java.time.Duration;

/**
 * 키(보통 유저 id)마다 토큰 버킷 하나. 버킷은 {@code capacity}개까지 차 있고 {@code refillEvery}마다 하나씩 다시 찬다. 요청 하나가 토큰
 * 하나를 쓰며, 토큰이 없으면 거절한다.
 *
 * <p>버킷은 {@link ExpiringStore}에 두고 {@link ExpiringStore#compute}로 원자적으로 바꾼다. 다 차는 데 걸리는 시간이 지나면 새
 * 버킷과 같으니 그만큼만 살려 둔다 (아무것도 안 하는 유저는 메모리를 차지하지 않는다).
 */
public class TokenBucketRateLimiter {

  /**
   * @param tokens 남은 토큰 (소수: 다시 차는 중인 몫 포함)
   * @param updatedAt 마지막으로 계산한 시각 (ms)
   * @param allowed 마지막 요청이 토큰을 받았는지
   */
  record Bucket(double tokens, long updatedAt, boolean allowed) {}

  private final ExpiringStore<Bucket> buckets;
  private final Clock clock;
  private final int capacity;
  private final long refillEveryMs;
  private final Duration ttl;

  public TokenBucketRateLimiter(
      ExpiringStoreFactory stores, Clock clock, String name, int capacity, Duration refillEvery) {
    if (capacity < 1 || refillEvery.isNegative() || refillEvery.isZero()) {
      throw new IllegalArgumentException("capacity >= 1 and a positive refill period required");
    }
    this.buckets = stores.create("rate-limit:" + name, Bucket.class);
    this.clock = clock;
    this.capacity = capacity;
    this.refillEveryMs = refillEvery.toMillis();
    this.ttl = refillEvery.multipliedBy(capacity);
  }

  /** 토큰 하나를 쓴다. 남아 있었으면 true, 없으면 false (거절된 요청은 토큰을 쓰지 않는다). */
  public boolean tryAcquire(String key) {
    long now = clock.millis();
    return buckets
        .compute(key, current -> next(current, now), ttl)
        .map(Bucket::allowed)
        .orElse(true);
  }

  private Bucket next(Bucket current, long now) {
    double tokens = capacity;
    if (current != null) {
      long elapsed = Math.max(0, now - current.updatedAt());
      tokens = Math.min(capacity, current.tokens() + (double) elapsed / refillEveryMs);
    }
    if (tokens >= 1) {
      return new Bucket(tokens - 1, now, true);
    }
    return new Bucket(tokens, now, false);
  }
}
