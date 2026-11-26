package com.kordi_api.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.course.entity.Course;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WorldContractTest {

  private final WorldContract rules = WorldContract.load();

  @Test
  void 코스마다_규칙이_있고_코스_지역은_월드_지역이다() {
    assertThat(rules.course().courses().keySet())
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(Course.values()).map(Course::region).toList());
    assertThat(rules.regions()).containsAll(rules.course().courses().keySet());
  }

  @Test
  void 공이_있는_지역과_보상_지역은_월드_지역이다() {
    assertThat(rules.regions()).containsAll(rules.balls().regions());
    assertThat(rules.regions()).contains(WorldLobby.WHALE_REGION, WorldLobby.CAVE_REGION);
  }

  @Test
  void 처음부터_깨어_있는_워프석은_있는_워프석이다() {
    assertThat(rules.warpStones().stones()).containsAll(rules.warpStones().alwaysAwake());
    assertThat(rules.warpStones().alwaysAwake()).isNotEmpty();
  }

  @Test
  void 체크포인트는_제단보다_앞이다() {
    // 입구(남쪽, z가 크다)에서 제단(북쪽)으로 가는 길: 체크포인트는 그 사이 (다리 건너편 좁은 길)
    assertThat(rules.cave().checkpoint().z()).isGreaterThan(rules.cave().altar().z());
  }
}
