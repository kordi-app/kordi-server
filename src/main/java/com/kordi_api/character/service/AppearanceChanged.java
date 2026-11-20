package com.kordi_api.character.service;

import com.kordi_api.character.dto.AppearanceDto;

/** 유저의 캐릭터 모습이 바뀌었다 (꾸미기 저장, 보상 자동 장착). 월드가 접속 중인 그 유저의 모습을 갱신한다. */
public record AppearanceChanged(Long userId, AppearanceDto appearance) {}
