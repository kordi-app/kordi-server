package com.kordi_api.world.warp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.support.IntegrationTest;
import com.kordi_api.user.entity.OAuthProvider;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 깨운 워프석이 계정에 남고, 다시 깨워도 겹치지 않으며, 없는 워프석은 받지 않는다. 실제 DB로. */
@IntegrationTest
class WarpStoneServiceTest {

  @Autowired WarpStoneService warpStoneService;
  @Autowired AwakeWarpStoneRepository repository;
  @Autowired UserRepository userRepository;

  @Test
  void 처음에는_광장_워프석만_깨어_있다() {
    User me = newUser();
    assertThat(warpStoneService.awake(me.getId())).containsExactly("plaza");
  }

  @Test
  void 깨운_워프석은_계정에_남는다() {
    User me = newUser();
    assertThat(warpStoneService.wake(me.getId(), List.of("drums")))
        .containsExactly("drums", "plaza");
    assertThat(warpStoneService.awake(me.getId())).containsExactly("drums", "plaza");
  }

  @Test
  void 같은_워프석을_다시_깨워도_한_줄이다() {
    User me = newUser();
    warpStoneService.wake(me.getId(), List.of("tower"));
    warpStoneService.wake(me.getId(), List.of("tower", "tower", "falls"));
    assertThat(repository.findStonesByUserId(me.getId()))
        .containsExactlyInAnyOrder("tower", "falls");
  }

  @Test
  void 광장_워프석은_저장하지_않는다() {
    User me = newUser();
    warpStoneService.wake(me.getId(), List.of("plaza", "pipes"));
    assertThat(repository.findStonesByUserId(me.getId())).containsExactly("pipes");
  }

  @Test
  void 유저마다_따로_남는다() {
    User me = newUser();
    User other = newUser();
    warpStoneService.wake(me.getId(), List.of("drums"));
    assertThat(warpStoneService.awake(other.getId())).containsExactly("plaza");
  }

  @Test
  void 없는_워프석이나_빈_목록은_받지_않는다() {
    User me = newUser();
    assertThatThrownBy(() -> warpStoneService.wake(me.getId(), List.of("moon")))
        .isInstanceOf(CoreException.class);
    assertThatThrownBy(() -> warpStoneService.wake(me.getId(), List.of()))
        .isInstanceOf(CoreException.class);
    assertThat(repository.findStonesByUserId(me.getId())).isEmpty();
  }

  private User newUser() {
    String tag = "warp-" + Long.toString(System.nanoTime(), 36);
    return userRepository.saveAndFlush(
        User.builder()
            .email(tag + "@example.com")
            .nickname(tag)
            .oauthProvider(OAuthProvider.GOOGLE)
            .oauthId(tag)
            .build());
  }
}
