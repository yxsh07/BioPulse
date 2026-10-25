package org.biopulse.domain;

public record MedicalDevice(long id, String serial, String adapterType, boolean online) {}
