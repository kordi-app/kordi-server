package com.kordi_api.review.dto;

/**
 * @param recorded 반영한 결과 수
 * @param dueCount 반영 후 지금 복습할 때가 된 카드 수
 */
public record ReviewResultResponse(int recorded, long dueCount) {}
