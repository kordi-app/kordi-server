package com.kordi_api.score.service;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.dto.ScoreRequest;
import com.kordi_api.score.dto.ScoreResponse;
import com.kordi_api.score.entity.Score;
import com.kordi_api.score.repository.ScoreRepository;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScoreService {

  private final ScoreRepository scoreRepository;
  private final UserRepository userRepository;

  @Transactional
  public ScoreResponse saveScore(Long userId, ScoreRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));

    Score score =
        Score.builder()
            .user(user)
            .difficulty(request.difficulty())
            .totalScore(request.totalScore())
            .correctCount(request.correctCount())
            .totalCount(request.totalCount())
            .build();

    scoreRepository.save(score);
    return ScoreResponse.from(score);
  }

  public List<ScoreResponse> getMyScores(Long userId) {
    return scoreRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(ScoreResponse::from)
        .toList();
  }

  public List<ScoreResponse> getMyScoresByDifficulty(Long userId, Difficulty difficulty) {
    return scoreRepository
        .findByUserIdAndDifficultyOrderByTotalScoreDesc(userId, difficulty)
        .stream()
        .map(ScoreResponse::from)
        .toList();
  }
}
