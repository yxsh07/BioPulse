package org.biopulse.domain;

public record Alert(
    long id,
    long admissionId,
    long ruleId,
    MeasurementCode metric,
    AlertSeverity severity,
    AlertState state,
    java.time.Instant startedAt,
    java.time.Instant triggeredAt,
    Double value,
    String unit,
    QualityFlag quality,
    long sourceId,
    long version) {}
