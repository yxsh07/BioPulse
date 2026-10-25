package org.biopulse.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.biopulse.dao.*;
import org.biopulse.domain.*;
import org.biopulse.dto.*;
import org.biopulse.dto.Requests.*;
import org.biopulse.security.AccessPolicy;
import org.biopulse.service.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class RegistryController {
  private final RegistryDao dao;
  private final PatientService patients;
  private final AdmissionService admissions;
  private final DeviceService devices;
  private final AccessPolicy access;
  private final PatientDao patientDao;
  private final AdmissionDao admissionDao;
  private final DeviceDao deviceDao;
  private final AuditDao auditDao;

  public RegistryController(
      RegistryDao dao,
      PatientService patients,
      AdmissionService admissions,
      DeviceService devices,
      AccessPolicy access,
      PatientDao patientDao,
      AdmissionDao admissionDao,
      DeviceDao deviceDao,
      AuditDao auditDao) {
    this.dao = dao;
    this.patients = patients;
    this.admissions = admissions;
    this.devices = devices;
    this.access = access;
    this.patientDao = patientDao;
    this.admissionDao = admissionDao;
    this.deviceDao = deviceDao;
    this.auditDao = auditDao;
  }

  @GetMapping("/patients")
  public Page<Patient> patients(
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
    return Page.of(
        patientDao.pageAuthorized(access.actor(), access.admin(), page * size, size + 1),
        page,
        size);
  }

  @GetMapping("/patients/{id}")
  public Patient patient(@PathVariable long id) {
    return patients.get(id);
  }

  @PostMapping("/patients")
  @PreAuthorize("hasRole('ADMIN')")
  public Patient createPatient(@Valid @RequestBody PatientCreate r) {
    return patients.create(r);
  }

  @GetMapping("/admissions")
  public Page<Admission> admissions(
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
    return Page.of(
        admissionDao.pageAuthorized(access.actor(), access.admin(), page * size, size + 1),
        page,
        size);
  }

  @GetMapping("/admissions/{id}")
  public Admission admission(@PathVariable long id) {
    return admissions.get(id);
  }

  @PostMapping("/admissions")
  @PreAuthorize("hasRole('ADMIN')")
  public Admission createAdmission(@Valid @RequestBody AdmissionCreate r) {
    return admissions.create(r);
  }

  @GetMapping("/devices")
  public List<MedicalDevice> devices() {
    return deviceDao.allAuthorized(access.actor(), access.admin());
  }

  @PostMapping("/devices")
  @PreAuthorize("hasRole('ADMIN')")
  public MedicalDevice createDevice(@Valid @RequestBody DeviceCreate r) {
    return devices.create(r);
  }

  @PostMapping("/device-assignments")
  @PreAuthorize("hasRole('ADMIN')")
  public DeviceAssignment assign(@Valid @RequestBody AssignmentCreate r) {
    return devices.assign(r);
  }

  @GetMapping("/wards")
  public List<Ward> wards() {
    return dao.wards().stream().filter(w -> access.canWard(w.id())).toList();
  }

  @GetMapping("/beds")
  public List<Bed> beds() {
    var ids = wards().stream().map(Ward::id).toList();
    return dao.beds().stream().filter(b -> ids.contains(b.wardId())).toList();
  }

  @GetMapping("/sources")
  @PreAuthorize("hasRole('ADMIN')")
  public List<DataSource> sources() {
    return dao.sources();
  }

  @PostMapping("/sources")
  @PreAuthorize("hasRole('ADMIN')")
  public DataSource source(@Valid @RequestBody SourceCreate r) {
    return dao.createSource(r);
  }

  @GetMapping("/recordings")
  @PreAuthorize("hasRole('ADMIN')")
  public List<SourceRecording> recordings() {
    return dao.recordings();
  }

  @PostMapping("/recordings")
  @PreAuthorize("hasRole('ADMIN')")
  public SourceRecording recording(@Valid @RequestBody RecordingCreate r) {
    AnalyticsService.range(r.startAt(), r.endAt());
    return dao.createRecording(r);
  }

  @GetMapping("/audit")
  @PreAuthorize("hasRole('ADMIN')")
  public Page<AuditEvent> audit(
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
    return Page.of(
        auditDao.page(page * size, size + 1).stream()
            .map(RegistryController::auditEvent)
            .toList(),
        page,
        size);
  }

  private static AuditEvent auditEvent(AuditDao.Entry entry) {
    String resource =
        "legacy_resource".equals(entry.entityType())
            ? entry.entityId()
            : entry.entityType() + ":" + entry.entityId();
    return new AuditEvent(
        entry.id(), entry.actor(), entry.action(), resource, entry.occurredAt());
  }

  @PostMapping("/devices/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  public MedicalDevice status(@PathVariable long id, @Valid @RequestBody DeviceStatus request) {
    return devices.status(id, request.online());
  }
}
