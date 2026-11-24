package com.kordi_api.score.controller;

import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.common.RankingScope;
import com.kordi_api.global.security.LoginUser;
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

  /**
   * @param scope ALL(기본, 공개) 또는 FRIENDS(나와 수락된 친구만, 로그인 필요)
   */
  @GetMapping
  public ApiResponse<List<RankingResponse>> getRankings(
      @LoginUser Long userId,
      @RequestParam Difficulty difficulty,
      @RequestParam(defaultValue = "ALL") RankingScope scope) {
    return ApiResponse.success(rankingService.getRankings(difficulty, scope.friendsOf(userId)));
  }
}
