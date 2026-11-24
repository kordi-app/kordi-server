package com.kordi_api.world;

import com.kordi_api.character.dto.AppearanceDto;

/** 다른 사람에게 보이는 캐릭터 모습 (회원만; 비회원은 null = 기본 모습). */
public record WorldLook(String body, String hat, String face, String accessory, String pet) {

  public static WorldLook of(AppearanceDto a) {
    return new WorldLook(a.body(), a.hat(), a.face(), a.accessory(), a.pet());
  }
}
