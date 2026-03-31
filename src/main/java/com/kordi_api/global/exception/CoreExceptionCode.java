package com.kordi_api.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CoreExceptionCode {

  // 시스템 공통 에러
  REQUIRED_VALUE_MISSING(400_0000, "필수 값이 누락되었습니다."),
  UNKNOWN_ERROR(400_1001, "알 수 없는 오류가 발생했습니다."),
  INVALID_REQUEST(400_1002, "요청이 유효하지 않습니다."),

  USER_NOT_FOUND(404_0001, "유저를 찾을 수 없습니다.");

  private final int code;
  private final String message;

  public int getHttpStatus() {
    return this.code / 10000;
  }

  public int getDetailCode() {
    return this.code % 10000;
  }
}
