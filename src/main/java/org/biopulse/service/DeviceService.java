package org.biopulse.service;

import org.biopulse.dao.AdmissionDao;
import org.biopulse.dao.DeviceDao;
import org.biopulse.domain.*;
import org.biopulse.dto.Requests.*;
import org.biopulse.exception.ApiException;
import org.biopulse.security.AccessPolicy;
import org.biopulse.websocket.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {
  private final DeviceDao dao;
  private final AdmissionDao admissions;
  private final AccessPolicy access;
  private final MonitoringEventPublisher events;

  public DeviceService(
      DeviceDao dao,
      AdmissionDao admissions,
      AccessPolicy access,
      MonitoringEventPublisher events) {
    this.dao = dao;
    this.admissions = admissions;
    this.access = access;
    this.events = events;
  }

  @Transactional
  public MedicalDevice create(DeviceCreate r) {
    MedicalDevice d = dao.create(r);
    access.audit("REGISTER_DEVICE", "device:" + d.id());
    return d;
  }

  @Transactional
  public DeviceAssignment assign(AssignmentCreate r) {
    var a = admissions.find(r.admissionId()).orElseThrow(() -> ApiException.missing("Admission"));
    if (a.status() != AdmissionStatus.ACTIVE) throw ApiException.bad("Admission is not active");
    var x = dao.assign(r);
    access.audit("ASSIGN_DEVICE", "device:" + r.deviceId());
    events.publish(
        MonitoringEvent.of(
            "DEVICE_STATUS",
            r.admissionId(),
            null,
            null,
            null,
            SourceType.DEVICE,
            QualityFlag.UNKNOWN,
            x));
    return x;
  }

  @Transactional
  public MedicalDevice status(long id, boolean online) {
    dao.find(id).orElseThrow(() -> ApiException.missing("Device"));
    dao.setOnline(id, online);
    var device = dao.find(id).orElseThrow();
    for (long admission : dao.assignedAdmissions(id))
      events.publish(
          MonitoringEvent.of(
              "DEVICE_STATUS",
              admission,
              null,
              null,
              null,
              SourceType.DEVICE,
              QualityFlag.UNKNOWN,
              device));
    access.audit("DEVICE_STATUS_" + (online ? "ONLINE" : "OFFLINE"), "device:" + id);
    return device;
  }
}
