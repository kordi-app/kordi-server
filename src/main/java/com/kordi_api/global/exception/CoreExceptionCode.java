package com.kordi_api.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 정의. {@code code}는 클라이언트가 분기에 사용하는 상세 코드이므로 기존 값을 바꾸지 않는다.
 *
 * <p>1xxx 공통 · 2xxx 인증 · 3xxx 유저 · 4xxx 친구
 */
@Getter
@RequiredArgsConstructor
public enum CoreExceptionCode {
  // 공통 (1xxx)
  REQUIRED_VALUE_MISSING(HttpStatus.BAD_REQUEST, 1001, "필수 값이 누락되었습니다."),
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, 1003, "요청이 유효하지 않습니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, 1004, "권한이 없습니다."),
  MALFORMED_REQUEST_BODY(HttpStatus.BAD_REQUEST, 1005, "요청 본문이 누락되었거나 형식이 올바르지 않습니다."),
  NOT_FOUND(HttpStatus.NOT_FOUND, 1006, "요청한 리소스를 찾을 수 없습니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, 1007, "지원하지 않는 HTTP 메서드입니다."),
  DATA_CONFLICT(HttpStatus.CONFLICT, 1008, "요청이 현재 데이터 상태와 충돌합니다."),
  TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, 1009, "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 1999, "서버 내부 오류가 발생했습니다."),

  // 인증 (2xxx)
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, 2001, "인증이 만료되었습니다."),
  INVALID_LOGIN_CODE(HttpStatus.UNAUTHORIZED, 2002, "로그인 코드가 유효하지 않거나 만료되었습니다."),
  LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, 2003, "로그인이 필요합니다."),

  // 유저 (3xxx)
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, 3001, "유저를 찾을 수 없습니다."),
  NICKNAME_DUPLICATED(HttpStatus.CONFLICT, 3002, "이미 사용 중인 닉네임입니다."),

  // 친구 (4xxx)
  CANNOT_ADD_SELF(HttpStatus.BAD_REQUEST, 4001, "자기 자신에게 친구 요청을 보낼 수 없습니다."),
  FRIENDSHIP_ALREADY_EXISTS(HttpStatus.CONFLICT, 4002, "이미 친구 요청이 존재합니다."),
  FRIENDSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, 4003, "친구 요청을 찾을 수 없습니다."),
  FRIENDSHIP_NOT_PENDING(HttpStatus.BAD_REQUEST, 4004, "대기 중인 요청만 처리할 수 있습니다.");

  private final HttpStatus status;
  private final int code;
  private final String message;
}
