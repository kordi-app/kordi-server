package com.kordi_api.review.dto;

import java.util.List;

/**
 * 오늘의 복습.
 *
 * @param dueCount 지금 복습할 때가 된 카드 전체 수 (cards는 이 중 limit개 + 모자라면 자주 틀린 카드)
 * @param cards 낼 순서대로
 */
public record ReviewDeckResponse(long dueCount, List<ReviewCardResponse> cards) {}
