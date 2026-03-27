package com.kordi_api.global.exception;

import static com.kordi_api.global.exception.CoreExceptionCode.UNKNOWN_ERROR;

import lombok.Getter;

@Getter
public class CoreException extends RuntimeException {

    private final Integer code;
    private final String message;

    public CoreException(CoreExceptionCode code) {
        this.code = code.getCode();
        this.message = code.getMessage();
    }

    public CoreException(CoreExceptionCode code, String additionalMessage) {
        this.code = code.getCode();
        this.message = code.getMessage() + "\n" + additionalMessage;
    }

    public CoreException() {
        this.code = UNKNOWN_ERROR.getCode();
        this.message = UNKNOWN_ERROR.getMessage();
    }
}
