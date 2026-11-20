package com.kordi_api.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.kordi_api.course.service.CourseRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** DB 없이 WebSocket 핸들러가 Boot 기본 JsonMapper로 조립되는지만 확인한다. */
class WorldSocketWiringTest {

  @Test
  void 핸들러가_JsonMapper와_로비를_주입받아_생성된다() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withBean(WorldLobby.class)
        .withBean(CourseTimer.class)
        .withBean(WorldTicketService.class)
        .withBean(CourseRecordService.class, () -> mock(CourseRecordService.class))
        .withBean(WorldSocketHandler.class)
        .run(context -> assertThat(context).hasNotFailed().hasSingleBean(WorldSocketHandler.class));
  }
}
