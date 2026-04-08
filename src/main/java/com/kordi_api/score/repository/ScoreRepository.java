package com.kordi_api.score.repository;

import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.entity.Score;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreRepository extends JpaRepository<Score, Long> {

  List<Score> findByUserIdOrderByCreatedAtDesc(Long userId);

  List<Score> findByUserIdAndDifficultyOrderByTotalScoreDesc(Long userId, Difficulty difficulty);
}
