package org.biopulse.dao;

import java.time.Instant;
import java.util.*;
import org.biopulse.domain.*;

public interface SimulationDao {
  SimulationSession create(
      long admission,
      long recording,
      long output,
      double speed,
      Instant start,
      ScenarioSpec scenario);

  Optional<SimulationSession> get(long id, boolean lock);

  List<SimulationSession> list(String actor, boolean admin, int limit, int offset);

  List<SimulationSession> list(
      String actor, boolean admin, int limit, int offset, Long admissionId);

  List<Long> running();

  boolean state(long id, SimulatorState expected, SimulatorState next);

  void cursor(long id, Instant at, SimulatorState state);

  List<Long> recordedDeviceIds(long recordingId);
}
