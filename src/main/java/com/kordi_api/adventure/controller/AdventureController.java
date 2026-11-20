package com.kordi_api.adventure.controller;

import com.kordi_api.adventure.dto.AdventureRankingResponse;
import com.kordi_api.adventure.dto.AdventureRecordRequest;
import com.kordi_api.adventure.dto.AdventureResult;
import com.kordi_api.adventure.service.AdventureRecordService;
import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.LoginUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

/** 코드 모험: 기록 제출(회원)과 역대 랭킹(공개). */
@RestController
@RequestMapping("/api/adventure")
@RequiredArgsConstructor
public class AdventureController {

  private final AdventureRecordService adventureRecordService;

  @PostMapping("/records")
  public ApiResponse<AdventureResult> submit(
      @LoginUser Long userId, @RequestBody AdventureRecordRequest request) {
    try {
      return ApiResponse.success(adventureRecordService.submit(userId, request));
    } catch (DataIntegrityViolationException e) {
      // 같은 유저의 동시 첫 제출: 상대가 먼저 넣었으니 이번엔 잠금·갱신 경로로 간다 (트랜잭션 밖에서 다시 호출)
      return ApiResponse.success(adventureRecordService.submit(userId, request));
    }
  }

  @GetMapping("/rankings")
  public ApiResponse<List<AdventureRankingResponse>> ranking() {
    return ApiResponse.success(adventureRecordService.ranking());
  }
}
