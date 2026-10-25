package org.biopulse.domain;

public record ClinicalContext(
    long admissionId,
    Boolean supplementalOxygen,
    Integer spo2Scale,
    String consciousness,
    String notes) {}
