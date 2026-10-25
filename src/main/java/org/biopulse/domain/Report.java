package org.biopulse.domain;

public record Report(
    long id,
    Long admissionId,
    Long wardId,
    java.time.Instant from,
    java.time.Instant to,
    java.time.Instant createdAt,
    String createdBy) {}
