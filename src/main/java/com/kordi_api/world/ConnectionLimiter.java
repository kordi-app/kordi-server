package com.kordi_api.world;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * IP당 동시 월드 접속 수 제한. 소켓은 로그인 없이 열리므로, 스크립트 하나가 전체 정원({@link WorldLobby#MAX_TOTAL})을 채워 모두를 "가득 참"으로
 * 밀어내지 못하게 한다.
 *
 * <p>상한은 넉넉히: 학교 교실·학원처럼 여러 사람이 한 공인 IP(NAT)를 함께 쓰는 곳에서도 한 반이 다 들어올 수 있어야 한다. 그래도 한 IP가 정원의 5%를 넘게
 * 차지하지는 못한다.
 *
 * <p>IPv6는 한 가입자가 보통 /64 대역을 통째로 받아 주소를 마음대로 바꿀 수 있으므로, 같은 /64는 한 IP로 센다.
 */
@Component
public class ConnectionLimiter {

  static final int MAX_PER_IP = 50;

  private final Map<String, Integer> counts = new ConcurrentHashMap<>();

  /** 자리가 있으면 차지하고 true. */
  public boolean tryAcquire(String ip) {
    boolean[] ok = {false};
    counts.compute(
        key(ip),
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
    counts.computeIfPresent(key(ip), (k, n) -> n <= 1 ? null : n - 1);
  }

  /** IPv6 주소는 앞 64비트(/64)로 묶는다. IPv4(IPv4-mapped 포함)와 알아볼 수 없는 값은 그대로. */
  static String key(String ip) {
    if (ip == null || ip.indexOf(':') < 0) {
      return ip;
    }
    try {
      // a literal containing ':' is parsed, never looked up in DNS
      InetAddress address = InetAddress.getByName(ip);
      if (address instanceof Inet6Address) {
        byte[] bytes = address.getAddress();
        return HexFormat.of().formatHex(bytes, 0, 8) + "::/64";
      }
      return address.getHostAddress();
    } catch (UnknownHostException | SecurityException e) {
      return ip;
    }
  }
}
