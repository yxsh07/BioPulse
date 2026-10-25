package org.biopulse.domain;

import static org.biopulse.domain.MeasurementCode.*;

import java.util.Set;

public final class RespiratoryAdapter extends MonitoringDevice {
  public RespiratoryAdapter() {
    super("RESPIRATORY", Set.of(RESP, RR));
  }
}
