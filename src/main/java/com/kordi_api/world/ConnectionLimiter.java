package com.kordi_api.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * IP당 동시 월드 접속 수 제한. 소켓은 로그인 없이 열리므로, 스크립트 하나가 전체 정원({@link WorldLobby#MAX_TOTAL})을 채워 모두를 "가득 참"으로
 * 밀어내지 못하게 한다.
 *
 * <p>상한은 넉넉히: 학교 교실·학원처럼 여러 사람이 한 공인 IP(NAT)를 함께 쓰는 곳에서도 한 반이 다 들어올 수 있어야 한다. 그래도 한 IP가 정원의 5%를 넘게
 * 차지하지는 못한다.
 */
@Component
public class ConnectionLimiter {

  static final int MAX_PER_IP = 50;

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
