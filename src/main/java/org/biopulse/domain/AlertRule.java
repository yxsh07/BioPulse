package org.biopulse.domain;
public record AlertRule(long id, long admissionId, MeasurementCode metric, Double lowerBound, Double upperBound, int persistenceSeconds, int maxGapSeconds, AlertSeverity severity, boolean enabled) {}
