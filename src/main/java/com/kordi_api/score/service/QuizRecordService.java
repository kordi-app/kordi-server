package com.kordi_api.score.service;

import com.kordi_api.global.common.RecordWriter;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.score.dto.QuizRecordResult;
import com.kordi_api.score.entity.QuizBestScore;
import com.kordi_api.score.entity.Score;
import com.kordi_api.score.repository.QuizBestScoreRepository;
import com.kordi_api.score.repository.ScoreRepository;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 서버가 채점한 퀴즈 한 판을 기록한다: 시도 기록({@code scores}, 내 기록)에 한 줄을 넣고, 최고 기록({@code quiz_best_scores}, 랭킹)은
 * 더 높을 때만 갱신한다. 동시 첫 기록 경합은 {@link RecordWriter}가 처리한다 (재시도하면 시도 기록도 함께 롤백됐다가 다시 들어가므로 두 번 남지 않는다).
 */
@Service
@RequiredArgsConstructor
public class QuizRecordService {

  private final ScoreRepository scoreRepository;
  private final QuizBestScoreRepository quizBestScoreRepository;
  private final UserRepository userRepository;
  private final RecordWriter recordWriter;
  private final Clock clock;

  public QuizRecordResult record(
      Long userId, Difficulty difficulty, int totalScore, int correctCount, int totalCount) {
    return recordWriter.write(() -> save(userId, difficulty, totalScore, correctCount, totalCount));
  }

  private QuizRecordResult save(
      Long userId, Difficulty difficulty, int totalScore, int correctCount, int totalCount) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));
    scoreRepository.save(Score.record(user, difficulty, totalScore, correctCount, totalCount));

    Instant now = clock.instant();
    long rank = quizBestScoreRepository.countAhead(difficulty, userId, totalScore, now) + 1;
    var existing = quizBestScoreRepository.findForUpdate(userId, difficulty);
    if (existing.isEmpty()) {
      if (totalScore == 0) {
        // nothing scored: history only, an empty game doesn't go on the board
        return new QuizRecordResult(0, rank, false);
      }
      quizBestScoreRepository.saveAndFlush(
          QuizBestScore.first(user, difficulty, totalScore, correctCount, totalCount, now));
      return new QuizRecordResult(totalScore, rank, true);
    }
    QuizBestScore best = existing.get();
    boolean improved = best.improve(totalScore, correctCount, totalCount, now);
    return new QuizRecordResult(best.getTotalScore(), rank, improved);
  }
}
