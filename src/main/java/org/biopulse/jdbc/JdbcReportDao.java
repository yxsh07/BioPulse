package org.biopulse.jdbc;

import java.time.*;
import java.util.*;
import org.biopulse.dao.AccessDao;
import org.biopulse.dao.ReportDao;
import org.biopulse.domain.Report;
import org.biopulse.dto.Requests.ReportCreate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReportDao implements ReportDao {
  private final Sql sql;
  private final AccessDao access;

  public JdbcReportDao(Sql sql, AccessDao access) {
    this.sql = sql;
    this.access = access;
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

  public Report createArtifact(
      ReportCreate r, String actor, String mediaType, String fileUri, byte[] binaryContent) {
    if (mediaType == null
        || mediaType.isBlank()
        || (fileUri == null) == (binaryContent == null)
        || (r.admissionId() == null) == (r.wardId() == null)
        || !r.to().isAfter(r.from())) throw new IllegalArgumentException("Invalid report artifact");
    if (r.admissionId() != null
        ? !access.canAdmission(actor, r.admissionId())
        : !access.canWard(actor, r.wardId()))
      throw new org.springframework.security.access.AccessDeniedException("Report scope");
    long id =
        sql.insert(
            "INSERT INTO"
                + " reports(admission_id,ward_id,from_at,to_at,created_at,created_by,content,media_type,file_uri,binary_content,owner_user_id)"
                + " VALUES(?,?,?,?,?,?,NULL,?,?,?,(SELECT id FROM users WHERE username=?))",
            r.admissionId(),
            r.wardId(),
            r.from(),
            r.to(),
            Instant.now(),
            actor,
            mediaType,
            fileUri,
            binaryContent,
            actor);
    return get(id).orElseThrow();
  }

  public Optional<Artifact> artifactForActor(long reportId, String actor) {
    Report report = get(reportId).orElse(null);
    if (report == null) return Optional.empty();
    if (report.admissionId() != null
        ? !access.canAdmission(actor, report.admissionId())
        : !access.canWard(actor, report.wardId())) return Optional.empty();
    return sql
        .query(
            "SELECT id,media_type,file_uri,binary_content FROM reports WHERE id=?",
            r ->
                new Artifact(
                    r.getLong("id"),
                    r.getString("media_type"),
                    r.getString("file_uri"),
                    r.getBytes("binary_content")),
            reportId)
        .stream()
        .findFirst();
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
