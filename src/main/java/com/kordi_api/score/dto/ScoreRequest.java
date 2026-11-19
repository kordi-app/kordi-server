package com.kordi_api.score.dto;

import static com.kordi_api.quiz.QuizRules.MAX_TOTAL_SCORE;
import static com.kordi_api.quiz.QuizRules.QUESTIONS_PER_QUIZ;

import com.kordi_api.quiz.entity.Difficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ScoreRequest(
    @NotNull Difficulty difficulty,
    @Min(0) @Max(MAX_TOTAL_SCORE) int totalScore,
    @Min(0) @Max(QUESTIONS_PER_QUIZ) int correctCount,
    @Min(1) @Max(QUESTIONS_PER_QUIZ) int totalCount) {}
