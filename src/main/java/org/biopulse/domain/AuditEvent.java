package org.biopulse.domain;

public record AuditEvent(
    long id, String actor, String action, String resource, java.time.Instant occurredAt) {}
