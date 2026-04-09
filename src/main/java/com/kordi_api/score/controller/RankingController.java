package com.kordi_api.score.controller;

import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.dto.RankingResponse;
import com.kordi_api.score.service.RankingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

  private final RankingService rankingService;

  @GetMapping
  public ApiResponse<List<RankingResponse>> getRankings(@RequestParam Difficulty difficulty) {
    return ApiResponse.success(rankingService.getRankings(difficulty));
  }
}
