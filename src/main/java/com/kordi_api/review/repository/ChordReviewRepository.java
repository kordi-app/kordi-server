package com.kordi_api.review.repository;

import com.kordi_api.review.entity.ChordReview;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChordReviewRepository extends JpaRepository<ChordReview, Long> {

  /** 결과 반영용: 같은 유저의 동시 제출이 서로의 갱신을 덮어쓰지 않도록 행을 잠근다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from ChordReview r where r.user.id = :userId and r.chord.id in :chordIds")
  List<ChordReview> findForUpdate(
      @Param("userId") Long userId, @Param("chordIds") Collection<Long> chordIds);

  long countByUserIdAndDueAtLessThanEqual(Long userId, Instant now);
}
