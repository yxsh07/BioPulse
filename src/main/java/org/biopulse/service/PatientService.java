package org.biopulse.service;

import org.biopulse.dao.RegistryDao;
import org.biopulse.domain.Patient;
import org.biopulse.dto.Requests.PatientCreate;
import org.biopulse.exception.ApiException;
import org.biopulse.security.AccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {
  private final RegistryDao dao;
  private final AccessPolicy access;

  public PatientService(RegistryDao dao, AccessPolicy access) {
    this.dao = dao;
    this.access = access;
  }

  public Patient get(long id) {
    Patient p = dao.patient(id).orElseThrow(() -> ApiException.missing("Patient"));
    access.patient(id);
    return p;
  }

  @Transactional
  public Patient create(PatientCreate r) {
    Patient p = dao.createPatient(r);
    access.audit("CREATE_PATIENT", "patient:" + p.id());
    return p;
  }
}
