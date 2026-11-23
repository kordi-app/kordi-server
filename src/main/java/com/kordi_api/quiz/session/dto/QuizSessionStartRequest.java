package com.kordi_api.quiz.session.dto;

import com.kordi_api.quiz.entity.Difficulty;
import jakarta.validation.constraints.NotNull;

public record QuizSessionStartRequest(@NotNull Difficulty difficulty) {}
