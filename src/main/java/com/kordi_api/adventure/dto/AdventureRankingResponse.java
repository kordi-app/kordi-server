package com.kordi_api.adventure.dto;

import jakarta.annotation.Nullable;

public record AdventureRankingResponse(
    int rank, String nickname, @Nullable String profileImageUrl, int defeated) {}
