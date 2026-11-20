package com.kordi_api.world;

import java.util.ArrayDeque;
import java.util.Deque;
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
 * <p>짧게 살고(1분) 한 번 쓰면 사라진다. 유저당 살아 있는 입장권은 {@value #MAX_PER_USER}장까지: 탭 여러 개가 동시에 입장권을 받아도 서로를
 * 무효화하지 않고, 그 이상 반복 발급하면 가장 오래된 것부터 버려 쌓이지 않는다. 메모리 보관 (서버 1대 기준).
 */
@Component
public class WorldTicketService {

  static final long TTL_MS = 60_000;
  static final int MAX_PER_USER = 5;

  private record Entry(long userId, long expiresAt) {}

  private final Map<String, Entry> tickets = new ConcurrentHashMap<>();

  /** user → their live tickets, oldest first */
  private final Map<Long, Deque<String>> ticketsOf = new ConcurrentHashMap<>();

  public String issue(long userId, long nowMillis) {
    String ticket = UUID.randomUUID().toString();
    tickets.put(ticket, new Entry(userId, nowMillis + TTL_MS));
    ticketsOf.compute(
        userId,
        (id, live) -> {
          Deque<String> queue = live == null ? new ArrayDeque<>() : live;
          queue.addLast(ticket);
          while (queue.size() > MAX_PER_USER) {
            tickets.remove(queue.removeFirst());
          }
          return queue;
        });
    return ticket;
  }

  /** 쓰이지 않고 만료된 입장권 정리 */
  @Scheduled(fixedRate = TTL_MS)
  void sweep() {
    sweep(System.currentTimeMillis());
  }

  void sweep(long nowMillis) {
    tickets.entrySet().removeIf(e -> e.getValue().expiresAt() < nowMillis);
    for (Long userId : ticketsOf.keySet()) {
      ticketsOf.computeIfPresent(
          userId,
          (id, queue) -> {
            queue.removeIf(ticket -> !tickets.containsKey(ticket));
            return queue.isEmpty() ? null : queue;
          });
    }
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
    ticketsOf.computeIfPresent(
        entry.userId(),
        (id, queue) -> {
          queue.remove(ticket);
          return queue.isEmpty() ? null : queue;
        });
    if (entry.expiresAt() < nowMillis) {
      return Optional.empty();
    }
    return Optional.of(entry.userId());
  }
}
