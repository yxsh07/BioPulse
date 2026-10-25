package org.biopulse.service;

import org.biopulse.dao.AccountDao;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService implements UserDetailsService {
  private final AccountDao accounts;

  public AuthenticationService(AccountDao accounts) {
    this.accounts = accounts;
  }

  public UserDetails loadUserByUsername(String username) {
    var account =
        accounts
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    return User.withUsername(account.username())
        .password(account.passwordHash())
        .roles(account.role().name())
        .disabled(!account.enabled())
        .build();
  }
}
