package com.kordi_api.world;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 월드 소켓용 1회용 입장권. 로그인 토큰은 브라우저 JS가 읽을 수 없는 httpOnly 쿠키에 있어 소켓에 직접 실을 수 없다. 그래서 로그인한 유저가 REST로 입장권을
 * 받아(BFF가 쿠키를 Bearer로 바꿔 줌) 소켓에 보내면, 그 소켓을 그 유저로 인정한다.
 *
 * <p>짧게 살고(1분) 한 번 쓰면 사라진다. 메모리 보관 (서버 1대 기준).
 */
@Component
public class WorldTicketService {

  static final long TTL_MS = 60_000;

  private record Entry(long userId, long expiresAt) {}

  private final Map<String, Entry> tickets = new ConcurrentHashMap<>();

  public String issue(long userId, long nowMillis) {
    tickets.values().removeIf(e -> e.expiresAt() < nowMillis); // 만료된 것 정리 (양이 적다)
    String ticket = UUID.randomUUID().toString();
    tickets.put(ticket, new Entry(userId, nowMillis + TTL_MS));
    return ticket;
  }

  /** 유효한 입장권이면 유저 id를 돌려주고 폐기한다. */
  public Optional<Long> redeem(String ticket, long nowMillis) {
    if (ticket == null) {
      return Optional.empty();
    }
    Entry entry = tickets.remove(ticket);
    if (entry == null || entry.expiresAt() < nowMillis) {
      return Optional.empty();
    }
    return Optional.of(entry.userId());
  }
}
