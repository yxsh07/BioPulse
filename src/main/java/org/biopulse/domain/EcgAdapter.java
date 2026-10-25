package org.biopulse.domain;

import static org.biopulse.domain.MeasurementCode.*;

import java.util.Set;

public final class EcgAdapter extends MonitoringDevice {
  public EcgAdapter() {
    super("ECG", Set.of(ECG, HR));
  }
}
