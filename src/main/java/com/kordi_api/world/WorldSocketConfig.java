package com.kordi_api.world;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableScheduling
@RequiredArgsConstructor
public class WorldSocketConfig implements WebSocketConfigurer {

  private final WorldSocketHandler worldSocketHandler;

  // REST CORS와 같은 허용 출처만 WebSocket 핸드셰이크를 허용
  @Value("${app.cors.allowed-origins}")
  private List<String> allowedOrigins;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(worldSocketHandler, "/ws/world")
        .setAllowedOrigins(allowedOrigins.stream().map(String::trim).toArray(String[]::new));
  }
}
