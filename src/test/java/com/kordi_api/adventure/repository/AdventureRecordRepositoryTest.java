package com.kordi_api.adventure.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.adventure.entity.AdventureRecord;
import com.kordi_api.support.IntegrationTest;
import com.kordi_api.user.entity.OAuthProvider;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;

/** 점수제 이전 기록(0점)은 랭킹 목록에도, 순위 계산에도 들어가지 않는다. */
@IntegrationTest
@Transactional
class AdventureRecordRepositoryTest {

  @Autowired AdventureRecordRepository adventureRecordRepository;
  @Autowired UserRepository userRepository;

  @Test
  void 점수제_이전_기록은_순위에서_빠진다() {
    String tag = Long.toString(System.nanoTime(), 36);
    User legacy = newUser("legacy-" + tag);
    User me = newUser("me-" + tag);
    Instant past = Instant.parse("2026-11-01T00:00:00Z");
    Instant now = Instant.parse("2026-11-26T00:00:00Z");

    long before = adventureRecordRepository.countAhead(me.getId(), 0, now);
    adventureRecordRepository.saveAndFlush(AdventureRecord.first(legacy, 0, 30, 100_000, past));

    // a 0-point run isn't put behind a legacy record that the board doesn't show
    assertThat(adventureRecordRepository.countAhead(me.getId(), 0, now)).isEqualTo(before);
    assertThat(
            adventureRecordRepository.findAllByScoreGreaterThanOrderByScoreDescAchievedAtAsc(
                0, Limit.unlimited()))
        .noneMatch(r -> r.getUser().getId().equals(legacy.getId()));
  }

  private User newUser(String nickname) {
    return userRepository.saveAndFlush(
        User.builder()
            .email(nickname + "@example.com")
            .nickname(nickname)
            .oauthProvider(OAuthProvider.GOOGLE)
            .oauthId(nickname)
            .build());
  }
}
