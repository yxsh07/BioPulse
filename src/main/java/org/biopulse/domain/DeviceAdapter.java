package org.biopulse.domain;

public interface DeviceAdapter {
  boolean supports(MeasurementCode code);

  String type();
}
