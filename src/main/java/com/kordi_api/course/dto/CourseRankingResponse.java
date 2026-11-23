package com.kordi_api.course.dto;

import jakarta.annotation.Nullable;

public record CourseRankingResponse(
    int rank, String nickname, @Nullable String profileImageUrl, long timeMs) {}
