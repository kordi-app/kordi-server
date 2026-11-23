package com.kordi_api.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.kordi_api.course.entity.Course;
import org.junit.jupiter.api.Test;

class CourseTimerTest {

  private final CourseTimer timer = new CourseTimer();

  @Test
  void 바닥에서_골까지_정상적으로_오르면_서버가_잰_시간으로_완주한다() {
    timer.track("a", "clouds", -6, 0, 2.6, 0);
    var none = timer.track("a", "clouds", -5.5, 0.8, 0, 300); // 첫 발판: 시작
    assertThat(none).isEmpty();
    long t = 300;
    double y = 0.8;
    while (y < 19.0 - 0.3) {
      t += 400;
      y += 0.3;
      assertThat(timer.track("a", "clouds", -5.5, y, 0, t)).isEmpty();
    }
    var finish = timer.track("a", "clouds", -5.5, 19.0, 0, t + 400);
    assertThat(finish).contains(new CourseTimer.Finish(Course.CLOUDS, t + 400 - 300));
  }

  @Test
  void 점프_중_중간_높이_샘플이_찍혀도_시작한다() {
    timer.track("a", "clouds", -6, 0, 2.6, 0);
    timer.track("a", "clouds", -5.8, 0.4, 1.5, 100); // 0.3~0.5 사이
    timer.track("a", "clouds", -5.5, 0.8, 0, 300); // 발판: 여기서 시작해야 한다
    long t = 300;
    double y = 0.8;
    while (y < 19.0 - 0.3) {
      t += 400;
      y += 0.3;
      timer.track("a", "clouds", -5.5, y, 0, t);
    }
    var finish = timer.track("a", "clouds", -5.5, 19.0, 0, t + 400);
    assertThat(finish).map(CourseTimer.Finish::millis).contains(t + 400 - 300);
  }

  @Test
  void 완주_뒤에는_바닥을_다시_밟아야_새로_시작한다() {
    timer.track("a", "clouds", -6, 0, 2.6, 0);
    timer.track("a", "clouds", -5.5, 0.8, 0, 300);
    long t = 300;
    double y = 0.8;
    while (y < 19.0 - 0.3) {
      t += 400;
      y += 0.3;
      timer.track("a", "clouds", -5.5, y, 0, t);
    }
    assertThat(timer.track("a", "clouds", -5.5, 19.0, 0, t + 400)).isPresent();
    // 바닥을 밟지 않고 골 높이에 계속 머무르면 다시 완주로 치지 않는다
    assertThat(timer.track("a", "clouds", -5.5, 19.0, 0, t + 800)).isEmpty();
  }

  @Test
  void 순간이동하면_그_시도는_완주로_치지_않는다() {
    timer.track("a", "clouds", -6, 0, 2.6, 0);
    timer.track("a", "clouds", -5.5, 0.8, 0, 300);
    assertThat(timer.track("a", "clouds", -5.5, 19.0, 0, 20_000))
        .isEmpty(); // 20초 공백 뒤 한 번에 18 상승: 순간이동
  }

  @Test
  void 최소_시간보다_빠르면_버린다() {
    timer.track("a", "night", -6, 0, 2.6, 0);
    timer.track("a", "night", -5.5, 0.8, 0, 100);
    long t = 100;
    double y = 0.8;
    // 초당 18씩 상승: 속도 검사는 통과하지만 1초 만에 완주는 사람이 못 낸다
    while (y < 17.3) {
      t += 100;
      y = Math.min(17.3, y + 1.8);
      if (y < 17.3) assertThat(timer.track("a", "night", -5.5, y, 0, t)).isEmpty();
    }
    assertThat(timer.track("a", "night", -5.5, 17.3, 0, t)).isEmpty();
  }

  @Test
  void 바닥으로_떨어지면_처음부터_다시_잰다() {
    timer.track("a", "clouds", -6, 0, 2.6, 0);
    timer.track("a", "clouds", -5.5, 0.8, 0, 300);
    timer.track("a", "clouds", -5.5, 0, 0, 600); // 떨어짐
    timer.track("a", "clouds", -5.5, 0.8, 0, 5_000); // 다시 시작
    long t = 5_000;
    double y = 0.8;
    while (y < 19.0 - 0.3) {
      t += 400;
      y += 0.3;
      timer.track("a", "clouds", -5.5, y, 0, t);
    }
    var finish = timer.track("a", "clouds", -5.5, 19.0, 0, t + 400);
    assertThat(finish).map(CourseTimer.Finish::millis).contains(t + 400 - 5_000);
  }

  @Test
  void 코스가_아닌_지역은_재지_않는다() {
    timer.track("a", "hall", 0, 0, 0, 0);
    assertThat(timer.track("a", "hall", 0, 19.0, 0, 60_000)).isEmpty();
  }

  @Test
  void 공중에_떠서_빠르게_솟아오르면_무효다() {
    timer.track("a", "clouds", -6, 0, 2.6, 0);
    timer.track("a", "clouds", -5.5, 0.8, 0, 100);
    long t = 100;
    double y = 0.8;
    // 30ms마다 1.5씩: 초당 50 상승 — 예산이 바로 바닥난다 (최소 시간을 넘겨 기다렸다 올라가도)
    t += 20_000;
    timer.track("a", "clouds", -5.5, y, 0, t);
    while (y < 19.0) {
      t += 30;
      y = Math.min(19.0, y + 1.5);
      var finish = timer.track("a", "clouds", -5.5, y, 0, t);
      if (y >= 19.0) assertThat(finish).isEmpty();
    }
  }

  @Test
  void 코스_영역을_벗어나면_무효다() {
    timer.track("a", "clouds", -6, 0, 2.6, 0);
    timer.track("a", "clouds", -5.5, 0.8, 0, 300);
    long t = 300;
    double y = 0.8;
    while (y < 19.0 - 0.3) {
      t += 400;
      y += 0.3;
      timer.track("a", "clouds", y > 5 && y < 6 ? -14 : -5.5, y, 0, t); // 잠깐 코스 밖
    }
    assertThat(timer.track("a", "clouds", -5.5, 19.0, 0, t + 400)).isEmpty();
  }
}
