package org.biopulse.jdbc;

import java.util.List;
import java.util.Optional;
import org.biopulse.dao.AdmissionDao;
import org.biopulse.domain.Admission;
import org.biopulse.domain.AdmissionStatus;
import org.biopulse.dto.Requests.AdmissionCreate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdmissionDao implements AdmissionDao {
  private final JdbcRegistryDao registry;
  private final Sql sql;

  public JdbcAdmissionDao(JdbcRegistryDao registry, Sql sql) {
    this.registry = registry;
    this.sql = sql;
  }

  public Admission create(AdmissionCreate admission) {
    return registry.createAdmission(admission);
  }

  public Optional<Admission> find(long id) {
    return registry.admission(id);
  }

  public List<Admission> page(int offset, int limit) {
    return registry.admissions(offset, limit);
  }

  public List<Admission> activeInWard(long wardId) {
    return sql.query(
        "SELECT a.* FROM admissions a JOIN beds b ON b.id=a.bed_id WHERE b.ward_id=? AND"
            + " a.status='ACTIVE' ORDER BY b.label,a.id",
        r ->
            new Admission(
                r.getLong("id"),
                r.getLong("patient_id"),
                r.getLong("bed_id"),
                AdmissionStatus.valueOf(r.getString("status")),
                Sql.instant(r, "admitted_at"),
                Sql.instant(r, "discharged_at")),
        wardId);
  }

  public List<Admission> allInWard(long wardId) {
    return sql.query(
        "SELECT a.* FROM admissions a JOIN beds b ON b.id=a.bed_id WHERE b.ward_id=? ORDER BY"
            + " a.admitted_at DESC,a.id DESC",
        this::map,
        wardId);
  }

  public List<Admission> pageAuthorized(String actor, boolean admin, int offset, int limit) {
    return sql.query(
        "SELECT a.* FROM admissions a JOIN beds b ON b.id=a.bed_id WHERE ? OR EXISTS (SELECT 1 FROM"
            + " user_wards uw JOIN users u ON u.id=uw.user_id WHERE uw.ward_id=b.ward_id AND"
            + " u.username=? AND u.enabled=TRUE) ORDER BY a.id LIMIT ? OFFSET ?",
        this::map,
        admin,
        actor,
        limit,
        offset);
  }

  private Admission map(java.sql.ResultSet r) throws java.sql.SQLException {
    return new Admission(
        r.getLong("id"),
        r.getLong("patient_id"),
        r.getLong("bed_id"),
        AdmissionStatus.valueOf(r.getString("status")),
        Sql.instant(r, "admitted_at"),
        Sql.instant(r, "discharged_at"));
  }
}
