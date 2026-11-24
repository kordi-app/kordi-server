package com.kordi_api.review.entity;

import java.time.Duration;
import java.time.Instant;

/**
 * 코드 복습 일정 (라이트너 상자). 상자는 1~{@link #MAX_BOX}이고, 맞히면 한 칸 올라가 다음 복습까지의 간격이 길어지고, 틀리면 1번 상자로 돌아가 바로 복습
 * 대상이 된다.
 *
 * <ul>
 *   <li>1번 상자: 바로 (오늘의 복습에 나온다)
 *   <li>2번: 1일 · 3번: 3일 · 4번: 7일 · 5번: 21일 뒤
 * </ul>
 *
 * 처음 보는 코드를 맞히면 이미 아는 코드로 보고 3번 상자에서 시작한다 (내일 바로 나오지 않게).
 */
public final class LeitnerSchedule {

  /** 아직 한 번도 답하지 않은 카드의 상자 */
  public static final int NEW_BOX = 0;

  public static final int MAX_BOX = 5;

  /** 처음 보는 코드를 맞혔을 때 들어가는 상자 */
  static final int FIRST_CORRECT_BOX = 3;

  private static final Duration[] INTERVALS = {
    Duration.ZERO, // 0: 쓰지 않음 (새 카드)
    Duration.ZERO,
    Duration.ofDays(1),
    Duration.ofDays(3),
    Duration.ofDays(7),
    Duration.ofDays(21)
  };

  private LeitnerSchedule() {}

  /** 한 번 답한 뒤의 상자. */
  public static int nextBox(int box, boolean correct) {
    if (!correct) {
      return 1;
    }
    if (box == NEW_BOX) {
      return FIRST_CORRECT_BOX;
    }
    return Math.min(MAX_BOX, box + 1);
  }

  /** 이 상자에 들어간 카드의 다음 복습 시각. */
  public static Instant dueAt(int box, Instant answeredAt) {
    return answeredAt.plus(interval(box));
  }

  public static Duration interval(int box) {
    if (box < 1 || box > MAX_BOX) {
      throw new IllegalArgumentException("box " + box);
    }
    return INTERVALS[box];
  }
}
