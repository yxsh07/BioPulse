package org.biopulse.dao;

import java.time.Instant;
import java.util.*;
import org.biopulse.domain.*;

public interface SimulationDao {
  SimulationSession create(
      long admission, long recording, long output, double speed, Instant start);

  Optional<SimulationSession> get(long id, boolean lock);

  List<Long> running();

  boolean state(long id, SimulatorState expected, SimulatorState next);

  void cursor(long id, Instant at, SimulatorState state);
}
