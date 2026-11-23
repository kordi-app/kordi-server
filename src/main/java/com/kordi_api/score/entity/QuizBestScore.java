package com.kordi_api.score.entity;

import com.kordi_api.global.common.BaseTimeEntity;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유저의 난이도별 코드 퀴즈 최고 기록 (유저·난이도당 한 줄, 더 높은 점수가 나오면 갱신). 랭킹은 이 표만 읽는다 — 시도 하나하나는 {@link Score}(내 기록)에
 * 남는다.
 */
@Entity
@Table(
    name = "quiz_best_scores",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "difficulty"}),
    indexes =
        @Index(
            name = "idx_quiz_best_scores_rank",
            columnList = "difficulty, total_score DESC, achieved_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizBestScore extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  // text, not a MySQL ENUM (see db/migration/V1__init_schema.sql)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "varchar(10)")
  private Difficulty difficulty;

  @Column(name = "total_score", nullable = false)
  private int totalScore;

  @Column(name = "correct_count", nullable = false)
  private int correctCount;

  @Column(name = "total_count", nullable = false)
  private int totalCount;

  /** 이 기록을 낸 시각 (동점이면 먼저 낸 사람이 위) */
  @Column(name = "achieved_at", nullable = false)
  private Instant achievedAt;

  public static QuizBestScore first(
      User user,
      Difficulty difficulty,
      int totalScore,
      int correctCount,
      int totalCount,
      Instant at) {
    QuizBestScore best = new QuizBestScore();
    best.user = user;
    best.difficulty = difficulty;
    best.totalScore = totalScore;
    best.correctCount = correctCount;
    best.totalCount = totalCount;
    best.achievedAt = at;
    return best;
  }

  /** 점수가 더 높으면 갱신하고 true (같으면 먼저 낸 기록 유지). */
  public boolean improve(int totalScore, int correctCount, int totalCount, Instant at) {
    if (totalScore <= this.totalScore) {
      return false;
    }
    this.totalScore = totalScore;
    this.correctCount = correctCount;
    this.totalCount = totalCount;
    this.achievedAt = at;
    return true;
  }
}
