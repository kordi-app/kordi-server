package com.kordi_api.character.repository;

import com.kordi_api.character.entity.CharacterAppearance;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CharacterAppearanceRepository extends JpaRepository<CharacterAppearance, Long> {

  Optional<CharacterAppearance> findByUserId(Long userId);

  /** 고치기 전에 행을 잠근다: 꾸미기 저장·코스 보상·펫이 동시에 오면 각자 읽은 옛 값으로 다른 칸을 덮어쓰지 않도록 (모든 칸을 다시 쓰므로). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from CharacterAppearance a where a.user.id = :userId")
  Optional<CharacterAppearance> findForUpdate(@Param("userId") Long userId);
}
