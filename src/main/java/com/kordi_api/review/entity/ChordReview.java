package com.kordi_api.review.entity;

import com.kordi_api.global.common.BaseTimeEntity;
import com.kordi_api.quiz.entity.QuizChord;
import com.kordi_api.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유저가 한 코드를 얼마나 맞히고 틀렸는지와 다음 복습 일정 (유저·코드당 한 줄). 서버가 채점한 퀴즈 결과와 복습 모드의 결과로 갱신된다. 일정 규칙은 {@link
 * LeitnerSchedule}.
 */
@Entity
@Table(
    name = "chord_reviews",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_chord_reviews_user_chord",
            columnNames = {"user_id", "quiz_chord_id"}),
    indexes = @Index(name = "idx_chord_reviews_user_due", columnList = "user_id, due_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChordReview extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_chord_reviews_user"))
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "quiz_chord_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_chord_reviews_chord"))
  private QuizChord chord;

  @Column(name = "correct_count", nullable = false)
  private int correctCount;

  @Column(name = "wrong_count", nullable = false)
  private int wrongCount;

  /** 라이트너 상자 (1~5, 답한 적 없으면 0) */
  @Column(name = "leitner_box", nullable = false)
  private int box;

  /** 다음 복습 시각. 이 시각이 지나면 오늘의 복습에 나온다 */
  @Column(name = "due_at", nullable = false)
  private Instant dueAt;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  /** 아직 답하지 않은 새 카드. 바로 {@link #answer}로 첫 결과를 반영한다. */
  public static ChordReview start(User user, QuizChord chord, Instant now) {
    ChordReview review = new ChordReview();
    review.user = user;
    review.chord = chord;
    review.box = LeitnerSchedule.NEW_BOX;
    review.dueAt = now;
    review.lastSeenAt = now;
    return review;
  }

  /** 한 번 답한 결과를 반영해 횟수와 다음 복습 일정을 바꾼다. */
  public void answer(boolean correct, Instant at) {
    if (correct) {
      correctCount++;
    } else {
      wrongCount++;
    }
    box = LeitnerSchedule.nextBox(box, correct);
    dueAt = LeitnerSchedule.dueAt(box, at);
    lastSeenAt = at;
  }

  public boolean isDue(Instant now) {
    return !dueAt.isAfter(now);
  }
}
