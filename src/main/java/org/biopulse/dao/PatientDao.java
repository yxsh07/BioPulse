package org.biopulse.dao;

import java.util.List;
import java.util.Optional;
import org.biopulse.domain.Patient;
import org.biopulse.dto.Requests.PatientCreate;

public interface PatientDao {
  Patient create(PatientCreate patient);

  Optional<Patient> find(long id);

  List<Patient> page(int offset, int limit);

  List<Patient> pageAuthorized(String actor, boolean admin, int offset, int limit);
}
