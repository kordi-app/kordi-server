package com.kordi_api.adventure.repository;

import com.kordi_api.adventure.entity.AdventureRecord;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdventureRecordRepository extends JpaRepository<AdventureRecord, Long> {

  /** 기록 갱신용: 같은 유저의 동시 제출이 서로의 갱신을 덮어쓰지 않도록 행을 잠근다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from AdventureRecord r where r.user.id = :userId")
  Optional<AdventureRecord> findForUpdate(@Param("userId") Long userId);

  Optional<AdventureRecord> findByUserId(Long userId);

  @EntityGraph(attributePaths = "user")
  List<AdventureRecord> findAllByOrderByDefeatedDescAchievedAtAsc(Limit limit);

  /** 이 기록보다 위에 있는 다른 사람 수: 더 많이 물리쳤거나, 같은데 먼저 냈다 (랭킹 목록과 같은 기준). +1 하면 순위 */
  @Query(
      """
      select count(r) from AdventureRecord r
      where r.user.id <> :userId
        and (r.defeated > :defeated or (r.defeated = :defeated and r.achievedAt < :at))
      """)
  long countAhead(
      @Param("userId") Long userId, @Param("defeated") int defeated, @Param("at") LocalDateTime at);
}
