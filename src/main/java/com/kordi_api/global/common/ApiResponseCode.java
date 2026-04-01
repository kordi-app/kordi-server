package com.kordi_api.global.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApiResponseCode {

  // 2xx
  SUCCESS(200_00, "OK"),
  CREATED(201_00, "CREATED"),
  NO_CONTENT(204_00, "NO CONTENT"),

  // 4xx
  BAD_REQUEST(400_00, "BAD REQUEST"),
  UNAUTHORIZED(401_00, "UNAUTHORIZED"),
  METHOD_NOT_ALLOWED(405_00, "METHOD NOT ALLOWED"),
  CONFLICT(409_00, "CONFLICT"),

  // 5xx
  INTERNAL_SERVER_ERROR(500_00, "INTERNAL SERVER ERROR");

  private final int code;
  private final String message;
}
