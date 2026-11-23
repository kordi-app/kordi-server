package com.kordi_api.score.dto;

import jakarta.annotation.Nullable;

public record RankingResponse(
    String nickname,
    @Nullable String profileImageUrl,
    int totalScore,
    int correctCount,
    int totalCount) {}
