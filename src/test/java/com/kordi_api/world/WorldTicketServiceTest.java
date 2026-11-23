package com.kordi_api.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.global.store.InMemoryExpiringStoreFactory;
import com.kordi_api.support.MutableClock;
import org.junit.jupiter.api.Test;

class WorldTicketServiceTest {

  private final MutableClock clock = new MutableClock();
  private final InMemoryExpiringStoreFactory stores = new InMemoryExpiringStoreFactory(clock);
  private final WorldTicketService service = new WorldTicketService(stores);

  @Test
  void 입장권은_한_번만_쓸_수_있다() {
    String ticket = service.issue(7L);
    clock.set(1_000);
    assertThat(service.redeem(ticket)).contains(7L);
    assertThat(service.redeem(ticket)).isEmpty();
  }

  @Test
  void 만료된_입장권은_거절한다() {
    String ticket = service.issue(7L);
    clock.set(WorldTicketService.TTL.toMillis() + 1);
    assertThat(service.redeem(ticket)).isEmpty();
  }

  @Test
  void 없는_입장권은_거절한다() {
    assertThat(service.redeem("nope")).isEmpty();
    assertThat(service.redeem(null)).isEmpty();
  }

  @Test
  void 탭_여러_개가_동시에_받은_입장권은_모두_쓸_수_있다() {
    String first = service.issue(7L);
    clock.set(10);
    String second = service.issue(7L);
    clock.set(20);
    assertThat(service.redeem(first)).contains(7L);
    assertThat(service.redeem(second)).contains(7L);
  }

  @Test
  void 너무_많이_발급하면_가장_오래된_것부터_무효가_된다() {
    String oldest = service.issue(7L);
    clock.set(1);
    for (int i = 0; i < WorldTicketService.MAX_PER_USER; i++) {
      service.issue(7L);
    }
    clock.set(2);
    assertThat(service.redeem(oldest)).isEmpty();
  }

  @Test
  void 쓴_입장권은_유저당_한도에서_빠진다() {
    String kept = service.issue(7L);
    for (int i = 0; i < WorldTicketService.MAX_PER_USER; i++) {
      service.redeem(service.issue(7L));
    }
    assertThat(service.redeem(kept)).contains(7L);
  }

  @Test
  void 만료된_입장권은_정리된다() {
    String ticket = service.issue(7L);
    clock.set(WorldTicketService.TTL.toMillis() + 1);
    stores.sweep();
    clock.set(0);
    assertThat(service.redeem(ticket)).isEmpty();
  }
}
