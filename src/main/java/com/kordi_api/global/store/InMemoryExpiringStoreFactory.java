package com.kordi_api.global.store;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;

/** 메모리 보관소 (서버 1대 기준). 만료된 값은 읽을 때 바로 걸러지고, 아무도 읽지 않는 값은 모든 보관소를 함께 도는 주기 정리가 치운다. */
public class InMemoryExpiringStoreFactory implements ExpiringStoreFactory {

  static final long SWEEP_INTERVAL_MS = 60_000;

  private final Clock clock;
  private final List<InMemoryExpiringStore<?>> stores = new CopyOnWriteArrayList<>();

  public InMemoryExpiringStoreFactory(Clock clock) {
    this.clock = clock;
  }

  @Override
  public <V> ExpiringStore<V> create(String name, Class<V> type) {
    InMemoryExpiringStore<V> store = new InMemoryExpiringStore<>(clock);
    stores.add(store);
    return store;
  }

  /** 만료된 값을 모든 보관소에서 치운다. */
  @Scheduled(fixedRate = SWEEP_INTERVAL_MS)
  public void sweep() {
    stores.forEach(InMemoryExpiringStore::sweep);
  }
}
