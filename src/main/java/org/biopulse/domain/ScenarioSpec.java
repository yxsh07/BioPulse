package org.biopulse.domain;

/**
 * Immutable scenario definition for one replay session. Source import rows are never changed; a
 * non-NORMAL scenario is applied only to the session's simulated output.
 */
public record ScenarioSpec(
    ScenarioMode mode,
    Double fromSeconds,
    Double toSeconds,
    Double magnitude) {
  public static final ScenarioSpec NORMAL = new ScenarioSpec(ScenarioMode.NORMAL, null, null, null);

  public ScenarioSpec {
    if (mode == null) mode = ScenarioMode.NORMAL;
    if (mode == ScenarioMode.NORMAL) {
      fromSeconds = null;
      toSeconds = null;
      magnitude = null;
    }
  }

  public boolean synthetic() {
    return mode != ScenarioMode.NORMAL;
  }
}
