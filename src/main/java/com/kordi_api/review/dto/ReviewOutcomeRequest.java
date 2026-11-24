package com.kordi_api.review.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 복습 모드에서 코드 하나를 친 결과.
 *
 * @param chordId quiz_chords의 id
 * @param correct 맞혔는지
 */
public record ReviewOutcomeRequest(@NotNull Long chordId, boolean correct) {}
