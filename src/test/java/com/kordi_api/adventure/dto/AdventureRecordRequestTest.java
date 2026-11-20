package com.kordi_api.adventure.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kordi_api.global.exception.CoreException;
import org.junit.jupiter.api.Test;

class AdventureRecordRequestTest {

  @Test
  void 서버가_잰_시간에_맞는_기록은_통과한다() {
    assertThatCode(() -> new AdventureRecordRequest("r", 10).validate(20_000))
        .doesNotThrowAnyException();
    assertThatCode(() -> new AdventureRecordRequest("r", 0).validate(1)).doesNotThrowAnyException();
  }

  @Test
  void 걸린_시간에_비해_너무_많이_물리치면_거절한다() {
    // 2초에 한 마리가 최소: 10초에 6마리는 불가능
    assertThatThrownBy(() -> new AdventureRecordRequest("r", 6).validate(10_000))
        .isInstanceOf(CoreException.class);
  }

  @Test
  void 범위를_벗어나면_거절한다() {
    assertThatThrownBy(() -> new AdventureRecordRequest("r", -1).validate(10_000))
        .isInstanceOf(CoreException.class);
    assertThatThrownBy(() -> new AdventureRecordRequest("r", 1001).validate(Long.MAX_VALUE))
        .isInstanceOf(CoreException.class);
  }
}
