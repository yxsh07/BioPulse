package org.biopulse.config;

import org.biopulse.websocket.MonitoringSocket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
  private final MonitoringSocket handler;
  private final String[] origins;
  private final org.biopulse.security.SessionHandshakeInterceptor sessions;

  public WebSocketConfig(
      MonitoringSocket handler,
      @Value("${biopulse.allowed-origins}") String origins,
      org.biopulse.security.SessionHandshakeInterceptor sessions) {
    this.handler = handler;
    this.origins = origins.split(",");
    this.sessions = sessions;
  }

  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(handler, "/ws/monitoring")
        .addInterceptors(sessions)
        .setAllowedOrigins(origins);
  }
}
