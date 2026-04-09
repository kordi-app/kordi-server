package com.kordi_api.score.entity;

import com.kordi_api.global.common.BaseTimeEntity;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "scores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Score extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Difficulty difficulty;

  @Column(nullable = false)
  private int totalScore;

  @Column(nullable = false)
  private int correctCount;

  @Column(nullable = false)
  private int totalCount;

  @Builder
  public Score(User user, Difficulty difficulty, int totalScore, int correctCount, int totalCount) {
    this.user = user;
    this.difficulty = difficulty;
    this.totalScore = totalScore;
    this.correctCount = correctCount;
    this.totalCount = totalCount;
  }
}
