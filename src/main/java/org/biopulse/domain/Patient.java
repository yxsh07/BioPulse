package org.biopulse.domain;

public record Patient(long id, String code, String displayName, java.time.LocalDate dateOfBirth) {}
