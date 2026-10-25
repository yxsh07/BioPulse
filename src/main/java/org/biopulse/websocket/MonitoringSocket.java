package org.biopulse.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.biopulse.security.AccessPolicy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * One authorized admission per connection. Reconnect by opening a fresh socket and refreshing REST
 * state.
 */
@Component
public class MonitoringSocket extends TextWebSocketHandler {
  private record Client(WebSocketSession session, long admission) {}

  private final Map<String, Client> clients = new ConcurrentHashMap<>();
  private final AccessPolicy access;
  private final ObjectMapper json;

  public MonitoringSocket(AccessPolicy access, ObjectMapper json) {
    this.access = access;
    this.json = json;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
    String query = raw.getUri().getQuery();
    long id;
    try {
      id = Long.parseLong(query.replaceFirst("^admissionId=", ""));
    } catch (Exception e) {
      raw.close(CloseStatus.BAD_DATA);
      return;
    }
    if (clients.size() >= 256
        || !authenticated(raw)
        || !access.canAdmission(raw.getPrincipal().getName(), id)) {
      raw.close(CloseStatus.POLICY_VIOLATION);
      return;
    }
    clients.put(
        raw.getId(), new Client(new ConcurrentWebSocketSessionDecorator(raw, 3000, 65536), id));
    raw.sendMessage(new TextMessage("{\"type\":\"CONNECTED\",\"resyncRequired\":true}"));
  }

  @Override
  public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
    clients.remove(s.getId());
  }

  @Override
  protected void handleTextMessage(WebSocketSession s, TextMessage m) throws Exception {
    if (!authenticated(s)) {
      s.close(CloseStatus.POLICY_VIOLATION);
      return;
    }
    if (m.getPayload().equals("ping")) s.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
    else s.close(CloseStatus.NOT_ACCEPTABLE);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void event(MonitoringEvent event) {
    try {
      String serialized = json.writeValueAsString(event);
      for (var entry : clients.entrySet()) {
        Client c = entry.getValue();
        if (!Objects.equals(event.admissionId(), c.admission())) continue;
        try {
          if (!authenticated(c.session())
              || !access.canAdmission(c.session().getPrincipal().getName(), c.admission())) {
            c.session().close(CloseStatus.POLICY_VIOLATION);
            clients.remove(entry.getKey());
            continue;
          }
          c.session().sendMessage(new TextMessage(serialized));
        } catch (Exception e) {
          clients.remove(entry.getKey());
          try {
            c.session().close(CloseStatus.SESSION_NOT_RELIABLE);
          } catch (Exception ignored) {
          }
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Scheduled(fixedDelay = 25000)
  public void heartbeat() {
    for (var entry : clients.entrySet()) {
      try {
        if (!authenticated(entry.getValue().session())) {
          entry.getValue().session().close(CloseStatus.POLICY_VIOLATION);
          clients.remove(entry.getKey());
          continue;
        }
        entry.getValue().session().sendMessage(new PingMessage());
      } catch (Exception e) {
        clients.remove(entry.getKey());
      }
    }
  }

  private boolean authenticated(WebSocketSession socket) {
    if (socket.getPrincipal() == null) return false;
    Object value = socket.getAttributes().get("httpSession");
    if (!(value instanceof jakarta.servlet.http.HttpSession session)) return false;
    try {
      var context =
          session.getAttribute(
              org.springframework.security.web.context.HttpSessionSecurityContextRepository
                  .SPRING_SECURITY_CONTEXT_KEY);
      int timeout = session.getMaxInactiveInterval();
      return context instanceof org.springframework.security.core.context.SecurityContext security
          && security.getAuthentication() != null
          && socket.getPrincipal().getName().equals(security.getAuthentication().getName())
          && (timeout <= 0
              || System.currentTimeMillis() - session.getLastAccessedTime() < timeout * 1000L);
    } catch (IllegalStateException invalidated) {
      return false;
    }
  }
}
