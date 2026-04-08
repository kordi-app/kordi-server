package com.kordi_api.quiz.controller;

import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.quiz.dto.QuizChordResponse;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.quiz.service.QuizService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

  private final QuizService quizService;

  @GetMapping("/chords")
  public ApiResponse<List<QuizChordResponse>> getQuizChords(@RequestParam Difficulty difficulty) {
    return ApiResponse.success(quizService.getQuizChords(difficulty));
  }
}
