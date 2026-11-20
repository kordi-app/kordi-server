package com.kordi_api.adventure.dto;

public record AdventureRankingResponse(
    int rank, String nickname, String profileImageUrl, int defeated) {}
