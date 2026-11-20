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
}
