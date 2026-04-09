package com.kordi_api.score.service;

import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.dto.RankingResponse;
import com.kordi_api.score.repository.ScoreRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

  private static final int RANKING_LIMIT = 20;

  private final ScoreRepository scoreRepository;

  public List<RankingResponse> getRankings(Difficulty difficulty) {
    return scoreRepository
        .findTopScoresByDifficulty(difficulty, PageRequest.ofSize(RANKING_LIMIT))
        .stream()
        .map(
            score ->
                new RankingResponse(
                    score.getUser().getNickname(),
                    score.getUser().getProfileImageUrl(),
                    score.getTotalScore(),
                    score.getCorrectCount(),
                    score.getTotalCount()))
        .toList();
  }
}
