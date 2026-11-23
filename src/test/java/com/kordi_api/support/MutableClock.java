package com.kordi_api.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 테스트가 직접 옮기는 시계. 0ms(epoch)에서 시작한다. */
public class MutableClock extends Clock {

  private volatile long millis;

  public void set(long millis) {
    this.millis = millis;
  }

  @Override
  public long millis() {
    return millis;
  }

  @Override
  public Instant instant() {
    return Instant.ofEpochMilli(millis);
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException();
  }
}
