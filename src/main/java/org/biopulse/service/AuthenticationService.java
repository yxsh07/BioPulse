package org.biopulse.service;
import org.biopulse.jdbc.Sql;import org.springframework.stereotype.Service;import org.springframework.security.core.userdetails.*;
@Service
public class AuthenticationService implements UserDetailsService {
 private final Sql sql; public AuthenticationService(Sql sql){this.sql=sql;}
 public UserDetails loadUserByUsername(String username){return sql.query("SELECT * FROM users WHERE username=?",r->User.withUsername(r.getString("username")).password(r.getString("password_hash")).roles(r.getString("role")).disabled(!r.getBoolean("enabled")).build(),username).stream().findFirst().orElseThrow(()->new UsernameNotFoundException("Invalid credentials"));}
}