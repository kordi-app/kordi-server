package com.kordi_api.score.dto;

import com.kordi_api.quiz.entity.Difficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ScoreRequest(
    @NotNull Difficulty difficulty,
    @Min(0) @Max(2000) int totalScore,
    @Min(0) @Max(10) int correctCount,
    @Min(1) @Max(10) int totalCount) {}
