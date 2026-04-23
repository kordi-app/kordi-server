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
  NICKNAME_DUPLICATED(409_3002, "이미 사용 중인 닉네임입니다."),

  // 친구 에러 (4xxx)
  CANNOT_ADD_SELF(400_4001, "자기 자신에게 친구 요청을 보낼 수 없습니다."),
  FRIENDSHIP_ALREADY_EXISTS(409_4002, "이미 친구 요청이 존재합니다."),
  FRIENDSHIP_NOT_FOUND(404_4003, "친구 요청을 찾을 수 없습니다."),
  FRIENDSHIP_NOT_PENDING(400_4004, "대기 중인 요청만 처리할 수 있습니다.");

  private final int code;
  private final String message;

  public int getHttpStatus() {
    return this.code / 10000;
  }

  public int getDetailCode() {
    return this.code % 10000;
  }
}
