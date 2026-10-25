package org.biopulse.jdbc;

import org.biopulse.dao.AccessDao;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccessDao implements AccessDao {
  private final Sql sql;

  public JdbcAccessDao(Sql sql) {
    this.sql = sql;
  }

  public boolean canAdmission(String actor, long admissionId) {
    return !sql.query(
            "SELECT a.id FROM admissions a JOIN beds b ON b.id=a.bed_id JOIN users u ON"
                + " u.username=? LEFT JOIN user_wards uw ON uw.user_id=u.id AND"
                + " uw.ward_id=b.ward_id WHERE a.id=? AND u.enabled=TRUE AND (u.role='ADMIN' OR"
                + " uw.ward_id IS NOT NULL)",
            r -> r.getLong(1),
            actor,
            admissionId)
        .isEmpty();
  }

  public boolean canPatient(String actor, long patientId) {
    return !sql.query(
            "SELECT p.id FROM patients p JOIN users u ON u.username=? WHERE p.id=? AND"
                + " u.enabled=TRUE AND (u.role='ADMIN' OR EXISTS(SELECT 1 FROM admissions a JOIN"
                + " beds b ON b.id=a.bed_id JOIN user_wards uw ON uw.ward_id=b.ward_id WHERE"
                + " a.patient_id=p.id AND uw.user_id=u.id))",
            r -> r.getLong(1),
            actor,
            patientId)
        .isEmpty();
  }

  public boolean canWard(String actor, long wardId) {
    return !sql.query(
            "SELECT w.id FROM wards w JOIN users u ON u.username=? WHERE w.id=? AND u.enabled=TRUE"
                + " AND (u.role='ADMIN' OR EXISTS(SELECT 1 FROM user_wards uw WHERE uw.user_id=u.id"
                + " AND uw.ward_id=w.id))",
            r -> r.getLong(1),
            actor,
            wardId)
        .isEmpty();
  }
}
