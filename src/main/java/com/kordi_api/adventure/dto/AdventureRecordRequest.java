package com.kordi_api.adventure.dto;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;

/**
 * 코드 모험 한 판의 결과. 판 길이는 클라이언트가 보내지 않는다: 서버가 {@code runId}의 시작 시각부터 잰다.
 *
 * @param runId 판을 시작할 때 받은 id
 * @param defeated 물리친 몬스터 수
 */
public record AdventureRecordRequest(String runId, int defeated) {

  public static final int MAX_DEFEATED = 1000;

  /** 몬스터 하나에 최소 이만큼은 걸린다 (처치 연출 1.9초 + 연주). 클라이언트 rules.ts MIN_MS_PER_MONSTER와 짝 */
  public static final long MIN_MS_PER_MONSTER = 2000;

  /** 범위와 개연성(서버가 잰 판 길이에 비해 너무 많이 물리친 경우) 검사. 어긋나면 INVALID_REQUEST. */
  public void validate(long durationMs) {
    boolean valid =
        defeated >= 0 && defeated <= MAX_DEFEATED && defeated * MIN_MS_PER_MONSTER <= durationMs;
    if (!valid) {
      throw new CoreException(CoreExceptionCode.INVALID_REQUEST);
    }
  }
}
