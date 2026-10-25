package org.biopulse.jdbc;

import java.util.List;
import java.util.Optional;
import org.biopulse.dao.PatientDao;
import org.biopulse.domain.Patient;
import org.biopulse.dto.Requests.PatientCreate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPatientDao implements PatientDao {
  private final JdbcRegistryDao registry;
  private final Sql sql;

  public JdbcPatientDao(JdbcRegistryDao registry, Sql sql) {
    this.registry = registry;
    this.sql = sql;
  }

  public Patient create(PatientCreate patient) {
    return registry.createPatient(patient);
  }

  public Optional<Patient> find(long id) {
    return registry.patient(id);
  }

  public List<Patient> page(int offset, int limit) {
    return registry.patients(offset, limit);
  }

  public List<Patient> pageAuthorized(String actor, boolean admin, int offset, int limit) {
    return sql.query(
        "SELECT p.* FROM patients p WHERE ? OR EXISTS (SELECT 1 FROM admissions a JOIN beds b ON"
            + " b.id=a.bed_id JOIN user_wards uw ON uw.ward_id=b.ward_id JOIN users u ON"
            + " u.id=uw.user_id WHERE a.patient_id=p.id AND u.username=? AND u.enabled=TRUE) ORDER"
            + " BY p.id LIMIT ? OFFSET ?",
        r ->
            new Patient(
                r.getLong("id"),
                r.getString("code"),
                r.getString("display_name"),
                r.getDate("date_of_birth") == null
                    ? null
                    : r.getDate("date_of_birth").toLocalDate()),
        admin,
        actor,
        limit,
        offset);
  }
}
