package com.kordi_api.character.controller;

import com.kordi_api.character.dto.AppearanceDto;
import com.kordi_api.character.dto.CharacterResponse;
import com.kordi_api.character.service.CharacterService;
import com.kordi_api.global.common.ApiResponse;
import com.kordi_api.global.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 내 캐릭터 꾸미기 (로그인 필요). */
@RestController
@RequestMapping("/api/characters/me")
@RequiredArgsConstructor
public class CharacterController {

  private final CharacterService characterService;

  @GetMapping
  public ApiResponse<CharacterResponse> get(@LoginUser Long userId) {
    return ApiResponse.success(characterService.get(userId));
  }

  @PutMapping
  public ApiResponse<CharacterResponse> update(
      @LoginUser Long userId, @Valid @RequestBody AppearanceDto request) {
    return ApiResponse.success(characterService.update(userId, request));
  }
}
