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
/** 코드 퀴즈 시도 기록 (한 판에 한 줄, "내 기록"). 랭킹은 이 표가 아니라 유저·난이도당 한 줄인 {@link QuizBestScore}를 읽는다. */
// my scores by difficulty, best first
@Table(
    name = "scores",
    indexes =
        @Index(
            name = "idx_scores_user_difficulty_total",
            columnList = "user_id, difficulty, total_score"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Score extends BaseTimeEntity {

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
   * 퀴즈 결과를 기록한다. 게임 규칙상 불가능한 값만 걸러낸다 — 점수 자체는 퀴즈 세션(서버 채점)이 계산한 값이다. 예전 클라이언트가 보내는 {@code POST
   * /api/scores}도 이 경로로 들어오지만 그 값은 기록에만 남고 랭킹에는 들어가지 않는다.
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
