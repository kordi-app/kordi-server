package com.kordi_api.score.controller;

import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.CustomUserDetails;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.dto.ScoreRequest;
import com.kordi_api.score.dto.ScoreResponse;
import com.kordi_api.score.service.ScoreService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scores")
@RequiredArgsConstructor
public class ScoreController {

  private final ScoreService scoreService;

  @PostMapping
  public ApiResponse<ScoreResponse> saveScore(
      @AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody ScoreRequest request) {
    return ApiResponse.success(scoreService.saveScore(userDetails.getUser().getId(), request));
  }

  @GetMapping("/me")
  public ApiResponse<List<ScoreResponse>> getMyScores(
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    return ApiResponse.success(scoreService.getMyScores(userDetails.getUser().getId()));
  }

  @GetMapping("/me/{difficulty}")
  public ApiResponse<List<ScoreResponse>> getMyScoresByDifficulty(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Difficulty difficulty) {
    return ApiResponse.success(
        scoreService.getMyScoresByDifficulty(userDetails.getUser().getId(), difficulty));
  }
}
