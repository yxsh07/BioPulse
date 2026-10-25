package org.biopulse.domain;

public record SimulationSession(
    long id,
    long admissionId,
    long recordingId,
    long outputRecordingId,
    SimulatorState state,
    double speed,
    java.time.Instant cursorAt,
    java.time.Instant createdAt,
    ScenarioSpec scenario) {
  public SimulationSession {
    scenario = scenario == null ? ScenarioSpec.NORMAL : scenario;
  }
}
