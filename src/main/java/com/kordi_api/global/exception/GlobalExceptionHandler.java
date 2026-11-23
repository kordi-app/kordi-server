package com.kordi_api.global.exception;

import com.kordi_api.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
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

  private static final Set<String> MISSING_VALUE_CONSTRAINTS =
      Set.of("NotNull", "NotBlank", "NotEmpty");

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
    // 1001 only when every failure is a missing value; @Max, @Pattern and the like are 1003
    boolean onlyMissing =
        !e.getBindingResult().getFieldErrors().isEmpty()
            && e.getBindingResult().getFieldErrors().stream()
                .allMatch(error -> MISSING_VALUE_CONSTRAINTS.contains(error.getCode()));
    CoreExceptionCode code =
        onlyMissing ? CoreExceptionCode.REQUIRED_VALUE_MISSING : CoreExceptionCode.INVALID_REQUEST;
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

  /** 동시 수정 충돌 (낙관적 잠금) */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(HttpServletRequest request) {
    return toResponse(CoreExceptionCode.DATA_CONFLICT, request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDenied(HttpServletRequest request) {
    return toResponse(CoreExceptionCode.FORBIDDEN, request);
  }

  /**
   * 그 밖의 Spring MVC 예외는 500이 아니라 자기 상태 코드로. 415·406 등은 {@code ErrorResponseException}이 아니라 {@code
   * ServletException implements ErrorResponse}라서 클래스로는 한 번에 잡을 수 없어, 마지막 핸들러에서 인터페이스로 가른다.
   */
  private ResponseEntity<ApiResponse<Void>> handleErrorResponse(
      ErrorResponse e, HttpServletRequest request) {
    int status = e.getStatusCode().value();
    CoreExceptionCode code =
        status == 404
            ? CoreExceptionCode.NOT_FOUND
            : status == 405
                ? CoreExceptionCode.METHOD_NOT_ALLOWED
                : CoreExceptionCode.INVALID_REQUEST;
    return ResponseEntity.status(e.getStatusCode())
        .body(ApiResponse.error(code.getCode(), code.getMessage(), request.getRequestURI()));
  }

  /** 예상하지 못한 예외: 내부 메시지는 로그에만 남기고 클라이언트에는 고정 메시지를 내려준다. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(
      Exception e, HttpServletRequest request) {
    if (e instanceof ErrorResponse errorResponse) {
      return handleErrorResponse(errorResponse, request);
    }
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
