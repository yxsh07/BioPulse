package org.biopulse.jdbc;

import java.time.*;
import java.util.*;
import org.biopulse.dao.SimulationDao;
import org.biopulse.domain.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSimulationDao implements SimulationDao {
  private final Sql sql;

  public JdbcSimulationDao(Sql sql) {
    this.sql = sql;
  }

  private final Sql.Mapper<SimulationSession> mapper =
      r ->
          new SimulationSession(
              r.getLong("id"),
              r.getLong("admission_id"),
              r.getLong("recording_id"),
              r.getLong("output_recording_id"),
              SimulatorState.valueOf(r.getString("state")),
              r.getDouble("speed"),
              Sql.instant(r, "cursor_at"));

  public SimulationSession create(long a, long r, long o, double speed, Instant at) {
    long id =
        sql.insert(
            "INSERT INTO"
                + " simulation_sessions(admission_id,recording_id,output_recording_id,state,speed,cursor_at,updated_at)"
                + " VALUES(?,?,?,'CREATED',?,?,?)",
            a,
            r,
            o,
            speed,
            at,
            Instant.now());
    return get(id, false).orElseThrow();
  }

  public Optional<SimulationSession> get(long id, boolean lock) {
    return sql
        .query(
            lock
                ? "SELECT * FROM simulation_sessions WHERE id=? FOR UPDATE"
                : "SELECT * FROM simulation_sessions WHERE id=?",
            mapper,
            id)
        .stream()
        .findFirst();
  }

  public List<Long> running() {
    return sql.query(
        "SELECT id FROM simulation_sessions WHERE state='RUNNING' ORDER BY id", r -> r.getLong(1));
  }

  public boolean state(long id, SimulatorState expected, SimulatorState next) {
    return sql.update(
            "UPDATE simulation_sessions SET state=?,updated_at=? WHERE id=? AND state=?",
            next,
            Instant.now(),
            id,
            expected)
        == 1;
  }

  public void cursor(long id, Instant at, SimulatorState state) {
    sql.update(
        "UPDATE simulation_sessions SET cursor_at=?,state=?,updated_at=? WHERE id=?",
        at,
        state,
        Instant.now(),
        id);
  }

  public List<Long> recordedDeviceIds(long recordingId) {
    return sql.query(
        "SELECT device_id FROM measurements WHERE recording_id=? UNION SELECT device_id FROM"
            + " waveform_chunks WHERE recording_id=? ORDER BY device_id",
        r -> r.getLong(1),
        recordingId,
        recordingId);
  }
}
