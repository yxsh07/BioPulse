package org.biopulse.domain;

import static org.biopulse.domain.MeasurementCode.*;

import java.util.Set;

public final class BloodPressureAdapter extends MonitoringDevice {
  public BloodPressureAdapter() {
    super("NIBP", Set.of(SBP, DBP));
  }
}
