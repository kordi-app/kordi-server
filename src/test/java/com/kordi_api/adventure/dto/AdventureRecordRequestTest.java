package com.kordi_api.adventure.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kordi_api.global.exception.CoreException;
import com.kordi_api.global.exception.CoreExceptionCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdventureRecordRequestTest {

  @ParameterizedTest
  @CsvSource({
    "0, 1", // 한 마리도 못 잡고 끝난 판
    "10, 15000", // 마리당 정확히 1.5초
    "1000, 10800000" // 최대 수, 최대 시간
  })
  void 범위_안의_그럴듯한_기록은_통과한다(int defeated, long durationMs) {
    assertThatCode(() -> new AdventureRecordRequest(defeated, durationMs).validate())
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @CsvSource({
    "-1, 60000", // 음수
    "1001, 10800000", // 최대 수 초과
    "0, 0", // 시간 0
    "0, -1", // 음수 시간
    "10, 10800001", // 3시간 초과
    "10, 14999" // 마리당 1.5초보다 빠름
  })
  void 범위를_벗어나거나_너무_빠른_기록은_거절한다(int defeated, long durationMs) {
    assertThatThrownBy(() -> new AdventureRecordRequest(defeated, durationMs).validate())
        .isInstanceOf(CoreException.class)
        .extracting("errorCode")
        .isEqualTo(CoreExceptionCode.INVALID_REQUEST);
  }

  @Test
  void 시간이_극단적으로_커도_오버플로_없이_검사한다() {
    assertThatThrownBy(() -> new AdventureRecordRequest(1000, Long.MAX_VALUE).validate())
        .isInstanceOf(CoreException.class);
  }
}
