package com.kordi_api.score.dto;

import com.kordi_api.quiz.entity.Difficulty;

public record ScoreRequest(
    Difficulty difficulty, int totalScore, int correctCount, int totalCount) {}
