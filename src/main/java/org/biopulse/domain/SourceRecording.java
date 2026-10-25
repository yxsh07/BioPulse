package org.biopulse.domain;
public record SourceRecording(long id, long sourceId, String name, java.time.Instant startAt, java.time.Instant endAt) {}
