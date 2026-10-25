package org.biopulse.domain;
import java.util.Set;import static org.biopulse.domain.MeasurementCode.*;
public final class BloodPressureAdapter extends MonitoringDevice { public BloodPressureAdapter(){super("NIBP",Set.of(SBP,DBP));} }