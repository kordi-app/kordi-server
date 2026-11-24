package com.kordi_api.review.service;

import com.kordi_api.global.common.RecordWriter;
import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.quiz.entity.QuizChord;
import com.kordi_api.quiz.repository.QuizChordRepository;
import com.kordi_api.review.dto.ReviewCardResponse;
import com.kordi_api.review.dto.ReviewDeckResponse;
import com.kordi_api.review.dto.ReviewOutcomeRequest;
import com.kordi_api.review.dto.ReviewResultResponse;
import com.kordi_api.review.entity.ChordReview;
import com.kordi_api.review.repository.ChordReviewQueryRepository;
import com.kordi_api.review.repository.ChordReviewRepository;
import com.kordi_api.user.entity.User;
import com.kordi_api.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코드별 정답·오답 기록과 복습 일정. 서버가 채점한 퀴즈 결과({@link #recordQuiz})와 복습 모드의 결과({@link #recordPractice})를 받아
 * 갱신하고, 오늘의 복습 카드({@link #deck})를 뽑는다. 비회원은 기록하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ChordReviewService {

  public static final int DEFAULT_DECK_SIZE = 10;
  public static final int MAX_DECK_SIZE = 30;

  /** 코드 하나를 한 번 친 결과 */
  public record Outcome(long chordId, boolean correct) {}

  private final ChordReviewRepository reviewRepository;
  private final ChordReviewQueryRepository reviewQueryRepository;
  private final QuizChordRepository quizChordRepository;
  private final UserRepository userRepository;
  private final RecordWriter recordWriter;
  private final Clock clock;

  /** 퀴즈 한 판의 문제별 결과 (questionIds와 correct는 문제 순서대로 같은 길이). */
  public void recordQuiz(Long userId, List<Long> questionIds, List<Boolean> correct) {
    if (userId == null || questionIds.size() != correct.size()) {
      return;
    }
    List<Outcome> outcomes = new ArrayList<>(questionIds.size());
    for (int i = 0; i < questionIds.size(); i++) {
      outcomes.add(new Outcome(questionIds.get(i), correct.get(i)));
    }
    record(userId, outcomes);
  }

  /** 복습 모드 결과. 없는 코드 id가 섞여 있으면 아무것도 반영하지 않고 400. */
  public ReviewResultResponse recordPractice(Long userId, List<ReviewOutcomeRequest> results) {
    List<Outcome> outcomes =
        results.stream().map(r -> new Outcome(r.chordId(), r.correct())).toList();
    record(userId, outcomes);
    return new ReviewResultResponse(
        outcomes.size(),
        reviewRepository.countByUserIdAndDueAtLessThanEqual(userId, clock.instant()));
  }

  /** 동시 첫 기록 경합(유니크 위반)은 {@link RecordWriter}가 한 번 다시 실행해 처리한다. */
  void record(Long userId, List<Outcome> outcomes) {
    if (outcomes.isEmpty()) {
      return;
    }
    recordWriter.write(
        () -> {
          apply(userId, outcomes);
          return null;
        });
  }

  private void apply(Long userId, List<Outcome> outcomes) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CoreException(CoreExceptionCode.USER_NOT_FOUND));
    Set<Long> chordIds = new LinkedHashSet<>();
    outcomes.forEach(o -> chordIds.add(o.chordId()));

    Map<Long, ChordReview> cards = new HashMap<>();
    reviewRepository
        .findForUpdate(userId, chordIds)
        .forEach(card -> cards.put(card.getChord().getId(), card));

    List<Long> missing = chordIds.stream().filter(id -> !cards.containsKey(id)).toList();
    Instant now = clock.instant();
    if (!missing.isEmpty()) {
      List<QuizChord> chords = quizChordRepository.findAllById(missing);
      if (chords.size() != missing.size()) {
        throw new CoreException(CoreExceptionCode.INVALID_REQUEST, "unknown chord id");
      }
      List<ChordReview> created = new ArrayList<>(chords.size());
      for (QuizChord chord : chords) {
        ChordReview card = ChordReview.start(user, chord, now);
        cards.put(chord.getId(), card);
        created.add(card);
      }
      reviewRepository.saveAll(created);
    }
    for (Outcome outcome : outcomes) {
      cards.get(outcome.chordId()).answer(outcome.correct(), now);
    }
    reviewRepository.flush();
  }

  /** 오늘의 복습: 때가 된 카드(약한 것·오래 밀린 것부터), 모자라면 자주 틀린 카드로 채운다. */
  @Transactional(readOnly = true)
  public ReviewDeckResponse deck(Long userId, int limit) {
    int size = Math.clamp(limit, 1, MAX_DECK_SIZE);
    Instant now = clock.instant();
    List<ChordReview> cards = new ArrayList<>(reviewQueryRepository.findDue(userId, now, size));
    if (cards.size() < size) {
      cards.addAll(
          reviewQueryRepository.findMostMissed(
              userId, now, cards.stream().map(ChordReview::getId).toList(), size - cards.size()));
    }
    long dueCount = reviewRepository.countByUserIdAndDueAtLessThanEqual(userId, now);
    return new ReviewDeckResponse(
        dueCount, cards.stream().map(card -> ReviewCardResponse.of(card, now)).toList());
  }
}
