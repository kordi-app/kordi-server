package com.kordi_api.world;

import com.kordi_api.global.store.ExpiringStore;
import com.kordi_api.global.store.ExpiringStoreFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 월드 소켓용 1회용 입장권. 로그인 토큰은 브라우저 JS가 읽을 수 없는 httpOnly 쿠키에 있어 소켓에 직접 실을 수 없다. 그래서 로그인한 유저가 REST로 입장권을
 * 받아(BFF가 쿠키를 Bearer로 바꿔 줌) 소켓에 보내면, 그 소켓을 그 유저로 인정한다.
 *
 * <p>짧게 살고(1분) 한 번 쓰면 사라진다. 유저당 살아 있는 입장권은 {@value #MAX_PER_USER}장까지: 탭 여러 개가 동시에 입장권을 받아도 서로를
 * 무효화하지 않고, 그 이상 반복 발급하면 가장 오래된 것부터 버려 쌓이지 않는다. {@link ExpiringStore}에 보관.
 */
@Component
public class WorldTicketService {

  static final Duration TTL = Duration.ofSeconds(60);
  static final int MAX_PER_USER = 5;

  /** ticket → user id */
  private final ExpiringStore<Long> tickets;

  /** user → their tickets, oldest first. Lives as long as their newest ticket. */
  private final ExpiringStore<Owned> ticketsOf;

  private record Owned(List<String> tickets) {}

  public WorldTicketService(ExpiringStoreFactory stores) {
    this.tickets = stores.create("world-ticket", Long.class);
    this.ticketsOf = stores.create("world-ticket-owner", Owned.class);
  }

  public String issue(long userId) {
    String ticket = UUID.randomUUID().toString();
    tickets.put(ticket, userId, TTL);
    List<String> evicted = new ArrayList<>();
    ticketsOf.compute(
        key(userId),
        owned -> {
          evicted.clear(); // remap may be retried
          List<String> queue = new ArrayList<>(owned == null ? List.of() : owned.tickets());
          queue.addLast(ticket);
          while (queue.size() > MAX_PER_USER) {
            evicted.add(queue.removeFirst());
          }
          return new Owned(List.copyOf(queue));
        },
        TTL);
    evicted.forEach(tickets::remove);
    return ticket;
  }

  /** 유효한 입장권이면 유저 id를 돌려주고 폐기한다. */
  public Optional<Long> redeem(String ticket) {
    if (ticket == null) {
      return Optional.empty();
    }
    Optional<Long> userId = tickets.take(ticket);
    userId.ifPresent(
        id ->
            ticketsOf.compute(
                key(id),
                owned -> {
                  if (owned == null) {
                    return null;
                  }
                  List<String> rest = new ArrayList<>(owned.tickets());
                  rest.remove(ticket);
                  return rest.isEmpty() ? null : new Owned(List.copyOf(rest));
                },
                TTL));
    return userId;
  }

  private static String key(long userId) {
    return Long.toString(userId);
  }
}
