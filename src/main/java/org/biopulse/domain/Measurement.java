package org.biopulse.domain;
public record Measurement(long id, String eventId, long admissionId, long deviceId, MeasurementCode metric, Double value, String unit, java.time.Instant observedAt, java.time.Instant receivedAt, long sourceId, Long recordingId, QualityFlag quality) {}
