package com.kordi_api.score.service;

import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.dto.RankingResponse;
import com.kordi_api.score.repository.QuizBestScoreRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 코드 퀴즈 난이도별 랭킹: 유저당 최고 기록 한 줄({@code quiz_best_scores})을 점수순으로. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

  private static final Limit RANKING_LIMIT = Limit.of(20);

  private final QuizBestScoreRepository quizBestScoreRepository;

  public List<RankingResponse> getRankings(Difficulty difficulty) {
    return quizBestScoreRepository
        .findByDifficultyOrderByTotalScoreDescAchievedAtAsc(difficulty, RANKING_LIMIT)
        .stream()
        .map(
            best ->
                new RankingResponse(
                    best.getUser().getNickname(),
                    best.getUser().getProfileImageUrl(),
                    best.getTotalScore(),
                    best.getCorrectCount(),
                    best.getTotalCount()))
        .toList();
  }
}
