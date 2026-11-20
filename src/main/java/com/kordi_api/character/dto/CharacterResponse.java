package com.kordi_api.character.dto;

import java.util.List;

/**
 * @param appearance 지금 모습
 * @param unlocked 점프 코스 보상으로 열린 아이템 (예: crown, star-crown)
 */
public record CharacterResponse(AppearanceDto appearance, List<String> unlocked) {}
