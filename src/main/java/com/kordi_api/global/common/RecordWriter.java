package com.kordi_api.global.common;

import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * "한 유저당 한 줄" 기록(코스·모험 최고 기록, 캐릭터 모습)을 넣거나 갱신하는 쓰기.
 *
 * <p>READ COMMITTED로 실행한다. MySQL 기본(REPEATABLE READ)에서는 없는 행을 {@code SELECT … FOR UPDATE}하면 갭 락이
 * 걸려, 두 유저가 동시에 첫 기록을 넣으면 서로의 갭 락을 기다리다 교착(deadlock)에 빠진다. READ COMMITTED에는 갭 락이 없어 두 번째 INSERT는
 * 유니크 제약 위반으로 끝나고, 그때 한 번 더 실행하면 이번엔 생긴 행을 잠그고 갱신하는 경로로 간다. 교착이 나더라도(락 실패) 같은 방식으로 한 번 재시도한다.
 *
 * <p>재시도는 새 트랜잭션이어야 하므로 서비스 메서드에 {@code @Transactional}을 붙이지 말고 이 안에서 작업을 실행한다.
 */
@Component
public class RecordWriter {

  private final TransactionTemplate transaction;

  public RecordWriter(PlatformTransactionManager transactionManager) {
    this.transaction = new TransactionTemplate(transactionManager);
    this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public <T> T write(Supplier<T> work) {
    try {
      return transaction.execute(status -> work.get());
    } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
      return transaction.execute(status -> work.get());
    }
  }
}
