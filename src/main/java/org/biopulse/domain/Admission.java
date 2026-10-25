package org.biopulse.domain;
public record Admission(long id, long patientId, long bedId, AdmissionStatus status, java.time.Instant admittedAt, java.time.Instant dischargedAt) {}
