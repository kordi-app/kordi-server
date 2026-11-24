package com.kordi_api.adventure.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kordi_api.adventure.dto.AdventureRecordRequest.Verdict;
import com.kordi_api.global.exception.CoreException;
import org.junit.jupiter.api.Test;

class AdventureRecordRequestTest {

  @Test
  void 서버가_잰_시간에_맞는_기록은_통과한다() {
    // 10마리 × 5초: 사람이 내기 좋은 속도
    assertThat(new AdventureRecordRequest("r", 10).validate(50_000)).isEqualTo(Verdict.PLAUSIBLE);
    assertThat(new AdventureRecordRequest("r", 0).validate(1)).isEqualTo(Verdict.PLAUSIBLE);
  }

  @Test
  void 걸린_시간에_비해_너무_많이_물리치면_거절한다() {
    // 시작 연출 1.2초 + 한 마리 최소 2.5초: 25초에 10마리는 불가능 (26.2초부터 가능)
    assertThatThrownBy(() -> new AdventureRecordRequest("r", 10).validate(25_000))
        .isInstanceOf(CoreException.class);
    assertThat(new AdventureRecordRequest("r", 10).validate(26_200)).isEqualTo(Verdict.SUSPICIOUS);
  }

  @Test
  void 가능하지만_아주_빠르거나_아주_많으면_의심_기록으로_표시한다() {
    // 평균 3.5초보다 빠름
    assertThat(new AdventureRecordRequest("r", 10).validate(30_000)).isEqualTo(Verdict.SUSPICIOUS);
    assertThat(new AdventureRecordRequest("r", 10).validate(36_200)).isEqualTo(Verdict.PLAUSIBLE);
    // 넉넉한 시간이어도 150마리를 넘으면
    assertThat(new AdventureRecordRequest("r", 151).validate(3_600_000))
        .isEqualTo(Verdict.SUSPICIOUS);
    assertThat(new AdventureRecordRequest("r", 150).validate(3_600_000))
        .isEqualTo(Verdict.PLAUSIBLE);
  }

  @Test
  void 범위를_벗어나면_거절한다() {
    assertThatThrownBy(() -> new AdventureRecordRequest("r", -1).validate(10_000))
        .isInstanceOf(CoreException.class);
    assertThatThrownBy(
            () ->
                new AdventureRecordRequest("r", AdventureRecordRequest.MAX_DEFEATED + 1)
                    .validate(Long.MAX_VALUE / 4))
        .isInstanceOf(CoreException.class);
  }
}
