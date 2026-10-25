package org.biopulse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.biopulse.dao.AccountDao;
import org.biopulse.domain.Role;
import org.biopulse.domain.User;
import org.biopulse.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class AuthenticationServiceTest {
  @Test
  void mapsSharedAccountRoleAndDisabledStatus() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("staff"))
        .thenReturn(Optional.of(new User(7, "staff", "test-hash", Role.DOCTOR, false)));
    var principal = new AuthenticationService(dao).loadUserByUsername("staff");
    assertFalse(principal.isEnabled());
    assertEquals("ROLE_DOCTOR", principal.getAuthorities().iterator().next().getAuthority());
    assertEquals("test-hash", principal.getPassword());
  }

  @Test
  void absentAccountCannotAuthenticate() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("unknown")).thenReturn(Optional.empty());
    assertThrows(
        UsernameNotFoundException.class,
        () -> new AuthenticationService(dao).loadUserByUsername("unknown"));
  }
}
