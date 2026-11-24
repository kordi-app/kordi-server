package com.kordi_api.quiz.session;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.global.ratelimit.TokenBucketRateLimiter;
import com.kordi_api.global.store.ExpiringStoreFactory;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 퀴즈 판 열기·결과 내기를 유저마다 제한한다. 한 판에 두 요청(열기·내기)이고 한 판은 1분 넘게 걸리므로, 사람이 하는 속도(중간에 다시 시작하기, 응답을 못 받아 다시
 * 내기 포함)는 걸리지 않고 스크립트로 판을 마구 여는 것만 막는다.
 */
@Component
public class QuizRateLimiter {

  /** 한 번에 몰아 쓸 수 있는 요청 수 */
  static final int BURST = 12;

  /** 이 시간마다 요청 하나가 다시 허용된다 (분당 6회) */
  static final Duration REFILL_EVERY = Duration.ofSeconds(10);

  private final TokenBucketRateLimiter limiter;

  public QuizRateLimiter(ExpiringStoreFactory stores, Clock clock) {
    this.limiter = new TokenBucketRateLimiter(stores, clock, "quiz-session", BURST, REFILL_EVERY);
  }

  /** 토큰이 없으면 429. */
  public void check(Long userId) {
    if (!limiter.tryAcquire(String.valueOf(userId))) {
      throw new CoreException(CoreExceptionCode.TOO_MANY_REQUESTS);
    }
  }
}
