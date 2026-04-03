package com.kordi_api.auth.repository;

import com.kordi_api.auth.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByToken(String token);

  Optional<RefreshToken> findByUserId(Long userId);

  @Modifying
  @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
  void deleteAllByUserId(@Param("userId") Long userId);

  @Modifying
  @Query("DELETE FROM RefreshToken rt WHERE rt.expirAt < :now")
  void deleteExpiredTokens(@Param("now") LocalDateTime now);
}
