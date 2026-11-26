package com.kordi_api.world.warp;

import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 내가 깨운 섬의 워프석 (로그인 필요). */
@RestController
@RequestMapping("/api/world/warp-stones")
@RequiredArgsConstructor
public class WarpStoneController {

  private final WarpStoneService warpStoneService;

  @GetMapping
  public ApiResponse<WarpStonesResponse> get(@LoginUser Long userId) {
    return ApiResponse.success(new WarpStonesResponse(warpStoneService.awake(userId)));
  }

  /** 깨운 워프석을 더한다 (이미 깨어 있는 것은 그대로) */
  @PutMapping
  public ApiResponse<WarpStonesResponse> wake(
      @LoginUser Long userId, @Valid @RequestBody WarpStonesRequest request) {
    return ApiResponse.success(
        new WarpStonesResponse(warpStoneService.wake(userId, request.stones())));
  }
}
