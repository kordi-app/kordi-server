package com.kordi_api.quiz.service;

import com.kordi_api.quiz.QuizRules;
import com.kordi_api.quiz.dto.QuizChordResponse;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.quiz.repository.QuizChordRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 퀴즈 문제 뽑기. 코드 목록은 Flyway 마이그레이션으로 채워진 뒤 바뀌지 않으므로 난이도별로 한 번만 읽어 두고, 요청마다 메모리에서 섞어 뽑는다 (ORDER BY
 * RAND()로 매번 DB를 치지 않도록).
 */
@Service
@RequiredArgsConstructor
public class QuizService {

  private final QuizChordRepository quizChordRepository;
  private final Map<Difficulty, List<QuizChordResponse>> chordsByDifficulty =
      new ConcurrentHashMap<>();

  public List<QuizChordResponse> getQuizChords(Difficulty difficulty) {
    List<QuizChordResponse> pool = new ArrayList<>(chords(difficulty));
    Collections.shuffle(pool, ThreadLocalRandom.current());
    return List.copyOf(pool.subList(0, Math.min(pool.size(), QuizRules.QUESTIONS_PER_QUIZ)));
  }

  private List<QuizChordResponse> chords(Difficulty difficulty) {
    List<QuizChordResponse> cached = chordsByDifficulty.get(difficulty);
    if (cached != null) {
      return cached;
    }
    List<QuizChordResponse> loaded =
        quizChordRepository.findByDifficulty(difficulty).stream()
            .map(QuizChordResponse::from)
            .toList();
    if (!loaded.isEmpty()) {
      chordsByDifficulty.put(difficulty, loaded); // an empty table (not seeded yet) is not cached
    }
    return loaded;
  }
}
