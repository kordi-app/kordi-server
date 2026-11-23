package com.kordi_api.global.store;

import java.time.Duration;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * 짧게 사는 키-값 보관소. 각 값은 넣을 때 정한 TTL이 지나면 사라진다 (만료 시각 그 순간까지는 살아 있다). 만료 처리는 보관소가 맡으므로 쓰는 쪽은 시각을 따로
 * 기억하지 않는다. 지금은 메모리 구현({@link InMemoryExpiringStoreFactory})이지만, 서버가 여러 대가 되면 같은 계약의 Redis 구현으로 바꿀
 * 수 있게 {@link ExpiringStoreFactory}로만 얻는다.
 *
 * <p>값은 불변으로 다룬다: 넣은 뒤 바꾸지 말고, 바꿀 땐 새 값으로 {@link #put} / {@link #compute}한다 (원격 구현은 직렬화된 사본을 돌려준다).
 */
public interface ExpiringStore<V> {

  /** 값을 넣는다 (있던 값은 대체되고 TTL도 새로 시작한다). */
  void put(String key, V value, Duration ttl);

  /** 살아 있는 값을 읽는다 (지우지 않음). */
  Optional<V> get(String key);

  /** 살아 있는 값을 꺼내며 지운다. 같은 키를 동시에 꺼내도 한 쪽만 값을 받는다 (1회용). */
  Optional<V> take(String key);

  /** 지운다. */
  void remove(String key);

  /** 지금 값이 {@code expected}와 같을 때만 지운다 (원자적). 지웠으면 true. */
  boolean remove(String key, V expected);

  /**
   * 값을 원자적으로 바꾼다. {@code remap}은 지금 값(없거나 만료됐으면 null)을 받아 새 값을 돌려주고, null을 돌려주면 지운다. 새 값의 TTL은
   * {@code ttl}로 새로 시작한다. 구현에 따라 경합 시 {@code remap}이 여러 번 불릴 수 있으니 부수 효과 없이 쓴다.
   *
   * @return 바뀐 값 (지웠으면 비어 있음)
   */
  Optional<V> compute(String key, UnaryOperator<V> remap, Duration ttl);
}
