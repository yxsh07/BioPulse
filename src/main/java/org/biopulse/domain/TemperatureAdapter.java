package org.biopulse.domain;

import static org.biopulse.domain.MeasurementCode.*;

import java.util.Set;

public final class TemperatureAdapter extends MonitoringDevice {
  public TemperatureAdapter() {
    super("TEMPERATURE", Set.of(TEMP));
  }
}
