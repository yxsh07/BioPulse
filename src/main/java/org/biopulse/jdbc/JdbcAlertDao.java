package org.biopulse.jdbc;

import java.time.*;
import java.util.*;
import org.biopulse.dao.AlertDao;
import org.biopulse.domain.*;
import org.biopulse.dto.Requests.RuleCreate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAlertDao implements AlertDao {
  private final Sql sql;

  public JdbcAlertDao(Sql sql) {
    this.sql = sql;
  }

  private final Sql.Mapper<AlertRule> rm =
      r ->
          new AlertRule(
              r.getLong("id"),
              r.getLong("admission_id"),
              MeasurementCode.valueOf(r.getString("metric")),
              Sql.number(r, "lower_bound"),
              Sql.number(r, "upper_bound"),
              r.getInt("persistence_seconds"),
              r.getInt("max_gap_seconds"),
              AlertSeverity.valueOf(r.getString("severity")),
              r.getBoolean("enabled"));
  private final Sql.Mapper<Alert> am =
      r ->
          new Alert(
              r.getLong("id"),
              r.getLong("admission_id"),
              r.getLong("rule_id"),
              MeasurementCode.valueOf(r.getString("metric")),
              AlertSeverity.valueOf(r.getString("severity")),
              AlertState.valueOf(r.getString("state")),
              Sql.instant(r, "started_at"),
              Sql.instant(r, "triggered_at"),
              Sql.number(r, "value"),
              r.getString("unit"),
              QualityFlag.valueOf(r.getString("quality")),
              r.getLong("source_id"),
              r.getLong("version"));

  public List<AlertRule> rules(long id) {
    return sql.query(
        "SELECT * FROM alert_rules WHERE admission_id=? AND enabled=TRUE ORDER BY id", rm, id);
  }

  public AlertRule createRule(RuleCreate r) {
    long id =
        sql.insert(
            "INSERT INTO"
                + " alert_rules(admission_id,metric,lower_bound,upper_bound,persistence_seconds,max_gap_seconds,severity)"
                + " VALUES(?,?,?,?,?,?,?)",
            r.admissionId(),
            r.metric(),
            r.lowerBound(),
            r.upperBound(),
            r.persistenceSeconds(),
            r.maxGapSeconds(),
            r.severity());
    return rules(r.admissionId()).stream().filter(x -> x.id() == id).findFirst().orElseThrow();
  }

  public void lockRule(long id) {
    sql.query("SELECT id FROM alert_rules WHERE id=? FOR UPDATE", r -> r.getLong(1), id);
  }

  public List<Alert> list(Long admission, String actor, boolean admin, int limit, int offset) {
    return sql.query(
        "SELECT al.* FROM alerts al JOIN admissions a ON a.id=al.admission_id JOIN beds b ON"
            + " b.id=a.bed_id WHERE (? IS NULL OR al.admission_id=?) AND (? OR EXISTS(SELECT 1 FROM"
            + " user_wards uw JOIN users u ON u.id=uw.user_id WHERE uw.ward_id=b.ward_id AND"
            + " u.username=?)) ORDER BY al.triggered_at DESC,al.id DESC LIMIT ? OFFSET ?",
        am,
        admission,
        admission,
        admin,
        actor,
        limit,
        offset);
  }

  public Optional<Alert> get(long id) {
    return sql.query("SELECT * FROM alerts WHERE id=?", am, id).stream().findFirst();
  }

  public Optional<Alert> active(long id) {
    return sql.query("SELECT * FROM alerts WHERE rule_id=? AND state!='RESOLVED'", am, id).stream()
        .findFirst();
  }

  public Alert open(AlertRule r, Measurement m, Instant start) {
    long id =
        sql.insert(
            "INSERT INTO"
                + " alerts(admission_id,rule_id,metric,severity,state,started_at,triggered_at,value,unit,quality,source_id)"
                + " VALUES(?,?,?,?,'OPEN',?,?,?,?,?,?)",
            m.admissionId(),
            r.id(),
            m.metric(),
            r.severity(),
            start,
            m.observedAt(),
            m.value(),
            m.unit(),
            m.quality(),
            m.sourceId());
    return get(id).orElseThrow();
  }

  public boolean transition(long id, long version, AlertState state) {
    return sql.update(
            "UPDATE alerts SET state=?,version=version+1 WHERE id=? AND version=? AND"
                + " state!='RESOLVED' AND state!=?",
            state,
            id,
            version,
            state)
        == 1;
  }

  public void event(long id, String actor, String action) {
    sql.insert(
        "INSERT INTO alert_events(alert_id,actor,action,occurred_at) VALUES(?,?,?,?)",
        id,
        actor,
        action,
        Instant.now());
  }

  public List<AlertEvent> events(long id) {
    return sql.query(
        "SELECT * FROM alert_events WHERE alert_id=? ORDER BY id",
        r ->
            new AlertEvent(
                r.getLong("id"),
                r.getLong("alert_id"),
                r.getString("actor"),
                r.getString("action"),
                Sql.instant(r, "occurred_at")),
        id);
  }
}
