package org.biopulse.jdbc;

import java.time.*;
import java.util.*;
import org.biopulse.dao.ReportDao;
import org.biopulse.domain.Report;
import org.biopulse.dto.Requests.ReportCreate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReportDao implements ReportDao {
  private final Sql sql;

  public JdbcReportDao(Sql sql) {
    this.sql = sql;
  }

  private final Sql.Mapper<Report> mapper =
      r ->
          new Report(
              r.getLong("id"),
              Sql.nullableLong(r, "admission_id"),
              Sql.nullableLong(r, "ward_id"),
              Sql.instant(r, "from_at"),
              Sql.instant(r, "to_at"),
              Sql.instant(r, "created_at"),
              r.getString("created_by"));

  public Report create(ReportCreate r, String actor, String content) {
    long id =
        sql.insert(
            "INSERT INTO reports(admission_id,ward_id,from_at,to_at,created_at,created_by,content)"
                + " VALUES(?,?,?,?,?,?,?)",
            r.admissionId(),
            r.wardId(),
            r.from(),
            r.to(),
            Instant.now(),
            actor,
            content);
    return get(id).orElseThrow();
  }

  public Optional<Report> get(long id) {
    return sql.query("SELECT * FROM reports WHERE id=?", mapper, id).stream().findFirst();
  }

  public String content(long id) {
    return sql.query("SELECT content FROM reports WHERE id=?", r -> r.getString(1), id).stream()
        .findFirst()
        .orElseThrow();
  }

  public List<Report> list(String actor, boolean admin, int limit, int offset) {
    return sql.query(
        "SELECT r.* FROM reports r WHERE (? OR (r.created_by=? AND EXISTS(SELECT 1 FROM user_wards"
            + " uw JOIN users u ON u.id=uw.user_id WHERE u.username=? AND (uw.ward_id=r.ward_id OR"
            + " EXISTS(SELECT 1 FROM admissions a JOIN beds b ON b.id=a.bed_id WHERE"
            + " a.id=r.admission_id AND b.ward_id=uw.ward_id))))) ORDER BY r.created_at DESC,r.id"
            + " DESC LIMIT ? OFFSET ?",
        mapper,
        admin,
        actor,
        actor,
        limit,
        offset);
  }
}
