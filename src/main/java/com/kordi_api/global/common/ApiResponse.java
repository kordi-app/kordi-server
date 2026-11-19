package com.kordi_api.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 공통 응답 포맷.
 *
 * <ul>
 *   <li>성공: {@code { "data": ... }} — payload 타입(객체/리스트/스칼라)과 관계없이 항상 data 키로 감싼다.
 *   <li>실패: {@code { "code": 4001, "message": "...", "path": "/api/..." }}
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(Integer code, String message, String path, T data) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(null, null, null, data);
  }

  public static ApiResponse<Void> success() {
    return new ApiResponse<>(null, null, null, null);
  }

  public static ApiResponse<Void> error(int code, String message, String path) {
    return new ApiResponse<>(code, message, path, null);
  }
}
