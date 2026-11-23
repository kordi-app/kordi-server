package com.kordi_api.quiz.repository;

import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.quiz.entity.QuizChord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizChordRepository extends JpaRepository<QuizChord, Long> {

  List<QuizChord> findByDifficulty(Difficulty difficulty);
}
