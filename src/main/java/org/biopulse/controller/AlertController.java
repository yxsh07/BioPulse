package org.biopulse.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.biopulse.dao.AlertDao;
import org.biopulse.domain.*;
import org.biopulse.dto.*;
import org.biopulse.dto.Requests.*;
import org.biopulse.exception.ApiException;
import org.biopulse.security.AccessPolicy;
import org.biopulse.service.AlertService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class AlertController {
  private final AlertDao dao;
  private final AlertService service;
  private final AccessPolicy access;

  public AlertController(AlertDao dao, AlertService service, AccessPolicy access) {
    this.dao = dao;
    this.service = service;
    this.access = access;
  }

  @GetMapping("/alerts")
  public Page<Alert> all(
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
    return Page.of(
        dao.list(null, access.actor(), access.admin(), size + 1, page * size), page, size);
  }

  @GetMapping("/admissions/{id}/alerts")
  public Page<Alert> admission(
      @PathVariable long id,
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
    access.admission(id);
    return Page.of(dao.list(id, access.actor(), access.admin(), size + 1, page * size), page, size);
  }

  @GetMapping("/alerts/{id}")
  public Map<String, Object> detail(@PathVariable long id) {
    var alert = dao.get(id).orElseThrow(() -> ApiException.missing("Alert"));
    access.admission(alert.admissionId());
    return Map.of(
        "alert",
        alert,
        "events",
        dao.events(id),
        "rules",
        dao.rules(alert.admissionId()).stream().filter(r -> r.id() == alert.ruleId()).toList());
  }

  @PostMapping("/alerts/{id}/acknowledge")
  public Alert acknowledge(@PathVariable long id, @Valid @RequestBody Transition r) {
    return service.transition(id, r.expectedVersion(), AlertState.ACKNOWLEDGED);
  }

  @PostMapping("/alerts/{id}/resolve")
  public Alert resolve(@PathVariable long id, @Valid @RequestBody Transition r) {
    return service.transition(id, r.expectedVersion(), AlertState.RESOLVED);
  }

  @GetMapping("/admissions/{id}/alert-rules")
  public List<org.biopulse.domain.AlertRule> rules(@PathVariable long id) {
    access.admission(id);
    return dao.rules(id);
  }

  @PostMapping("/alert-rules")
  @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
  public org.biopulse.domain.AlertRule rule(@Valid @RequestBody RuleCreate r) {
    return service.createRule(r);
  }
}
