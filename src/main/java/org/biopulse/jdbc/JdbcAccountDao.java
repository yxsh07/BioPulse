package org.biopulse.jdbc;

import java.util.Optional;
import org.biopulse.dao.AccountDao;
import org.biopulse.domain.Role;
import org.biopulse.domain.User;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccountDao implements AccountDao {
  private final Sql sql;

  public JdbcAccountDao(Sql sql) {
    this.sql = sql;
  }

  public Optional<User> findByUsername(String username) {
    return sql
        .query(
            "SELECT id,username,password_hash,role,enabled FROM users WHERE username=?",
            r ->
                new User(
                    r.getLong("id"),
                    r.getString("username"),
                    r.getString("password_hash"),
                    Role.valueOf(r.getString("role")),
                    r.getBoolean("enabled")),
            username)
        .stream()
        .findFirst();
  }
}
