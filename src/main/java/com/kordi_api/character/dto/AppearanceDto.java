package com.kordi_api.character.dto;

import jakarta.validation.constraints.NotBlank;

/** 캐릭터 모습. hat·accessory는 없으면 null. */
public record AppearanceDto(
    @NotBlank String body, String hat, @NotBlank String face, String accessory) {}
