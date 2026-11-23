package com.kordi_api.score.entity;

import com.kordi_api.global.common.BaseTimeEntity;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.quiz.QuizRules;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
// ranking: per difficulty by score, and the "no better score of the same user" subquery
@Table(
    name = "scores",
    indexes = {
      @Index(name = "idx_scores_difficulty_total", columnList = "difficulty, total_score"),
      @Index(
          name = "idx_scores_user_difficulty_total",
          columnList = "user_id, difficulty, total_score")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Score extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  // text, not a MySQL ENUM (see data.sql)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "varchar(10)")
  private Difficulty difficulty;

  @Column(nullable = false)
  private int totalScore;

  @Column(nullable = false)
  private int correctCount;

  @Column(nullable = false)
  private int totalCount;

  @Builder(access = AccessLevel.PRIVATE)
  private Score(
      User user, Difficulty difficulty, int totalScore, int correctCount, int totalCount) {
    this.user = user;
    this.difficulty = difficulty;
    this.totalScore = totalScore;
    this.correctCount = correctCount;
    this.totalCount = totalCount;
  }

  /**
   * 퀴즈 결과를 기록한다. 점수는 클라이언트가 계산하므로 게임 규칙상 불가능한 값만 걸러낸다.
   *
   * <p>한계: 규칙 범위 안의 조작된 점수는 막지 못한다. 완전히 막으려면 서버가 퀴즈 세션과 정답 판정을 직접 가져야 한다.
   */
  public static Score record(
      User user, Difficulty difficulty, int totalScore, int correctCount, int totalCount) {
    if (correctCount > totalCount || totalScore > correctCount * QuizRules.MAX_SCORE_PER_QUESTION) {
      throw new CoreException(CoreExceptionCode.INVALID_REQUEST);
    }
    return Score.builder()
        .user(user)
        .difficulty(difficulty)
        .totalScore(totalScore)
        .correctCount(correctCount)
        .totalCount(totalCount)
        .build();
  }
}
