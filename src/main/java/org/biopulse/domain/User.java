package org.biopulse.domain;

public record User(long id, String username, String passwordHash, Role role, boolean enabled) {}
