package com.kordi_api.review.controller;

import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.LoginUser;
import com.kordi_api.review.dto.ReviewDeckResponse;
import com.kordi_api.review.dto.ReviewResultRequest;
import com.kordi_api.review.dto.ReviewResultResponse;
import com.kordi_api.review.service.ChordReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 오늘의 복습: 퀴즈에서 약했던 코드를 다시 친다. 회원 전용. */
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

  private final ChordReviewService chordReviewService;

  /**
   * @param limit 받을 카드 수 (1~{@value ChordReviewService#MAX_DECK_SIZE})
   */
  @GetMapping("/deck")
  public ApiResponse<ReviewDeckResponse> deck(
      @LoginUser Long userId,
      @RequestParam(defaultValue = "" + ChordReviewService.DEFAULT_DECK_SIZE) int limit) {
    return ApiResponse.success(chordReviewService.deck(userId, limit));
  }

  @PostMapping("/results")
  public ApiResponse<ReviewResultResponse> results(
      @LoginUser Long userId, @Valid @RequestBody ReviewResultRequest request) {
    return ApiResponse.success(chordReviewService.recordPractice(userId, request.results()));
  }
}
