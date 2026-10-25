package org.biopulse.domain;

public record AnalyticsSnapshot(
    long admissionId,
    MeasurementCode metric,
    java.time.Instant from,
    java.time.Instant to,
    long count,
    Double minimum,
    Double maximum,
    Double mean,
    Double median,
    Double standardDeviation,
    Double slopePerSecond,
    double availabilityPercent,
    double thresholdDeviationSeconds,
    java.util.List<org.biopulse.analytics.Statistics.Gap> gaps,
    boolean fresh) {}
