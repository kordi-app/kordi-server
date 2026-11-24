package com.kordi_api.score.service;

import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.dto.ScoreResponse;
import com.kordi_api.score.repository.ScoreRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScoreService {

  /** 내 기록 조회 시 최대 개수 */
  private static final Limit MY_SCORES_LIMIT = Limit.of(50);

  private final ScoreRepository scoreRepository;

  public List<ScoreResponse> getMyScores(Long userId) {
    return scoreRepository.findByUserIdOrderByCreatedAtDesc(userId, MY_SCORES_LIMIT).stream()
        .map(ScoreResponse::from)
        .toList();
  }

  public List<ScoreResponse> getMyScoresByDifficulty(Long userId, Difficulty difficulty) {
    return scoreRepository
        .findByUserIdAndDifficultyOrderByTotalScoreDesc(userId, difficulty, MY_SCORES_LIMIT)
        .stream()
        .map(ScoreResponse::from)
        .toList();
  }
}
