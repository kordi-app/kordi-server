package com.kordi_api.world;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 소켓 하나의 보낼 메시지 줄. 보내는 쪽(이동 묶음 타이머, 다른 사람의 입·퇴장 등)은 줄에 넣기만 하고 바로 돌아가며, 실제 전송은 이 소켓 전용 작업(가상 스레드)이 한
 * 번에 하나씩 순서대로 한다. 그래서 느린 클라이언트 하나가 다른 사람에게 가는 전송이나 타이머를 붙잡지 못한다.
 *
 * <p>줄에 쌓인 양이 {@value #MAX_PENDING_CHARS}자를 넘거나 전송이 실패하면(전송 시간 초과 포함) 더 보내지 않고 {@code onFailure}로
 * 알린다. 핸들러가 소켓을 닫고 정리하며, 클라이언트는 다시 접속해 최신 상태를 새로 받는다. 이동 묶음은 "그 사이 움직인 사람"만 담은 변경분이라 오래된 것을 버리면
 * 아바타가 엉뚱한 자리에 멈춰 있게 되므로, 버리기보다 끊고 다시 받게 한다.
 */
final class WorldOutbox {

  /** 채널이 꽉 찼을 때의 이동 묶음 기준으로 1~2초 분량 */
  static final int MAX_PENDING_CHARS = 64 * 1024;

  private final WebSocketSession session;
  private final Executor executor;
  private final Consumer<CloseStatus> onFailure;
  private final Deque<String> queue = new ConcurrentLinkedDeque<>();
  private final AtomicInteger pendingChars = new AtomicInteger();
  private final AtomicBoolean draining = new AtomicBoolean();
  private final AtomicBoolean failed = new AtomicBoolean();

  /** 첫 메시지(welcome)가 줄 맨 앞에 들어가기 전까지는 쌓기만 한다 */
  private volatile boolean started;

  WorldOutbox(WebSocketSession session, Executor executor, Consumer<CloseStatus> onFailure) {
    this.session = session;
    this.executor = executor;
    this.onFailure = onFailure;
  }

  WebSocketSession session() {
    return session;
  }

  /**
   * 첫 메시지를 줄 맨 앞에 넣고 보내기 시작한다. 그 전에 쌓인 메시지(입장 직후 받은 다른 사람 소식)는 첫 메시지 뒤에 그대로 간다: 하나도 잃지 않고, 첫 메시지보다
   * 먼저 가지도 않는다.
   */
  void start(String first) {
    pendingChars.addAndGet(first.length());
    queue.addFirst(first);
    started = true;
    schedule();
  }

  void offer(String json) {
    if (failed.get()) {
      return;
    }
    if (pendingChars.addAndGet(json.length()) > MAX_PENDING_CHARS) {
      fail(CloseStatus.SESSION_NOT_RELIABLE.withReason("too slow"));
      return;
    }
    queue.addLast(json);
    schedule();
  }

  private void schedule() {
    if (!started || failed.get() || !draining.compareAndSet(false, true)) {
      return;
    }
    try {
      executor.execute(this::drain);
    } catch (RejectedExecutionException e) {
      draining.set(false);
      fail(CloseStatus.GOING_AWAY);
    }
  }

  private void drain() {
    try {
      String json;
      while (!failed.get() && (json = queue.pollFirst()) != null) {
        pendingChars.addAndGet(-json.length());
        if (!session.isOpen()) {
          fail(CloseStatus.SESSION_NOT_RELIABLE);
          return;
        }
        session.sendMessage(new TextMessage(json));
      }
    } catch (Exception e) {
      // IOException(전송 시간 초과·끊김), IllegalStateException 등 어떤 실패든 이 소켓만 정리한다
      fail(CloseStatus.SESSION_NOT_RELIABLE.withReason("send failed"));
      return;
    } finally {
      draining.set(false);
    }
    // 빠져나오는 사이에 새로 들어온 메시지가 있으면 다시 맡는다
    if (!queue.isEmpty()) {
      schedule();
    }
  }

  private void fail(CloseStatus status) {
    if (failed.compareAndSet(false, true)) {
      queue.clear();
      onFailure.accept(status);
    }
  }
}
