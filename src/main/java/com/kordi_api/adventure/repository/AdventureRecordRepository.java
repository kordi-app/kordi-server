package com.kordi_api.adventure.repository;

import com.kordi_api.adventure.entity.AdventureRecord;
import jakarta.persistence.LockModeType;
import java.time.Instant;
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

  /** 랭킹: 점수 높은 순, 같으면 먼저 낸 순. 점수제 이전 기록(0점)은 빠진다. */
  @EntityGraph(attributePaths = "user")
  List<AdventureRecord> findAllByScoreGreaterThanOrderByScoreDescAchievedAtAsc(
      int score, Limit limit);

  /**
   * 이 기록보다 위에 있는 다른 사람 수: 점수가 더 높거나, 같은데 먼저 냈다 (랭킹 목록과 같은 기준). +1 하면 순위. 랭킹에 없는 점수제 이전 기록(0점)은 세지
   * 않는다 (0점 판이 그 기록들 뒤로 밀리지 않게).
   */
  @Query(
      """
      select count(r) from AdventureRecord r
      where r.user.id <> :userId
        and r.score > 0
        and (r.score > :score or (r.score = :score and r.achievedAt < :at))
      """)
  long countAhead(@Param("userId") Long userId, @Param("score") int score, @Param("at") Instant at);
}
