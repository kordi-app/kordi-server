package com.kordi_api.score.repository;

import static com.kordi_api.score.entity.QQuizBestScore.quizBestScore;
import static com.kordi_api.user.entity.QUser.user;

import com.kordi_api.friendship.repository.FriendScope;
import com.kordi_api.global.common.Querydsl4RepositorySupport;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.entity.QuizBestScore;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 난이도별 퀴즈 랭킹 (전체 또는 나와 친구만). 점수 내림차순, 동점이면 먼저 낸 기록이 위 (idx_quiz_best_scores_rank). */
@Repository
public class QuizRankingQueryRepository extends Querydsl4RepositorySupport<QuizBestScore> {

  public QuizRankingQueryRepository() {
    super(QuizBestScore.class);
  }

  /**
   * @param friendsOf 이 유저와 친구만 (null이면 전체)
   */
  public List<QuizBestScore> findRanking(Difficulty difficulty, Long friendsOf, int limit) {
    return selectFrom(quizBestScore)
        .join(quizBestScore.user, user)
        .fetchJoin()
        .where(
            quizBestScore.difficulty.eq(difficulty),
            friendsOf == null ? null : FriendScope.meOrFriends(user.id, friendsOf))
        .orderBy(quizBestScore.totalScore.desc(), quizBestScore.achievedAt.asc())
        .limit(limit)
        .fetch();
  }
}
