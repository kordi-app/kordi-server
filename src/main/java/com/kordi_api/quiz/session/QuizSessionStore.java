package com.kordi_api.quiz.session;

import com.kordi_api.global.store.ExpiringStore;
import com.kordi_api.global.store.ExpiringStoreFactory;
import com.kordi_api.quiz.QuizRules;
import com.kordi_api.quiz.entity.Difficulty;
import com.kordi_api.quiz.session.dto.QuizAnswerRequest;
import com.kordi_api.quiz.session.dto.QuizResultResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 진행 중인 퀴즈 판. 판을 열 때 낸 문제와 발급 시각을 서버가 기억해 두고, 결과는 그 판을 연 유저만 한 번 낼 수 있다. 유저당 한 판만 살아 있고(새로 열면 이전 판은
 * 무효), {@link QuizRules#SESSION_TTL}이 지나면 사라진다 ({@link ExpiringStore}에 보관).
 *
 * <p>채점이 끝난 판의 응답은 잠깐({@link #COMPLETED_TTL}) 남겨 둔다: 응답이 네트워크에서 사라져 클라이언트가 같은 답을 다시 보내면 같은 결과를 돌려준다
 * (다시 채점·기록하지 않음).
 */
@Component
public class QuizSessionStore {

  static final Duration COMPLETED_TTL = Duration.ofMinutes(5);

  /** 열린 판: 낸 문제(id·이름, 순서대로)와 발급 시각 */
  public record Session(
      String id,
      long userId,
      Difficulty difficulty,
      List<Long> questionIds,
      List<String> chordNames,
      long issuedAt) {}

  private record Completed(
      String sessionId, List<QuizAnswerRequest> answers, QuizResultResponse response) {}

  private final ExpiringStore<Session> sessions;
  private final ExpiringStore<Completed> completed;
  private final Clock clock;

  public QuizSessionStore(ExpiringStoreFactory stores, Clock clock) {
    this.sessions = stores.create("quiz-session", Session.class);
    this.completed = stores.create("quiz-session-completed", Completed.class);
    this.clock = clock;
  }

  public Session open(
      long userId, Difficulty difficulty, List<Long> questionIds, List<String> chordNames) {
    Session session =
        new Session(
            UUID.randomUUID().toString(),
            userId,
            difficulty,
            List.copyOf(questionIds),
            List.copyOf(chordNames),
            clock.millis());
    sessions.put(key(userId), session, QuizRules.SESSION_TTL);
    return session;
  }

  /** 그 유저의 살아 있는 판이 이 id면 꺼내며 지운다 (한 번만). */
  public Optional<Session> take(long userId, String sessionId) {
    Session session = sessions.get(key(userId)).orElse(null);
    if (session == null
        || !session.id().equals(sessionId)
        || !sessions.remove(key(userId), session)) {
      return Optional.empty();
    }
    return Optional.of(session);
  }

  /** 채점 전에 저장이 실패했을 때(서버 오류) 판을 되돌려 놓아 다시 낼 수 있게 한다. 남은 수명은 처음 발급 기준. */
  public void restore(Session session) {
    long left = session.issuedAt() + QuizRules.SESSION_TTL.toMillis() - clock.millis();
    if (left > 0) {
      sessions.compute(
          key(session.userId()),
          current -> current == null ? session : current,
          Duration.ofMillis(left));
    }
  }

  /** 판이 열린 뒤 지난 시간 (서버 시계) */
  public long elapsedSinceIssue(Session session) {
    return clock.millis() - session.issuedAt();
  }

  public void complete(
      long userId, String sessionId, List<QuizAnswerRequest> answers, QuizResultResponse response) {
    completed.put(
        key(userId), new Completed(sessionId, List.copyOf(answers), response), COMPLETED_TTL);
  }

  /** 이미 채점한 판에 같은 답이 다시 오면 그때의 결과 */
  public Optional<QuizResultResponse> completedResult(
      long userId, String sessionId, List<QuizAnswerRequest> answers) {
    return completed
        .get(key(userId))
        .filter(c -> c.sessionId().equals(sessionId) && c.answers().equals(answers))
        .map(Completed::response);
  }

  private static String key(long userId) {
    return Long.toString(userId);
  }
}
