package com.kordi_api.global.store;

/** {@link ExpiringStore}를 만든다. 구현(메모리, Redis 등)은 이 빈을 바꿔 끼워 정한다. */
public interface ExpiringStoreFactory {

  /**
   * @param name 보관소 이름. 서로 다른 보관소의 키가 섞이지 않게 하는 이름공간이다 (원격 구현이면 키 접두사).
   * @param type 값의 타입. 원격 구현이 값을 직렬화·역직렬화할 때 쓴다.
   */
  <V> ExpiringStore<V> create(String name, Class<V> type);
}
