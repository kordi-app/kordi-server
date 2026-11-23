package com.kordi_api.quiz.judge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 판정이 클라이언트 판정과 같은지: 클라이언트 {@code scripts/gen-chord-fixtures.mjs}가 TS {@code validateChord}로 만든
 * 픽스처의 모든 경우를 {@link ChordValidator}로 다시 판정해 결과가 하나도 다르지 않아야 한다.
 */
class ChordValidatorContractTest {

  @Test
  void 클라이언트_판정과_모든_경우에_같다() throws Exception {
    JsonNode fixture;
    try (InputStream in = getClass().getResourceAsStream("/chord-validator-fixtures.json")) {
      assertThat(in).as("chord-validator-fixtures.json on the test classpath").isNotNull();
      fixture = JsonMapper.builder().build().readTree(in);
    }
    JsonNode cases = fixture.get("cases");
    assertThat(cases.size()).isEqualTo(fixture.get("count").asInt()).isGreaterThan(10_000);

    List<String> mismatches = new ArrayList<>();
    int valid = 0;
    for (JsonNode c : cases) {
      String name = c.get(0).asString();
      List<Integer> notes = new ArrayList<>();
      c.get(1).forEach(n -> notes.add(n.asInt()));
      boolean expected = c.get(2).asBoolean();
      boolean actual = ChordValidator.validate(name, notes);
      if (actual != expected) {
        mismatches.add(name + " " + notes + " expected " + expected);
      }
      if (expected) {
        valid++;
      }
    }
    assertThat(mismatches).isEmpty();
    assertThat(valid).isEqualTo(fixture.get("valid").asInt());
  }

  @Test
  void 자리바꿈과_옥타브는_상관없고_다른_코드는_아니다() {
    assertThat(ChordValidator.validate("C", List.of(60, 64, 67))).isTrue();
    assertThat(ChordValidator.validate("C", List.of(64, 67, 72))).isTrue();
    assertThat(ChordValidator.validate("C", List.of(60, 63, 67))).isFalse();
    assertThat(ChordValidator.validate("C", List.of())).isFalse();
    // 7th chord may drop its 5th and add a 9th; Cmaj9 itself is a rival of Cmaj7 (strict)
    assertThat(ChordValidator.validate("Cmaj7", List.of(48, 64, 71))).isTrue();
    assertThat(ChordValidator.validate("Cmaj7", List.of(48, 64, 67, 71, 74))).isFalse();
    assertThat(ChordValidator.validate("A#69", List.of(58, 62, 65, 67, 72))).isTrue();
  }
}
