package org.biopulse.security;

import org.biopulse.dao.AccessDao;
import org.biopulse.dao.AuditDao;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AccessPolicy {
  private final AccessDao access;
  private final AuditDao audit;

  public AccessPolicy(AccessDao access, AuditDao audit) {
    this.access = access;
    this.audit = audit;
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
    return access.canAdmission(actor, id);
  }

  public boolean canPatient(long id) {
    return access.canPatient(actor(), id);
  }

  public boolean canWard(long id) {
    return access.canWard(actor(), id);
  }

  public void admission(long id) {
    if (!canAdmission(actor(), id)) throw new AccessDeniedException("Admission scope");
  }

  public void patient(long id) {
    if (!canPatient(id)) throw new AccessDeniedException("Patient scope");
  }

  public void ward(long id) {
    if (!canWard(id)) throw new AccessDeniedException("Ward scope");
  }

  public void audit(String action, String resource) {
    int delimiter = resource.indexOf(':');
    audit.append(
        actor(),
        action,
        delimiter < 0 ? "resource" : resource.substring(0, delimiter),
        delimiter < 0 ? resource : resource.substring(delimiter + 1));
  }
}
