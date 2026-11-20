package com.kordi_api.character.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kordi_api.character.dto.AppearanceDto;
import com.kordi_api.global.exception.CoreException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CharacterServiceTest {

  @Test
  void 목록에_있는_아이템이면_통과한다() {
    assertThatCode(
            () ->
                CharacterService.validate(
                    new AppearanceDto("mint", "beret", "smile", "scarf"), List.of()))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                CharacterService.validate(
                    new AppearanceDto("cream", null, "default", null), List.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void 목록에_없는_값은_거절한다() {
    assertThatThrownBy(
            () ->
                CharacterService.validate(
                    new AppearanceDto("gold", null, "default", null), List.of()))
        .isInstanceOf(CoreException.class);
    assertThatThrownBy(
            () ->
                CharacterService.validate(
                    new AppearanceDto("cream", "tophat", "default", null), List.of()))
        .isInstanceOf(CoreException.class);
  }

  @Test
  void 얻지_못한_보상_모자는_쓸_수_없다() {
    assertThatThrownBy(
            () ->
                CharacterService.validate(
                    new AppearanceDto("cream", "star-crown", "default", null), List.of("crown")))
        .isInstanceOf(CoreException.class);
    assertThatCode(
            () ->
                CharacterService.validate(
                    new AppearanceDto("cream", "crown", "default", null), List.of("crown")))
        .doesNotThrowAnyException();
  }
}
