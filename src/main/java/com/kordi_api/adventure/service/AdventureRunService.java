package com.kordi_api.adventure.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 진행 중인 코드 모험 판. 판을 시작할 때 서버가 시작 시각을 기억해 두고, 결과를 낼 때 걸린 시간을 서버가 직접 잰다 — 클라이언트가 보낸 시간은 믿지 않는다. 유저당 한
 * 판만 살아 있고(새로 시작하면 이전 판은 무효), 한 판의 결과는 한 번만 낼 수 있다. 메모리 보관 (서버 1대 기준).
 */
@Component
public class AdventureRunService {

  static final long MAX_RUN_MS = 3 * 60 * 60 * 1000L;

  private record Run(String id, long startedAt) {}

  private final Map<Long, Run> runs = new ConcurrentHashMap<>();

  public String start(long userId, long nowMillis) {
    String id = UUID.randomUUID().toString();
    runs.put(userId, new Run(id, nowMillis));
    return id;
  }

  /** 그 유저의 살아 있는 판이 맞으면 걸린 시간을 돌려주고 끝낸다. */
  public Optional<Long> finish(long userId, String runId, long nowMillis) {
    if (runId == null) {
      return Optional.empty();
    }
    Run run = runs.get(userId);
    if (run == null || !run.id().equals(runId) || !runs.remove(userId, run)) {
      return Optional.empty();
    }
    long duration = nowMillis - run.startedAt();
    return duration > 0 && duration <= MAX_RUN_MS ? Optional.of(duration) : Optional.empty();
  }

  @Scheduled(fixedRate = 10 * 60 * 1000L)
  void sweep() {
    sweep(System.currentTimeMillis());
  }

  void sweep(long nowMillis) {
    runs.values().removeIf(run -> nowMillis - run.startedAt() > MAX_RUN_MS);
  }
}
