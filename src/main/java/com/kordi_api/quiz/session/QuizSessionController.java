package com.kordi_api.quiz.session;

import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.LoginUser;
import com.kordi_api.quiz.session.dto.QuizResultRequest;
import com.kordi_api.quiz.session.dto.QuizResultResponse;
import com.kordi_api.quiz.session.dto.QuizSessionResponse;
import com.kordi_api.quiz.session.dto.QuizSessionStartRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 코드 퀴즈 판: 열기(문제 받기)와 결과 내기(서버 채점·기록). 회원 전용. */
@RestController
@RequestMapping("/api/quiz/sessions")
@RequiredArgsConstructor
public class QuizSessionController {

  private final QuizSessionService quizSessionService;

  @PostMapping
  public ApiResponse<QuizSessionResponse> start(
      @LoginUser Long userId, @Valid @RequestBody QuizSessionStartRequest request) {
    return ApiResponse.success(quizSessionService.start(userId, request.difficulty()));
  }

  @PostMapping("/{sessionId}/result")
  public ApiResponse<QuizResultResponse> submit(
      @LoginUser Long userId,
      @PathVariable String sessionId,
      @Valid @RequestBody QuizResultRequest request) {
    return ApiResponse.success(quizSessionService.submit(userId, sessionId, request));
  }
}
