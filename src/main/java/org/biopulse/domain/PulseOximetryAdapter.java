package org.biopulse.domain;

import static org.biopulse.domain.MeasurementCode.*;

import java.util.Set;

public final class PulseOximetryAdapter extends MonitoringDevice {
  public PulseOximetryAdapter() {
    super("OXIMETER", Set.of(PPG, PULSE, SPO2));
  }
}
