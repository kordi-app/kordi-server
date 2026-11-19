package com.kordi_api.score.repository;

import static com.kordi_api.score.entity.QScore.score;

import com.kordi_api.global.common.Querydsl4RepositorySupport;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.entity.QScore;
import com.kordi_api.score.entity.Score;
import com.querydsl.jpa.JPAExpressions;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class ScoreQueryRepository extends Querydsl4RepositorySupport<Score> {

  private static final QScore better = new QScore("better");

  public ScoreQueryRepository() {
    super(Score.class);
  }

  /**
   * 난이도별 랭킹: 유저마다 대표 기록 1건(최고점, 동점이면 먼저 달성한 기록)만 뽑아 점수 내림차순으로 정렬한다.
   *
   * <p>"같은 유저의 더 나은 기록이 존재하지 않는" 행만 남기므로, 같은 최고점을 여러 번 기록해도 한 줄만 나온다.
   */
  public List<Score> findTopScoresByDifficulty(Difficulty difficulty, int limit) {
    return selectFrom(score)
        .join(score.user)
        .fetchJoin()
        .where(
            score.difficulty.eq(difficulty),
            JPAExpressions.selectOne()
                .from(better)
                .where(
                    better.user.eq(score.user),
                    better.difficulty.eq(difficulty),
                    better
                        .totalScore
                        .gt(score.totalScore)
                        .or(better.totalScore.eq(score.totalScore).and(better.id.lt(score.id))))
                .notExists())
        .orderBy(score.totalScore.desc(), score.createdAt.asc())
        .limit(limit)
        .fetch();
  }
}
