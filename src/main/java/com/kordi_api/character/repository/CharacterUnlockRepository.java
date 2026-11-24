package com.kordi_api.character.repository;

import com.kordi_api.character.entity.CharacterUnlock;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CharacterUnlockRepository extends JpaRepository<CharacterUnlock, Long> {

  boolean existsByUserIdAndItem(Long userId, String item);

  @Query("select u.item from CharacterUnlock u where u.user.id = :userId")
  List<String> findItemsByUserId(@Param("userId") Long userId);
}
