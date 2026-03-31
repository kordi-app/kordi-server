package com.kordi_api.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  private final Integer code;
  private final String message;
  private final String path;
  @JsonUnwrapped private final PageContent<?> page;
  @JsonUnwrapped private final T data;

  private ApiResponse(Integer code, String message, String path, PageContent<?> page, T data) {
    this.code = code;
    this.message = message;
    this.path = path;
    this.page = page;
    this.data = data;
  }

  // ApiResponseCode
  public static ApiResponse<Void> of(ApiResponseCode responseCode) {
    return new ApiResponse<>(responseCode.getCode(), responseCode.getMessage(), null, null, null);
  }

  public static <T> ApiResponse<T> of(ApiResponseCode responseCode, T data) {
    return new ApiResponse<>(responseCode.getCode(), responseCode.getMessage(), null, null, data);
  }

  // HttpStatus
  public static ApiResponse<Void> of(HttpStatus status) {
    return new ApiResponse<>(status.value(), status.name(), null, null, null);
  }

  // 성공
  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(null, null, null, null, data);
  }

  public static ApiResponse<Void> success() {
    return new ApiResponse<>(200, "OK", null, null, null);
  }

  // 페이징
  public static <T> ApiResponse<Void> successWithPage(T data, Long totalPage, Long totalCount) {
    PageContent<T> page =
        PageContent.<T>builder().totalPage(totalPage).totalCount(totalCount).contents(data).build();
    return new ApiResponse<>(null, null, null, page, null);
  }

  public static <T> ApiResponse<Void> successWithPage(
      T data, Long totalPage, Long totalCount, Long currentPage) {
    PageContent<T> page =
        PageContent.<T>builder()
            .totalPage(totalPage)
            .totalCount(totalCount)
            .currentPage(currentPage)
            .contents(data)
            .build();
    return new ApiResponse<>(null, null, null, page, null);
  }

  // 에러
  public static ApiResponse<Void> error(Integer code, String message) {
    return new ApiResponse<>(code, message, null, null, null);
  }

  public static ApiResponse<Void> error(Integer code, String message, String path) {
    return new ApiResponse<>(code, message, path, null, null);
  }
}
