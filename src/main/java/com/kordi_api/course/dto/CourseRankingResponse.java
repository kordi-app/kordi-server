package com.kordi_api.course.dto;

public record CourseRankingResponse(
    int rank, String nickname, String profileImageUrl, long timeMs) {}
