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

  private static final QScore subScore = new QScore("subScore");

  public ScoreQueryRepository() {
    super(Score.class);
  }

  public List<Score> findTopScoresByDifficulty(Difficulty difficulty, int limit) {
    return selectFrom(score)
        .where(
            score.difficulty.eq(difficulty),
            score.totalScore.eq(
                JPAExpressions.select(subScore.totalScore.max())
                    .from(subScore)
                    .where(subScore.user.eq(score.user), subScore.difficulty.eq(difficulty))))
        .orderBy(score.totalScore.desc())
        .limit(limit)
        .fetch();
  }
}
