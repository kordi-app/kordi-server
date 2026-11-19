package com.kordi_api.global.exception;

import com.kordi_api.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(CoreException.class)
  public ResponseEntity<ApiResponse<Void>> handleCoreException(
      CoreException e, HttpServletRequest request) {
    if (e.getErrorCode().getStatus().is5xxServerError()) {
      log.error("[CoreException] {}", e.getMessage(), e);
    }
    return toResponse(e.getErrorCode(), e.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(
      MethodArgumentNotValidException e, HttpServletRequest request) {
    String fields =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + defaultMessage(error))
            .collect(Collectors.joining(", "));
    CoreExceptionCode code = CoreExceptionCode.REQUIRED_VALUE_MISSING;
    return toResponse(code, code.getMessage() + " [" + fields + "]", request);
  }

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MissingRequestHeaderException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleBadParameter(HttpServletRequest request) {
    return toResponse(CoreExceptionCode.INVALID_REQUEST, request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpServletRequest request) {
    return toResponse(CoreExceptionCode.MALFORMED_REQUEST_BODY, request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(HttpServletRequest request) {
    return toResponse(CoreExceptionCode.NOT_FOUND, request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpServletRequest request) {
    return toResponse(CoreExceptionCode.METHOD_NOT_ALLOWED, request);
  }

  /** 유니크 제약 위반 등 동시 요청 경합으로 인한 충돌 */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(
      DataIntegrityViolationException e, HttpServletRequest request) {
    log.warn("[DataIntegrityViolation] {}", e.getMostSpecificCause().getMessage());
    return toResponse(CoreExceptionCode.DATA_CONFLICT, request);
  }

  /** 예상하지 못한 예외: 내부 메시지는 로그에만 남기고 클라이언트에는 고정 메시지를 내려준다. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(
      Exception e, HttpServletRequest request) {
    log.error("[Exception] {}", e.getMessage(), e);
    return toResponse(CoreExceptionCode.INTERNAL_SERVER_ERROR, request);
  }

  private ResponseEntity<ApiResponse<Void>> toResponse(
      CoreExceptionCode code, HttpServletRequest request) {
    return toResponse(code, code.getMessage(), request);
  }

  private ResponseEntity<ApiResponse<Void>> toResponse(
      CoreExceptionCode code, String message, HttpServletRequest request) {
    return ResponseEntity.status(code.getStatus())
        .body(ApiResponse.error(code.getCode(), message, request.getRequestURI()));
  }

  private String defaultMessage(FieldError error) {
    return error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage();
  }
}
