package org.biopulse.dao;

import java.util.List;
import java.util.Optional;
import org.biopulse.domain.Admission;
import org.biopulse.dto.Requests.AdmissionCreate;

public interface AdmissionDao {
  Admission create(AdmissionCreate admission);

  Optional<Admission> find(long id);

  List<Admission> page(int offset, int limit);

  List<Admission> activeInWard(long wardId);

  List<Admission> allInWard(long wardId);

  List<Admission> pageAuthorized(String actor, boolean admin, int offset, int limit);
}
