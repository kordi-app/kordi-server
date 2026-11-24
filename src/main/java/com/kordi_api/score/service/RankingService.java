package com.kordi_api.score.service;

import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.dto.RankingResponse;
import com.kordi_api.score.repository.QuizRankingQueryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 코드 퀴즈 난이도별 랭킹: 유저당 최고 기록 한 줄({@code quiz_best_scores})을 점수순으로 (전체 또는 나와 친구만). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

  static final int RANKING_LIMIT = 20;

  private final QuizRankingQueryRepository quizRankingQueryRepository;

  /**
   * @param friendsOf 이 유저와 수락된 친구만 (null이면 전체)
   */
  public List<RankingResponse> getRankings(Difficulty difficulty, Long friendsOf) {
    return quizRankingQueryRepository.findRanking(difficulty, friendsOf, RANKING_LIMIT).stream()
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
