package com.kordi_api.quiz.session;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import com.kordi_api.quiz.dto.QuizChordResponse;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.quiz.service.QuizService;
import com.kordi_api.quiz.session.dto.QuizResultRequest;
import com.kordi_api.quiz.session.dto.QuizResultResponse;
import com.kordi_api.quiz.session.dto.QuizSessionResponse;
import com.kordi_api.score.dto.QuizRecordResult;
import com.kordi_api.score.service.QuizRecordService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 서버가 채점하는 코드 퀴즈. 판을 열면 서버가 문제를 뽑아 기억하고, 결과로는 문제마다 누른 음과 걸린 시간만 받아 서버가 정답 판정·점수 계산을 한 뒤 기록한다
 * ({@link QuizGrader}).
 */
@Service
@RequiredArgsConstructor
public class QuizSessionService {

  private final QuizService quizService;
  private final QuizSessionStore sessionStore;
  private final QuizRecordService quizRecordService;

  public QuizSessionResponse start(Long userId, Difficulty difficulty) {
    List<QuizChordResponse> questions = quizService.getQuizChords(difficulty);
    if (questions.isEmpty()) {
      throw new CoreException(CoreExceptionCode.NOT_FOUND, "no quiz chords for " + difficulty);
    }
    QuizSessionStore.Session session =
        sessionStore.open(
            userId,
            difficulty,
            questions.stream().map(QuizChordResponse::id).toList(),
            questions.stream().map(QuizChordResponse::name).toList());
    return QuizSessionResponse.of(session.id(), questions);
  }

  public QuizResultResponse submit(Long userId, String sessionId, QuizResultRequest request) {
    QuizSessionStore.Session session = sessionStore.take(userId, sessionId).orElse(null);
    if (session == null) {
      // a retried submit whose first response was lost gets the same result back
      return sessionStore
          .completedResult(userId, sessionId, request.answers())
          .orElseThrow(
              () ->
                  new CoreException(
                      CoreExceptionCode.INVALID_REQUEST, "no open quiz session " + sessionId));
    }
    // a rejected submit burns the session: answers can't be probed by resubmitting
    QuizGrader.Graded graded =
        QuizGrader.grade(
            session.chordNames(), request.answers(), sessionStore.elapsedSinceIssue(session));

    QuizRecordResult record;
    try {
      record =
          quizRecordService.record(
              userId,
              session.difficulty(),
              graded.totalScore(),
              graded.correctCount(),
              session.chordNames().size());
    } catch (RuntimeException e) {
      if (!(e instanceof CoreException)) {
        sessionStore.restore(session); // a server-side failure: let the player retry the save
      }
      throw e;
    }
    QuizResultResponse response =
        new QuizResultResponse(
            graded.totalScore(),
            graded.correctCount(),
            session.chordNames().size(),
            graded.results(),
            record.best(),
            record.rank(),
            record.improved());
    sessionStore.complete(userId, sessionId, request.answers(), response);
    return response;
  }
}
