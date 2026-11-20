package com.kordi_api.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorldTicketServiceTest {

  private final WorldTicketService service = new WorldTicketService();

  @Test
  void 입장권은_한_번만_쓸_수_있다() {
    String ticket = service.issue(7L, 0);
    assertThat(service.redeem(ticket, 1_000)).contains(7L);
    assertThat(service.redeem(ticket, 1_000)).isEmpty();
  }

  @Test
  void 만료된_입장권은_거절한다() {
    String ticket = service.issue(7L, 0);
    assertThat(service.redeem(ticket, WorldTicketService.TTL_MS + 1)).isEmpty();
  }

  @Test
  void 없는_입장권은_거절한다() {
    assertThat(service.redeem("nope", 0)).isEmpty();
    assertThat(service.redeem(null, 0)).isEmpty();
  }

  @Test
  void 탭_여러_개가_동시에_받은_입장권은_모두_쓸_수_있다() {
    String first = service.issue(7L, 0);
    String second = service.issue(7L, 10);
    assertThat(service.redeem(first, 20)).contains(7L);
    assertThat(service.redeem(second, 20)).contains(7L);
  }

  @Test
  void 너무_많이_발급하면_가장_오래된_것부터_무효가_된다() {
    String oldest = service.issue(7L, 0);
    for (int i = 0; i < WorldTicketService.MAX_PER_USER; i++) {
      service.issue(7L, 1);
    }
    assertThat(service.redeem(oldest, 2)).isEmpty();
  }

  @Test
  void 만료된_입장권은_정리된다() {
    String ticket = service.issue(7L, 0);
    service.sweep(WorldTicketService.TTL_MS + 1);
    assertThat(service.redeem(ticket, 0)).isEmpty();
  }
}
