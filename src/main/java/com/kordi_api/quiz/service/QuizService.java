package com.kordi_api.quiz.service;

import com.kordi_api.quiz.dto.QuizChordResponse;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.quiz.repository.QuizChordRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

  private final QuizChordRepository quizChordRepository;

  public List<QuizChordResponse> getQuizChords(Difficulty difficulty) {
    return quizChordRepository.findRandomByDifficulty(difficulty.name()).stream()
        .map(QuizChordResponse::from)
        .toList();
  }
}
