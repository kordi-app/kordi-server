package com.kordi_api.score.controller;

import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.LoginUser;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.dto.ScoreRequest;
import com.kordi_api.score.dto.ScoreResponse;
import com.kordi_api.score.service.ScoreService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scores")
@RequiredArgsConstructor
public class ScoreController {

  private final ScoreService scoreService;

  /**
   * 클라이언트가 계산한 점수를 그대로 받던 예전 저장 경로. 이제 "내 기록"에만 남고 최고 기록·랭킹에는 반영되지 않는다 (랭킹은 서버가 채점하는 {@code POST
   * /api/quiz/sessions/{id}/result}만 반영).
   *
   * @deprecated 퀴즈 세션 흐름으로 바뀐 클라이언트가 배포되기 전까지 예전 클라이언트를 위해 남겨 둔다. 클라이언트 배포 후 이 엔드포인트와 {@link
   *     ScoreRequest}, {@link ScoreService#saveScore}를 지운다.
   */
  @Deprecated
  @PostMapping
  public ApiResponse<ScoreResponse> saveScore(
      @LoginUser Long userId, @Valid @RequestBody ScoreRequest request) {
    return ApiResponse.success(scoreService.saveScore(userId, request));
  }

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
