package com.kordi_api.adventure.dto;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;

/**
 * 코드 모험 한 판의 결과.
 *
 * @param defeated 물리친 몬스터 수
 * @param durationMs 판 길이
 */
public record AdventureRecordRequest(int defeated, long durationMs) {

  public static final int MAX_DEFEATED = 1000;
  public static final long MAX_DURATION_MS = 3 * 60 * 60 * 1000L;

  /** 몬스터 하나에 최소 이만큼은 걸린다 (연주 + 연출) */
  public static final long MIN_MS_PER_MONSTER = 1500;

  /** 범위와 개연성(물리친 수에 비해 너무 짧은 판) 검사. 어긋나면 INVALID_REQUEST. */
  public void validate() {
    boolean valid =
        defeated >= 0
            && defeated <= MAX_DEFEATED
            && durationMs > 0
            && durationMs <= MAX_DURATION_MS
            && defeated * MIN_MS_PER_MONSTER <= durationMs;
    if (!valid) {
      throw new CoreException(CoreExceptionCode.INVALID_REQUEST);
    }
  }
}
