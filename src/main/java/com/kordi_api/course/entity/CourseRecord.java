package com.kordi_api.course.entity;

import com.kordi_api.global.common.BaseTimeEntity;
import com.kordi_api.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 유저의 코스별 최고 기록 (유저·코스당 한 줄, 더 빠른 기록이 나오면 갱신). */
@Entity
@Table(
    name = "course_records",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "course"}),
    indexes = @Index(name = "idx_course_records_course_time", columnList = "course, time_ms"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseRecord extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  // text, not a MySQL ENUM: a new course must not need a schema change (see
  // db/migration/V1__init_schema.sql)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "varchar(20)")
  private Course course;

  @Column(name = "time_ms", nullable = false)
  private long timeMs;

  /** 이 기록을 낸 시각 (동률이면 먼저 낸 사람이 위) */
  @Column(nullable = false)
  private Instant achievedAt;

  public static CourseRecord first(User user, Course course, long timeMs, Instant at) {
    CourseRecord record = new CourseRecord();
    record.user = user;
    record.course = course;
    record.timeMs = timeMs;
    record.achievedAt = at;
    return record;
  }

  /** 더 빠르면 갱신하고 true. */
  public boolean improve(long timeMs, Instant at) {
    if (timeMs >= this.timeMs) {
      return false;
    }
    this.timeMs = timeMs;
    this.achievedAt = at;
    return true;
  }
}
