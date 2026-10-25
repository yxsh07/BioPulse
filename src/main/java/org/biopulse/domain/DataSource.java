package org.biopulse.domain;

public record DataSource(long id, String name, SourceType type, String provenance) {}
