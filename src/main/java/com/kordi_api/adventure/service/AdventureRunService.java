package com.kordi_api.adventure.service;

import com.kordi_api.global.store.ExpiringStore;
import com.kordi_api.global.store.ExpiringStoreFactory;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 진행 중인 코드 모험 판. 판을 시작할 때 서버가 시작 시각을 기억해 두고, 결과를 낼 때 걸린 시간을 서버가 직접 잰다 — 클라이언트가 보낸 시간은 믿지 않는다. 유저당 한
 * 판만 살아 있고(새로 시작하면 이전 판은 무효), 한 판의 결과는 한 번만 낼 수 있다. 판은 {@link #MAX_RUN}이 지나면 무효가 되어 사라진다 ({@link
 * ExpiringStore}에 보관).
 */
@Component
public class AdventureRunService {

  static final Duration MAX_RUN = Duration.ofHours(3);

  private record Run(String id, long startedAt) {}

  private final ExpiringStore<Run> runs;
  private final Clock clock;

  public AdventureRunService(ExpiringStoreFactory stores, Clock clock) {
    this.runs = stores.create("adventure-run", Run.class);
    this.clock = clock;
  }

  public String start(long userId) {
    String id = UUID.randomUUID().toString();
    runs.put(key(userId), new Run(id, clock.millis()), MAX_RUN);
    return id;
  }

  /** 그 유저의 살아 있는 판이 맞으면 걸린 시간을 돌려주고 끝낸다. */
  public Optional<Long> finish(long userId, String runId) {
    if (runId == null) {
      return Optional.empty();
    }
    Run run = runs.get(key(userId)).orElse(null);
    if (run == null || !run.id().equals(runId) || !runs.remove(key(userId), run)) {
      return Optional.empty();
    }
    long duration = clock.millis() - run.startedAt();
    return duration > 0 && duration <= MAX_RUN.toMillis()
        ? Optional.of(duration)
        : Optional.empty();
  }

  private static String key(long userId) {
    return Long.toString(userId);
  }
}
