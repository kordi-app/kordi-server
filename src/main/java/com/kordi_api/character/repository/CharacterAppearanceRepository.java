package com.kordi_api.character.repository;

import com.kordi_api.character.entity.CharacterAppearance;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterAppearanceRepository extends JpaRepository<CharacterAppearance, Long> {

  Optional<CharacterAppearance> findByUserId(Long userId);
}
