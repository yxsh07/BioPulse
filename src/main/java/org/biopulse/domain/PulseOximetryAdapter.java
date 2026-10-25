package org.biopulse.domain;
import java.util.Set;import static org.biopulse.domain.MeasurementCode.*;
public final class PulseOximetryAdapter extends MonitoringDevice { public PulseOximetryAdapter(){super("OXIMETER",Set.of(PPG,PULSE,SPO2));} }