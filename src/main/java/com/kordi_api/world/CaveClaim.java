package com.kordi_api.world;

/**
 * 제단의 목걸이를 가져가겠다는 알림({@code {"t":"cave"}})에 대한 서버의 답. 클라이언트에는 {@code
 * {"t":"cave","ok":...,"reason":...}}로 간다 ({@link #reason()}).
 *
 * @param retryable 잠시 뒤 다시 물어볼 만한가 (위치가 한 박자 늦게 왔거나, 막 다시 연결해 아직 머문 시간이 모자라거나)
 */
public enum CaveClaim {
  /** 줬다 (이미 가지고 있었어도) */
  OK("ok", false),
  /** 서버가 아는 위치가 아직 제단 위가 아니다 (위치는 초당 10번 오므로 막 올라선 순간엔 한 칸 아래일 수 있다) */
  NOT_ON_ALTAR("altar", true),
  /** 이 연결로 동굴에 머문 시간이 모자라다 */
  TOO_SOON("stay", true),
  /** 비회원: 목걸이는 브라우저에 남는다 */
  GUEST("guest", false),
  /** 동굴에 있지 않다 */
  NOT_IN_CAVE("region", false),
  /** 우주의 왕관을 쓰고 있지 않다 */
  NO_CROWN("crown", false),
  /** 사람이 낼 수 없는 움직임(순간이동, 비행)이 있었다 */
  MOVEMENT("movement", false),
  /** 끊어진 다리 건너편을 지나오지 않았다 */
  PATH("path", false),
  /** 저장하다 실패했다 */
  ERROR("error", true);

  private final String reason;
  private final boolean retryable;

  CaveClaim(String reason, boolean retryable) {
    this.reason = reason;
    this.retryable = retryable;
  }

  public String reason() {
    return reason;
  }

  public boolean retryable() {
    return retryable;
  }
}
