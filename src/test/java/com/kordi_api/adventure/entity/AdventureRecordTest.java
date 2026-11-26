package com.kordi_api.adventure.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.support.Fixtures;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdventureRecordTest {

  private final Instant at = Instant.parse("2026-11-20T12:00:00Z");

  @Test
  void 점수가_더_높은_기록만_갱신한다() {
    AdventureRecord record = AdventureRecord.first(Fixtures.user(1), 5_000, 20, 60_000, at);

    // 더 많이 물리쳐도 점수가 낮으면 그대로
    assertThat(record.improve(4_000, 25, 50_000, at.plus(Duration.ofMinutes(1)))).isFalse();
    assertThat(record.getScore()).isEqualTo(5_000);
    assertThat(record.getDefeated()).isEqualTo(20);

    assertThat(record.improve(6_000, 18, 80_000, at.plus(Duration.ofMinutes(2)))).isTrue();
    assertThat(record.getScore()).isEqualTo(6_000);
    assertThat(record.getDefeated()).isEqualTo(18);
    assertThat(record.getDurationMs()).isEqualTo(80_000);
    assertThat(record.getAchievedAt()).isEqualTo(at.plus(Duration.ofMinutes(2)));
  }

  @Test
  void 같은_점수면_먼저_낸_기록을_유지한다() {
    AdventureRecord record = AdventureRecord.first(Fixtures.user(1), 5_000, 20, 60_000, at);

    assertThat(record.improve(5_000, 22, 40_000, at.plus(Duration.ofMinutes(1)))).isFalse();
    assertThat(record.getDurationMs()).isEqualTo(60_000);
    assertThat(record.getAchievedAt()).isEqualTo(at);
  }
}
