package com.kordi_api.global.common;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;

/** 랭킹 범위: 전체, 또는 나와 수락된 친구만. */
public enum RankingScope {
  ALL,
  FRIENDS;

  /**
   * 친구 범위면 기준이 될 유저 id, 전체면 null. 친구 범위인데 로그인하지 않았으면 401.
   *
   * @param userId 로그인한 유저 (비회원이면 null)
   */
  public Long friendsOf(Long userId) {
    if (this == ALL) {
      return null;
    }
    if (userId == null) {
      throw new CoreException(CoreExceptionCode.LOGIN_REQUIRED);
    }
    return userId;
  }
}
