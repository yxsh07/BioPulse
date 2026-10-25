package org.biopulse.domain;
import java.util.Set;
public abstract class MonitoringDevice implements DeviceAdapter {
 private final Set<MeasurementCode> supported;private final String type;
 protected MonitoringDevice(String type,Set<MeasurementCode> supported){this.type=type;this.supported=Set.copyOf(supported);}
 public final boolean supports(MeasurementCode code){return supported.contains(code);}public final String type(){return type;}
}