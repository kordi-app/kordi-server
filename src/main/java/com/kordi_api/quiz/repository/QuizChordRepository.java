package com.kordi_api.quiz.repository;

import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.quiz.entity.QuizChord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizChordRepository extends JpaRepository<QuizChord, Long> {

  List<QuizChord> findByDifficulty(Difficulty difficulty);

  @Query(
      value = "SELECT * FROM quiz_chords WHERE difficulty = :difficulty ORDER BY RAND() LIMIT 10",
      nativeQuery = true)
  List<QuizChord> findRandomByDifficulty(@Param("difficulty") String difficulty);
}
