package org.biopulse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.security.Principal;
import java.util.HashMap;
import org.biopulse.security.AccessPolicy;
import org.biopulse.websocket.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.socket.*;

class MonitoringSocketTest {
  @Test
  void invalidatedLoginStopsStreaming() throws Exception {
    var access = mock(AccessPolicy.class);
    when(access.canAdmission("staff", 1)).thenReturn(true);
    var session = new MockHttpSession();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated("staff", null, java.util.List.of()));
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    var socket = mock(WebSocketSession.class);
    when(socket.getId()).thenReturn("one");
    when(socket.getUri()).thenReturn(URI.create("ws://localhost/ws/monitoring?admissionId=1"));
    when(socket.getPrincipal()).thenReturn((Principal) () -> "staff");
    when(socket.getAttributes())
        .thenReturn(new HashMap<>(java.util.Map.of("httpSession", session)));
    var handler = new MonitoringSocket(access, new ObjectMapper().findAndRegisterModules());
    handler.afterConnectionEstablished(socket);
    session.invalidate();
    handler.event(MonitoringEvent.of("READING", 1L, null, null, null, null, null, "fixture"));
    verify(socket).close(CloseStatus.POLICY_VIOLATION);
  }

  @Test
  void missingLoginClosesConnection() throws Exception {
    var access = mock(AccessPolicy.class);
    var socket = mock(WebSocketSession.class);
    when(socket.getUri()).thenReturn(URI.create("ws://localhost/ws/monitoring?admissionId=1"));
    when(socket.getAttributes()).thenReturn(new HashMap<>());
    new MonitoringSocket(access, new ObjectMapper()).afterConnectionEstablished(socket);
    verify(socket).close(CloseStatus.POLICY_VIOLATION);
  }
}
