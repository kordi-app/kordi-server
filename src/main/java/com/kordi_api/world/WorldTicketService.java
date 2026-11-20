package com.kordi_api.world;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 월드 소켓용 1회용 입장권. 로그인 토큰은 브라우저 JS가 읽을 수 없는 httpOnly 쿠키에 있어 소켓에 직접 실을 수 없다. 그래서 로그인한 유저가 REST로 입장권을
 * 받아(BFF가 쿠키를 Bearer로 바꿔 줌) 소켓에 보내면, 그 소켓을 그 유저로 인정한다.
 *
 * <p>짧게 살고(1분) 한 번 쓰면 사라진다. 유저당 살아 있는 입장권은 하나뿐(새로 받으면 이전 것은 무효)이라 반복 발급으로 쌓이지 않는다. 메모리 보관 (서버 1대
 * 기준).
 */
@Component
public class WorldTicketService {

  static final long TTL_MS = 60_000;

  private record Entry(long userId, long expiresAt) {}

  private final Map<String, Entry> tickets = new ConcurrentHashMap<>();
  private final Map<Long, String> ticketOf = new ConcurrentHashMap<>();

  public String issue(long userId, long nowMillis) {
    String ticket = UUID.randomUUID().toString();
    tickets.put(ticket, new Entry(userId, nowMillis + TTL_MS));
    String previous = ticketOf.put(userId, ticket);
    if (previous != null) {
      tickets.remove(previous);
    }
    return ticket;
  }

  /** 쓰이지 않고 만료된 입장권 정리 */
  @Scheduled(fixedRate = TTL_MS)
  void sweep() {
    sweep(System.currentTimeMillis());
  }

  void sweep(long nowMillis) {
    tickets.entrySet().removeIf(e -> e.getValue().expiresAt() < nowMillis);
    ticketOf.values().removeIf(ticket -> !tickets.containsKey(ticket));
  }

  /** 유효한 입장권이면 유저 id를 돌려주고 폐기한다. */
  public Optional<Long> redeem(String ticket, long nowMillis) {
    if (ticket == null) {
      return Optional.empty();
    }
    Entry entry = tickets.remove(ticket);
    if (entry == null) {
      return Optional.empty();
    }
    ticketOf.remove(entry.userId(), ticket);
    if (entry.expiresAt() < nowMillis) {
      return Optional.empty();
    }
    return Optional.of(entry.userId());
  }
}
