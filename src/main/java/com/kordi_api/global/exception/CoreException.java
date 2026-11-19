package com.kordi_api.global.exception;

import lombok.Getter;

@Getter
public class CoreException extends RuntimeException {

  private final CoreExceptionCode errorCode;

  public CoreException(CoreExceptionCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public CoreException(CoreExceptionCode errorCode, String detail) {
    super(errorCode.getMessage() + " " + detail);
    this.errorCode = errorCode;
  }
}
