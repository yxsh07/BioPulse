package org.biopulse.domain;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DeviceAdapterFactory {
  private final Map<String, DeviceAdapter> adapters;

  public DeviceAdapterFactory() {
    List<DeviceAdapter> all =
        List.of(
            new EcgAdapter(),
            new PulseOximetryAdapter(),
            new RespiratoryAdapter(),
            new BloodPressureAdapter(),
            new TemperatureAdapter(),
            new MonitoringDevice("MULTI", EnumSet.allOf(MeasurementCode.class)) {});
    Map<String, DeviceAdapter> map = new HashMap<>();
    all.forEach(a -> map.put(a.type(), a));
    adapters = Map.copyOf(map);
  }

  public DeviceAdapter create(String type) {
    var a = adapters.get(type);
    if (a == null) throw new IllegalArgumentException("Unsupported device adapter");
    return a;
  }
}
