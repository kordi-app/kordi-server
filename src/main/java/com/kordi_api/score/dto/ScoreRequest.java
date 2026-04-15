package com.kordi_api.score.dto;

import com.kordi_api.quiz.entity.Difficulty;
import jakarta.validation.constraints.NotNull;

public record ScoreRequest(
    @NotNull Difficulty difficulty, int totalScore, int correctCount, int totalCount) {}
