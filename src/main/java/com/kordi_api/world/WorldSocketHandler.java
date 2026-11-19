package com.kordi_api.world;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 3D 피아노 월드 실시간 동기화.
 *
 * <ul>
 *   <li>서버 → 클라이언트
 *       <ul>
 *         <li>{@code welcome}: 내 id, 지역·채널 번호, 같은 채널 접속자, 전체·지역별 접속자 수
 *         <li>{@code join} / {@code leave}: 같은 채널 입·퇴장 (즉시)
 *         <li>{@code moves}: 같은 채널에서 움직인 사람들의 최신 위치 ({@value #TICK_MS}ms마다 묶어서)
 *         <li>{@code online}: 전체·지역별 접속자 수 ({@value #ONLINE_MS}ms마다)
 *       </ul>
 *   <li>클라이언트 → 서버: {@link WorldMove} (x, z, h, m)
 * </ul>
 *
 * 인증 없이 누구나 입장한다(홈 화면이 공개 페이지). 이름은 표시용이며 검증하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorldSocketHandler extends TextWebSocketHandler {

  static final long TICK_MS = 100;
  static final long ONLINE_MS = 5_000;

  private static final String PLAYER_ID = "playerId";
  private static final int SEND_TIME_LIMIT_MS = 2_000;
  private static final int BUFFER_SIZE_LIMIT = 64 * 1024;
  private static final int MAX_INCOMING_LENGTH = 256;

  private final WorldLobby lobby;
  private final JsonMapper jsonMapper;
  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession rawSession) throws IOException {
    var seat = lobby.join(queryParam(rawSession, "region"), queryParam(rawSession, "name"));
    if (seat.isEmpty()) {
      // 전체 정원 초과: 클라이언트는 이 코드로 혼자 모드 전환
      rawSession.close(CloseStatus.SERVICE_OVERLOAD.withReason("full"));
      return;
    }
    WorldPlayer me = seat.get().player();
    WorldLobby.ChannelKey channel = seat.get().channel();

    WebSocketSession session =
        new ConcurrentWebSocketSessionDecorator(rawSession, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
    rawSession.getAttributes().put(PLAYER_ID, me.id());
    sessions.put(me.id(), session);

    send(
        session,
        Map.of(
            "t", "welcome",
            "id", me.id(),
            "region", channel.region(),
            "ch", channel.number(),
            "players", List.copyOf(lobby.playersIn(channel)),
            "online", lobby.totalPlayers(),
            "regions", lobby.playersPerRegion()));
    broadcastToChannel(channel, Map.of("t", "join", "p", me), me.id());
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    String id = (String) session.getAttributes().get(PLAYER_ID);
    if (id == null || message.getPayloadLength() > MAX_INCOMING_LENGTH) {
      return;
    }
    try {
      lobby.move(id, jsonMapper.readValue(message.getPayload(), WorldMove.class), now());
    } catch (JacksonException e) {
      // 형식이 틀린 메시지는 무시
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String id = (String) session.getAttributes().get(PLAYER_ID);
    if (id == null) {
      return;
    }
    sessions.remove(id);
    lobby
        .leave(id)
        .ifPresent(channel -> broadcastToChannel(channel, Map.of("t", "leave", "id", id), id));
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.debug("[World] transport error: {}", exception.getMessage());
  }

  /** 채널마다 움직인 사람들을 한 메시지로 묶어 보낸다. 받는 쪽은 자기 id를 무시한다. */
  @Scheduled(fixedRate = TICK_MS)
  void flushMoves() {
    for (WorldLobby.ChannelKey channel : lobby.channelNumbers()) {
      List<WorldPlayer> moved = lobby.drainMoves(channel);
      if (!moved.isEmpty()) {
        broadcastToChannel(channel, Map.of("t", "moves", "ps", moved), null);
      }
    }
  }

  @Scheduled(fixedRate = ONLINE_MS)
  void broadcastOnlineCount() {
    if (sessions.isEmpty()) {
      return;
    }
    String json =
        jsonMapper.writeValueAsString(
            Map.of("t", "online", "n", lobby.totalPlayers(), "regions", lobby.playersPerRegion()));
    sessions.values().forEach(session -> sendRaw(session, json));
  }

  private void broadcastToChannel(WorldLobby.ChannelKey channel, Object payload, String exceptId) {
    String json = jsonMapper.writeValueAsString(payload);
    for (WorldPlayer player : lobby.playersIn(channel)) {
      if (player.id().equals(exceptId)) {
        continue;
      }
      WebSocketSession session = sessions.get(player.id());
      if (session != null) {
        sendRaw(session, json);
      }
    }
  }

  private void send(WebSocketSession session, Object payload) {
    sendRaw(session, jsonMapper.writeValueAsString(payload));
  }

  private void sendRaw(WebSocketSession session, String json) {
    if (!session.isOpen()) {
      return;
    }
    try {
      session.sendMessage(new TextMessage(json));
    } catch (IOException | IllegalStateException e) {
      // 느리거나 끊긴 클라이언트: 버퍼 초과 시 데코레이터가 세션을 닫고, 정리는 afterConnectionClosed가 맡는다
      log.debug("[World] send failed: {}", e.getMessage());
    }
  }

  private static long now() {
    return System.currentTimeMillis();
  }

  private static String queryParam(WebSocketSession session, String key) {
    if (session.getUri() == null) {
      return null;
    }
    String raw =
        UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst(key);
    if (raw == null) {
      return null;
    }
    try {
      return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return null; // 깨진 인코딩이면 기본값
    }
  }
}
