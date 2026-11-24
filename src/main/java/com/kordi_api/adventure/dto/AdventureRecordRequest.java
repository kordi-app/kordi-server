package com.kordi_api.adventure.dto;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;

/**
 * 코드 모험 한 판의 결과. 판 길이는 클라이언트가 보내지 않는다: 서버가 {@code runId}의 시작 시각부터 잰다.
 *
 * <p><b>임시 방어</b>: 물리친 수는 여전히 클라이언트가 주장하는 값이다. 서버는 판 길이에 비해 말이 되는지만 본다 — 사람이 낼 수 있는 속도보다 빠르거나 상한을
 * 넘으면 거절하고, 가능은 하지만 드문 기록은 {@link Verdict#SUSPICIOUS}로 표시해 로그로 남긴다. 오래 기다렸다가 그럴듯한 수를 보내는 조작은 막지
 * 못한다.
 *
 * <p>제대로 하려면 퀴즈 세션처럼 서버가 몬스터(코드)를 내고 채점해야 한다: 판을 시작하면 서버가 n번째 몬스터의 코드와 제한 시간을 정해 주고, 클라이언트는 몬스터마다 친
 * 음을 보내며, 서버가 맞았는지·제한 시간 안인지 판정해 물리친 수를 스스로 센다 (클라이언트는 수를 보내지 않는다).
 *
 * @param runId 판을 시작할 때 받은 id
 * @param defeated 물리친 몬스터 수
 */
public record AdventureRecordRequest(String runId, int defeated) {

  /** 하드 상한. 바닥 제한 시간(6초)·어려운 코드 위주 구간에서 이보다 오래 버티는 판은 현실적이지 않다. */
  public static final int MAX_DEFEATED = 500;

  /** 판 시작 연출 (클라이언트 ADVENTURE_INTRO_MS) */
  public static final long INTRO_MS = 1200;

  /**
   * 몬스터 하나에 드는 최소 시간: 처치 연출 1.9초(ADVENTURE_VICTORY_MS) + 코드를 읽고 누르는 데 최소 0.6초. 계속 이 속도로 쳐 내는 것도
   * 사람에겐 이미 비현실적이다. 이보다 빠르면 거절.
   */
  public static final long MIN_MS_PER_MONSTER = 2500;

  /** 평균이 이보다 빠르면(가능은 하지만 아주 빠름) 의심 기록으로 표시 */
  public static final long SUSPICIOUS_MS_PER_MONSTER = 3500;

  /** 이만큼 넘게 물리친 판은 의심 기록으로 표시 (드문 기록은 사람이 한 번 들여다볼 수 있게) */
  public static final int SUSPICIOUS_DEFEATED = 150;

  public enum Verdict {
    PLAUSIBLE,
    /** 받아들이되 로그로 남긴다 */
    SUSPICIOUS
  }

  /** 범위와 개연성(서버가 잰 판 길이에 비해 너무 많이 물리친 경우) 검사. 불가능하면 INVALID_REQUEST. */
  public Verdict validate(long durationMs) {
    boolean valid =
        defeated >= 0
            && defeated <= MAX_DEFEATED
            && (defeated == 0 || INTRO_MS + defeated * MIN_MS_PER_MONSTER <= durationMs);
    if (!valid) {
      throw new CoreException(CoreExceptionCode.INVALID_REQUEST);
    }
    boolean suspicious =
        defeated > SUSPICIOUS_DEFEATED
            || (defeated > 0 && INTRO_MS + defeated * SUSPICIOUS_MS_PER_MONSTER > durationMs);
    return suspicious ? Verdict.SUSPICIOUS : Verdict.PLAUSIBLE;
  }
}
