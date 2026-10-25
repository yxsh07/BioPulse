package org.biopulse.dao;

import java.util.Optional;
import org.biopulse.domain.User;

public interface AccountDao {
  Optional<User> findByUsername(String username);
}
