package org.biopulse.domain;
import java.util.Set;import static org.biopulse.domain.MeasurementCode.*;
public final class TemperatureAdapter extends MonitoringDevice { public TemperatureAdapter(){super("TEMPERATURE",Set.of(TEMP));} }