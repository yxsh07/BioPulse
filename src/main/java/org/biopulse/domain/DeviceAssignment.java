package org.biopulse.domain;

public record DeviceAssignment(
    long id,
    long deviceId,
    long admissionId,
    java.time.Instant assignedAt,
    java.time.Instant endedAt) {}
