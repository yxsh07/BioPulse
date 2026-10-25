package org.biopulse.dao;

public interface AccessDao {
  boolean canAdmission(String actor, long admissionId);

  boolean canPatient(String actor, long patientId);

  boolean canWard(String actor, long wardId);
}
