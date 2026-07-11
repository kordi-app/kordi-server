package com.kordi_api.global.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

  @GetMapping("/health-check")
  public ApiResponse<Void> healthCheck() {
    return ApiResponse.success();
  }
}
