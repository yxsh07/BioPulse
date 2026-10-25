package org.biopulse.domain;

public record WaveformChunk(
    long id,
    String eventId,
    long admissionId,
    long deviceId,
    MeasurementCode metric,
    String unit,
    java.time.Instant observedAt,
    java.time.Instant receivedAt,
    double sampleRateHz,
    java.util.List<Double> samples,
    long sourceId,
    Long recordingId,
    QualityFlag quality) {
  public WaveformChunk {
    samples = java.util.List.copyOf(samples);
  }
}
