package com.kordi_api.course.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.support.Fixtures;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CourseRecordTest {

  @Test
  void 더_빠른_기록만_갱신한다() {
    Instant at = Instant.parse("2026-11-20T12:00:00Z");
    CourseRecord record = CourseRecord.first(Fixtures.user(1), Course.CLOUDS, 40_000, at);

    assertThat(record.improve(45_000, at.plus(Duration.ofMinutes(1)))).isFalse();
    assertThat(record.getTimeMs()).isEqualTo(40_000);

    assertThat(record.improve(38_000, at.plus(Duration.ofMinutes(2)))).isTrue();
    assertThat(record.getTimeMs()).isEqualTo(38_000);
    assertThat(record.getAchievedAt()).isEqualTo(at.plus(Duration.ofMinutes(2)));
  }

  @Test
  void 지역_이름으로_코스를_찾는다() {
    assertThat(Course.ofRegion("night")).contains(Course.NIGHT);
    assertThat(Course.ofRegion("hall")).isEmpty();
  }
}
