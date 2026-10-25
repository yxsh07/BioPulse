package org.biopulse.config;
import org.biopulse.websocket.MonitoringSocket;import org.springframework.context.annotation.Configuration;import org.springframework.beans.factory.annotation.Value;import org.springframework.web.socket.config.annotation.*;
@Configuration @EnableWebSocket public class WebSocketConfig implements WebSocketConfigurer {
 private final MonitoringSocket handler;private final String[] origins;public WebSocketConfig(MonitoringSocket handler,@Value("${biopulse.allowed-origins}") String origins){this.handler=handler;this.origins=origins.split(",");}
 public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){registry.addHandler(handler,"/ws/monitoring").setAllowedOrigins(origins);}
}