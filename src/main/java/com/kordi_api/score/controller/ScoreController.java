package com.kordi_api.score.controller;

import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.LoginUser;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.dto.ScoreResponse;
import com.kordi_api.score.service.ScoreService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scores")
@RequiredArgsConstructor
public class ScoreController {

  private final ScoreService scoreService;

  @GetMapping("/me")
  public ApiResponse<List<ScoreResponse>> getMyScores(@LoginUser Long userId) {

    return ApiResponse.success(scoreService.getMyScores(userId));
  }

  @GetMapping("/me/{difficulty}")
  public ApiResponse<List<ScoreResponse>> getMyScoresByDifficulty(
      @LoginUser Long userId, @PathVariable Difficulty difficulty) {
    return ApiResponse.success(scoreService.getMyScoresByDifficulty(userId, difficulty));
  }
}
