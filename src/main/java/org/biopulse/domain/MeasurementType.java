package org.biopulse.domain;
public record MeasurementType(MeasurementCode code, String canonicalUnit, java.time.Duration expectedCadence) {}
