package com.kordi_api.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * IP당 동시 월드 접속 수 제한. 소켓은 로그인 없이 열리므로, 스크립트 하나가 전체 정원({@link WorldLobby#MAX_TOTAL})을 채워 모두를 "가득 참"으로
 * 밀어내지 못하게 한다. 한 집·한 사무실에서 여러 탭을 여는 정도는 넉넉히 허용한다.
 */
@Component
public class ConnectionLimiter {

  static final int MAX_PER_IP = 10;

  private final Map<String, Integer> counts = new ConcurrentHashMap<>();

  /** 자리가 있으면 차지하고 true. */
  public boolean tryAcquire(String ip) {
    boolean[] ok = {false};
    counts.compute(
        ip,
        (k, n) -> {
          int current = n == null ? 0 : n;
          if (current >= MAX_PER_IP) {
            return current;
          }
          ok[0] = true;
          return current + 1;
        });
    return ok[0];
  }

  public void release(String ip) {
    counts.computeIfPresent(ip, (k, n) -> n <= 1 ? null : n - 1);
  }
}
