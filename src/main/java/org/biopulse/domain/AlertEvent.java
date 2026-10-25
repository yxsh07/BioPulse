package org.biopulse.domain;

public record AlertEvent(
    long id, long alertId, String actor, String action, java.time.Instant occurredAt) {}
