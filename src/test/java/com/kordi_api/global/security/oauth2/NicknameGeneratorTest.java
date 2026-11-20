package com.kordi_api.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kordi_api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

class NicknameGeneratorTest {

  @Test
  void 허용되지_않는_문자는_빼고_길이를_맞춘다() {
    assertThat(NicknameGenerator.sanitize("John Kim")).isEqualTo("JohnKim");
    assertThat(NicknameGenerator.sanitize("  ")).isEqualTo("kordi");
    assertThat(NicknameGenerator.sanitize(null)).isEqualTo("kordi");
    assertThat(NicknameGenerator.sanitize("가나다라마바사아자차카타파하가나다라마바사")).hasSize(20);
  }

  @Test
  void 이미_있는_이름이면_숫자를_붙인다() {
    UserRepository users = mock(UserRepository.class);
    given(users.existsByNickname("JohnKim")).willReturn(true);
    given(users.existsByNickname(anyString()))
        .willAnswer(inv -> inv.getArgument(0).equals("JohnKim"));
    String nickname = new NicknameGenerator(users).uniqueFrom("John Kim");
    assertThat(nickname).startsWith("JohnKim_").hasSizeLessThanOrEqualTo(20);
  }
}
