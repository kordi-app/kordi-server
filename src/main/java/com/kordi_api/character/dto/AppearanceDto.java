package com.kordi_api.character.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;

/** 캐릭터 모습. hat·accessory·pet은 없으면 null. */
public record AppearanceDto(
    @NotBlank String body,
    @Nullable String hat,
    @NotBlank String face,
    @Nullable String accessory,
    @Nullable String pet) {}
