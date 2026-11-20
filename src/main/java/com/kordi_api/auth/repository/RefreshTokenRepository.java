package com.kordi_api.auth.repository;

import com.kordi_api.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  /**
   * 재발급용 조회. 같은 토큰으로 동시에 들어온 요청을 직렬화하기 위해 행 잠금을 건다. 뒤늦게 잠금을 얻은 요청은 이미 회전된 행을 보게 되어 empty를 받고,
   * {@link #findByPreviousToken}으로 넘어간다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.token = :token")
  Optional<RefreshToken> findByTokenForUpdate(@Param("token") String token);

  @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.previousToken = :token")
  Optional<RefreshToken> findByPreviousToken(@Param("token") String token);

  Optional<RefreshToken> findByUserId(Long userId);

  @Modifying
  @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
  void deleteByUserId(@Param("userId") Long userId);

  @Modifying
  @Query("DELETE FROM RefreshToken rt WHERE rt.token = :token")
  void deleteByToken(@Param("token") String token);
}
