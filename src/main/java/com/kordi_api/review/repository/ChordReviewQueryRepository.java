package com.kordi_api.review.repository;

import static com.kordi_api.quiz.entity.QQuizChord.quizChord;
import static com.kordi_api.review.entity.QChordReview.chordReview;

import com.kordi_api.global.common.Querydsl4RepositorySupport;
import com.kordi_api.review.entity.ChordReview;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 오늘의 복습 카드 뽑기. 복습할 때가 된 카드와, 모자라면 채울 자주 틀린 카드를 같은 쿼리 모양에 조건만 바꿔 뽑는다. */
@Repository
public class ChordReviewQueryRepository extends Querydsl4RepositorySupport<ChordReview> {

  public ChordReviewQueryRepository() {
    super(ChordReview.class);
  }

  /** 복습할 때가 된 카드: 낮은 상자(약한 코드)부터, 같은 상자면 오래 밀린 것부터, 그다음 많이 틀린 것부터. */
  public List<ChordReview> findDue(Long userId, Instant now, int limit) {
    return deck(
        userId,
        chordReview.dueAt.loe(now),
        null,
        limit,
        chordReview.box.asc(),
        chordReview.dueAt.asc(),
        chordReview.wrongCount.desc(),
        chordReview.id.asc());
  }

  /**
   * 아직 때가 안 됐지만 틀린 적 있는 카드 (복습할 카드가 모자랄 때 채움): 틀린 횟수에서 맞힌 횟수를 뺀 값이 큰 것부터, 그다음 많이 틀린 것부터.
   *
   * @param exclude 이미 뽑은 카드 id (비어 있으면 무시)
   */
  public List<ChordReview> findMostMissed(
      Long userId, Instant now, Collection<Long> exclude, int limit) {
    return deck(
        userId,
        chordReview.dueAt.gt(now).and(chordReview.wrongCount.gt(0)),
        exclude.isEmpty() ? null : chordReview.id.notIn(exclude),
        limit,
        chordReview.wrongCount.subtract(chordReview.correctCount).desc(),
        chordReview.wrongCount.desc(),
        chordReview.lastSeenAt.desc(),
        chordReview.id.asc());
  }

  private List<ChordReview> deck(
      Long userId,
      BooleanExpression when,
      BooleanExpression exclude,
      int limit,
      OrderSpecifier<?>... order) {
    if (limit <= 0) {
      return List.of();
    }
    return selectFrom(chordReview)
        .join(chordReview.chord, quizChord)
        .fetchJoin()
        .where(chordReview.user.id.eq(userId), when, exclude)
        .orderBy(order)
        .limit(limit)
        .fetch();
  }
}
