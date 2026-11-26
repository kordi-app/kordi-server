package com.kordi_api.adventure.entity;

import com.kordi_api.global.common.BaseTimeEntity;
import com.kordi_api.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 유저의 코드 모험 최고 기록 (유저당 한 줄, 점수가 더 높으면 갱신). */
@Entity
@Table(
    name = "adventure_records",
    uniqueConstraints = @UniqueConstraint(columnNames = "user_id"),
    indexes = @Index(name = "idx_adventure_records_rank", columnList = "score DESC, achieved_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdventureRecord extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /** 점수 (랭킹 기준). 점수제 이전 기록은 0이라 랭킹에 나오지 않는다 */
  @Column(nullable = false)
  private int score;

  /** 물리친 몬스터 수 */
  @Column(nullable = false)
  private int defeated;

  /** 그 판의 플레이 시간 */
  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  /** 이 기록을 낸 시각 (동률이면 먼저 낸 사람이 위) */
  @Column(name = "achieved_at", nullable = false)
  private Instant achievedAt;

  public static AdventureRecord first(
      User user, int score, int defeated, long durationMs, Instant at) {
    AdventureRecord record = new AdventureRecord();
    record.user = user;
    record.score = score;
    record.defeated = defeated;
    record.durationMs = durationMs;
    record.achievedAt = at;
    return record;
  }

  /** 점수가 더 높으면 갱신하고 true (같으면 먼저 낸 기록 유지). */
  public boolean improve(int score, int defeated, long durationMs, Instant at) {
    if (score <= this.score) {
      return false;
    }
    this.score = score;
    this.defeated = defeated;
    this.durationMs = durationMs;
    this.achievedAt = at;
    return true;
  }
}
