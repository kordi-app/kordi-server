package com.kordi_api.adventure.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.support.Fixtures;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdventureRecordTest {

  private final Instant at = Instant.parse("2026-11-20T12:00:00Z");

  @Test
  void 더_많이_물리친_기록만_갱신한다() {
    AdventureRecord record = AdventureRecord.first(Fixtures.user(1), 20, 60_000, at);

    assertThat(record.improve(15, 50_000, at.plus(Duration.ofMinutes(1)))).isFalse();
    assertThat(record.getDefeated()).isEqualTo(20);

    assertThat(record.improve(25, 80_000, at.plus(Duration.ofMinutes(2)))).isTrue();
    assertThat(record.getDefeated()).isEqualTo(25);
    assertThat(record.getDurationMs()).isEqualTo(80_000);
    assertThat(record.getAchievedAt()).isEqualTo(at.plus(Duration.ofMinutes(2)));
  }

  @Test
  void 같은_기록이면_먼저_낸_기록을_유지한다() {
    AdventureRecord record = AdventureRecord.first(Fixtures.user(1), 20, 60_000, at);

    assertThat(record.improve(20, 40_000, at.plus(Duration.ofMinutes(1)))).isFalse();
    assertThat(record.getDurationMs()).isEqualTo(60_000);
    assertThat(record.getAchievedAt()).isEqualTo(at);
  }
}
