package org.biopulse.jdbc;

import java.time.Instant;
import java.util.List;
import org.biopulse.dao.AnalyticsSnapshotDao;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAnalyticsSnapshotDao implements AnalyticsSnapshotDao {
  private final Sql sql;

  public JdbcAnalyticsSnapshotDao(Sql sql) {
    this.sql = sql;
  }

  public long create(
      long admissionId,
      Instant start,
      Instant end,
      String version,
      Instant coverageStart,
      Instant coverageEnd,
      String payloadJson) {
    if (start == null
        || end == null
        || !end.isAfter(start)
        || version == null
        || version.isBlank()
        || coverageStart == null
        || coverageEnd == null
        || coverageEnd.isBefore(coverageStart)
        || coverageStart.isBefore(start)
        || coverageEnd.isAfter(end)
        || payloadJson == null
        || payloadJson.isBlank())
      throw new IllegalArgumentException("Invalid analytics provenance");
    Instant now = Instant.now();
    return sql.insert(
        "INSERT INTO"
            + " analytics_snapshots(admission_id,computed_at,payload,window_start_at,window_end_at,algorithm_version,source_coverage_start_at,source_coverage_end_at)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        admissionId,
        now,
        payloadJson,
        start,
        end,
        version,
        coverageStart,
        coverageEnd);
  }

  public List<Snapshot> history(long admissionId, Instant from, Instant to, int limit) {
    return sql.query(
        "SELECT * FROM analytics_snapshots WHERE admission_id=? AND window_start_at>=? AND"
            + " window_end_at<=? ORDER BY computed_at DESC,id DESC LIMIT ?",
        r ->
            new Snapshot(
                r.getLong("id"),
                r.getLong("admission_id"),
                Sql.instant(r, "window_start_at"),
                Sql.instant(r, "window_end_at"),
                r.getString("algorithm_version"),
                Sql.instant(r, "source_coverage_start_at"),
                Sql.instant(r, "source_coverage_end_at"),
                Sql.instant(r, "computed_at"),
                r.getString("payload")),
        admissionId,
        from,
        to,
        limit);
  }
}
