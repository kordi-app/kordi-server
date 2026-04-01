package com.kordi_api.global.exception;

import static com.kordi_api.global.common.ApiResponseCode.*;
import static com.kordi_api.global.exception.CoreExceptionCode.REQUIRED_VALUE_MISSING;

import com.kordi_api.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(CoreException.class)
  public ApiResponse<Void> handleCoreException(
      CoreException e, HttpServletRequest request, HttpServletResponse response) {
    int statusCode = e.getHttpStatus();
    int detailCode = e.getDetailCode();

    if (statusCode >= 500) {
      log.error("[CoreException) {} - {}", e.getCode(), e.getMessage(), e);
    }

    response.setStatus(statusCode);
    return ApiResponse.error(detailCode, e.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ApiResponse<Void> handleValidationException(
      MethodArgumentNotValidException e, HttpServletRequest request, HttpServletResponse response) {
    int statusCode = REQUIRED_VALUE_MISSING.getHttpStatus();
    int detailCode = REQUIRED_VALUE_MISSING.getDetailCode();

    BindingResult bindingResult = e.getBindingResult();
    Map<String, String> fieldErrors = new HashMap<>();

    if (bindingResult.hasFieldErrors()) {
      fieldErrors =
          bindingResult.getFieldErrors().stream()
              .collect(
                  Collectors.toMap(
                      FieldError::getField,
                      FieldError::getDefaultMessage,
                      (existing, replacement) -> existing));
    }

    response.setStatus(statusCode);
    return ApiResponse.error(
        detailCode,
        REQUIRED_VALUE_MISSING.getMessage() + " " + fieldErrors,
        request.getRequestURI());
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ApiResponse<Void> handleMissingParameter(
      MissingServletRequestParameterException e,
      HttpServletRequest request,
      HttpServletResponse response) {
    int statusCode = REQUIRED_VALUE_MISSING.getHttpStatus();
    int detailCode = REQUIRED_VALUE_MISSING.getDetailCode();

    String message = REQUIRED_VALUE_MISSING.getMessage();
    if (e.getParameterName() != null) {
      message = message + " - 필수 파라미터 누락: " + e.getParameterName();
    }

    response.setStatus(statusCode);
    return ApiResponse.error(detailCode, message, request.getRequestURI());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ApiResponse<Void> handleMessageNotReadable(
      HttpMessageNotReadableException e, HttpServletRequest request, HttpServletResponse response) {
    int statusCode = REQUIRED_VALUE_MISSING.getHttpStatus();
    int detailCode = REQUIRED_VALUE_MISSING.getDetailCode();

    response.setStatus(statusCode);
    return ApiResponse.error(
        detailCode,
        REQUIRED_VALUE_MISSING.getMessage() + " - 요청 본문(Request Body)이 누락되었거나 형식이 올바르지 않습니다.",
        request.getRequestURI());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ApiResponse<Void> handleMethodNotSupported(
      HttpServletRequest request, HttpServletResponse response) {
    response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
    return ApiResponse.error(
        METHOD_NOT_ALLOWED.getCode(), METHOD_NOT_ALLOWED.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler(Exception.class)
  public ApiResponse<Void> handleException(
      Exception e, HttpServletRequest request, HttpServletResponse response) {
    log.error("[Exception] {}", e.getMessage(), e);

    Throwable cause = e.getCause();
    int statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
    int detailCode = INTERNAL_SERVER_ERROR.getCode();

    if (cause instanceof ConstraintViolationException) {
      statusCode = HttpStatus.CONFLICT.value();
      detailCode = CONFLICT.getCode();
    }

    response.setStatus(statusCode);
    return ApiResponse.error(detailCode, e.getMessage(), request.getRequestURI());
  }
}
