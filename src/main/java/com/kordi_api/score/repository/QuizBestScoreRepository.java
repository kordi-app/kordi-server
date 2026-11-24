package com.kordi_api.score.repository;

import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.entity.QuizBestScore;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizBestScoreRepository extends JpaRepository<QuizBestScore, Long> {

  /** 기록 갱신용: 같은 유저의 동시 제출이 서로의 갱신을 덮어쓰지 않도록 행을 잠근다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from QuizBestScore b where b.user.id = :userId and b.difficulty = :difficulty")
  Optional<QuizBestScore> findForUpdate(
      @Param("userId") Long userId, @Param("difficulty") Difficulty difficulty);

  Optional<QuizBestScore> findByUserIdAndDifficulty(Long userId, Difficulty difficulty);

  /** 이 점수보다 위에 있는 다른 사람 수: 더 높거나, 같은데 먼저 냈다 (랭킹 목록과 같은 기준). +1 하면 순위 */
  @Query(
      """
      select count(b) from QuizBestScore b
      where b.difficulty = :difficulty and b.user.id <> :userId
        and (b.totalScore > :score or (b.totalScore = :score and b.achievedAt < :at))
      """)
  long countAhead(
      @Param("difficulty") Difficulty difficulty,
      @Param("userId") Long userId,
      @Param("score") int score,
      @Param("at") Instant at);
}
