package com.kordi_api.score.repository;

import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.entity.Score;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoreRepository extends JpaRepository<Score, Long> {

  List<Score> findByUserIdOrderByCreatedAtDesc(Long userId);

  List<Score> findByUserIdAndDifficultyOrderByTotalScoreDesc(Long userId, Difficulty difficulty);

  @Query(
      """
          SELECT s FROM Score s
          WHERE s.difficulty = :difficulty AND
          s.totalScore = (SELECT MAX(s2.totalScore) FROM Score s2 WHERE s2.user = s.user AND s2.difficulty = :difficulty)
          ORDER BY s.totalScore DESC
          """)
  List<Score> findTopScoresByDifficulty(
      @Param("difficulty") Difficulty difficulty, Pageable pageable);
}
