package org.biopulse.jdbc;

import java.util.List;
import org.biopulse.dao.AuditDao;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditDao implements AuditDao {
  private final Sql sql;

  public JdbcAuditDao(Sql sql) {
    this.sql = sql;
  }

  public long append(String actor, String action, String entityType, String entityId) {
    return sql.insert(
        "INSERT INTO audit_logs(actor,action,entity_type,entity_id) VALUES(?,?,?,?)",
        actor,
        action,
        entityType,
        entityId);
  }

  private final Sql.Mapper<Entry> mapper =
      r ->
          new Entry(
              r.getLong("id"),
              r.getString("actor"),
              r.getString("action"),
              r.getString("entity_type"),
              r.getString("entity_id"),
              Sql.instant(r, "occurred_at"));

  public List<Entry> byActor(String actor, int limit) {
    return sql.query(
        "SELECT * FROM audit_logs WHERE actor=? ORDER BY occurred_at DESC,id DESC LIMIT ?",
        mapper,
        actor,
        limit);
  }

  public List<Entry> byEntity(String entityType, String entityId, int limit) {
    return sql.query(
        "SELECT * FROM audit_logs WHERE entity_type=? AND entity_id=? ORDER BY occurred_at DESC,id"
            + " DESC LIMIT ?",
        mapper,
        entityType,
        entityId,
        limit);
  }
}
