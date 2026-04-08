package com.kordi_api.score.dto;

import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.entity.Score;
import java.time.LocalDateTime;

public record ScoreResponse(
    Long id,
    Difficulty difficulty,
    int totalScore,
    int correctCount,
    int totalCount,
    LocalDateTime createdAt) {
  public static ScoreResponse from(Score score) {
    return new ScoreResponse(
        score.getId(),
        score.getDifficulty(),
        score.getTotalScore(),
        score.getCorrectCount(),
        score.getTotalCount(),
        score.getCreatedAt());
  }
}
