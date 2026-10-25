package org.biopulse.security;

import org.biopulse.jdbc.Sql;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AccessPolicy {
  private final Sql sql;

  public AccessPolicy(Sql sql) {
    this.sql = sql;
  }

  public String actor() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    return a == null ? "system" : a.getName();
  }

  public boolean admin() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    return a != null
        && a.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_ADMIN"));
  }

  public boolean canAdmission(String actor, long id) {
    return !sql.query(
            "SELECT a.id FROM admissions a JOIN beds b ON b.id=a.bed_id JOIN users u ON"
                + " u.username=? LEFT JOIN user_wards uw ON uw.user_id=u.id AND"
                + " uw.ward_id=b.ward_id WHERE a.id=? AND u.enabled=TRUE AND (u.role='ADMIN' OR"
                + " uw.ward_id IS NOT NULL)",
            r -> r.getLong(1),
            actor,
            id)
        .isEmpty();
  }

  public void admission(long id) {
    if (!canAdmission(actor(), id)) throw new AccessDeniedException("Admission scope");
  }

  public void ward(long id) {
    if (!admin()
        && sql.query(
                "SELECT uw.ward_id FROM user_wards uw JOIN users u ON u.id=uw.user_id WHERE"
                    + " u.username=? AND uw.ward_id=?",
                r -> r.getLong(1),
                actor(),
                id)
            .isEmpty()) throw new AccessDeniedException("Ward scope");
  }

  public boolean canPatient(long id) {
    return admin()
        || !sql.query(
                "SELECT a.id FROM admissions a JOIN beds b ON b.id=a.bed_id JOIN user_wards uw ON"
                    + " uw.ward_id=b.ward_id JOIN users u ON u.id=uw.user_id WHERE a.patient_id=?"
                    + " AND u.username=?",
                r -> r.getLong(1),
                id,
                actor())
            .isEmpty();
  }

  public void patient(long id) {
    if (!canPatient(id)) throw new AccessDeniedException("Patient scope");
  }

  public void audit(String action, String resource) {
    sql.insert(
        "INSERT INTO audit_events(actor,action,resource,occurred_at) VALUES(?,?,?,?)",
        actor(),
        action,
        resource,
        java.time.Instant.now());
  }
}
