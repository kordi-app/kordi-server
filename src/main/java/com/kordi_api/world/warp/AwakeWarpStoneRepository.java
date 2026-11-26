package com.kordi_api.world.warp;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AwakeWarpStoneRepository extends JpaRepository<AwakeWarpStone, Long> {

  @Query("select w.stone from AwakeWarpStone w where w.user.id = :userId")
  List<String> findStonesByUserId(@Param("userId") Long userId);
}
