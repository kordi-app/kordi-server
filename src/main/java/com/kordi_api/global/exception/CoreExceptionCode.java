package com.kordi_api.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CoreExceptionCode {

  // 시스템 공통 에러 (1xxx)
  REQUIRED_VALUE_MISSING(400_1001, "필수 값이 누락되었습니다."),
  UNKNOWN_ERROR(400_1002, "알 수 없는 오류가 발생했습니다."),
  INVALID_REQUEST(400_1003, "요청이 유효하지 않습니다."),

  // 인증 에러 (2xxx)
  INVALID_REFRESH_TOKEN(401_2001, "인증이 만료되었습니다."),

  // 유저 에러 (3xxx)
  USER_NOT_FOUND(404_3001, "유저를 찾을 수 없습니다."),
  NICKNAME_DUPLICATED(409_3002, "이미 사용 중인 닉네임입니다.");

  private final int code;
  private final String message;

  public int getHttpStatus() {
    return this.code / 10000;
  }

  public int getDetailCode() {
    return this.code % 10000;
  }
}
