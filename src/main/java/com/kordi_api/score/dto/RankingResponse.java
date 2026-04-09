package com.kordi_api.score.dto;

public record RankingResponse(
    String nickname, String profileImageUrl, int totalScore, int correctCount, int totalCount) {}
