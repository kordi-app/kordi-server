package com.kordi_api.global.store;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/** ConcurrentHashMap 위의 {@link ExpiringStore}. 만료는 읽을 때 걸러지고 {@link #sweep()}이 치운다. */
class InMemoryExpiringStore<V> implements ExpiringStore<V> {

  private record Entry<V>(V value, long expiresAt) {}

  private final Clock clock;
  private final Map<String, Entry<V>> entries = new ConcurrentHashMap<>();

  InMemoryExpiringStore(Clock clock) {
    this.clock = clock;
  }

  @Override
  public void put(String key, V value, Duration ttl) {
    entries.put(key, new Entry<>(value, expiresAt(ttl)));
  }

  @Override
  public Optional<V> get(String key) {
    Entry<V> entry = entries.get(key);
    if (entry == null) {
      return Optional.empty();
    }
    if (isExpired(entry, clock.millis())) {
      entries.remove(key, entry);
      return Optional.empty();
    }
    return Optional.of(entry.value());
  }

  @Override
  public Optional<V> take(String key) {
    Entry<V> entry = entries.remove(key);
    return entry == null || isExpired(entry, clock.millis())
        ? Optional.empty()
        : Optional.of(entry.value());
  }

  @Override
  public void remove(String key) {
    entries.remove(key);
  }

  @Override
  public boolean remove(String key, V expected) {
    long now = clock.millis();
    boolean[] removed = {false};
    entries.computeIfPresent(
        key,
        (k, entry) -> {
          if (isExpired(entry, now)) {
            return null;
          }
          if (entry.value().equals(expected)) {
            removed[0] = true;
            return null;
          }
          return entry;
        });
    return removed[0];
  }

  @Override
  public Optional<V> compute(String key, UnaryOperator<V> remap, Duration ttl) {
    long now = clock.millis();
    Entry<V> updated =
        entries.compute(
            key,
            (k, entry) -> {
              V current = entry == null || isExpired(entry, now) ? null : entry.value();
              V next = remap.apply(current);
              return next == null ? null : new Entry<>(next, now + ttl.toMillis());
            });
    return updated == null ? Optional.empty() : Optional.of(updated.value());
  }

  void sweep() {
    long now = clock.millis();
    entries.values().removeIf(entry -> isExpired(entry, now));
  }

  private long expiresAt(Duration ttl) {
    return clock.millis() + ttl.toMillis();
  }

  private static boolean isExpired(Entry<?> entry, long now) {
    return entry.expiresAt() < now;
  }
}
