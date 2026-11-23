package com.kordi_api.adventure.controller;

import com.kordi_api.adventure.dto.AdventureRankingResponse;
import com.kordi_api.adventure.dto.AdventureRecordRequest;
import com.kordi_api.adventure.dto.AdventureResult;
import com.kordi_api.adventure.dto.AdventureRunResponse;
import com.kordi_api.adventure.service.AdventureRecordService;
import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.LoginUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 코드 모험: 기록 제출(회원)과 역대 랭킹(공개). */
@RestController
@RequestMapping("/api/adventure")
@RequiredArgsConstructor
public class AdventureController {

  private final AdventureRecordService adventureRecordService;

  /** 판 시작: 결과를 낼 때 쓸 runId (판 길이는 서버가 이 시점부터 잰다). */
  @PostMapping("/runs")
  public ApiResponse<AdventureRunResponse> startRun(@LoginUser Long userId) {
    return ApiResponse.success(new AdventureRunResponse(adventureRecordService.startRun(userId)));
  }

  @PostMapping("/records")
  public ApiResponse<AdventureResult> submit(
      @LoginUser Long userId, @RequestBody AdventureRecordRequest request) {
    return ApiResponse.success(adventureRecordService.submit(userId, request));
  }

  @GetMapping("/rankings")
  public ApiResponse<List<AdventureRankingResponse>> ranking() {
    return ApiResponse.success(adventureRecordService.ranking());
  }
}
