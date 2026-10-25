package org.biopulse.domain;
import java.util.Set;import static org.biopulse.domain.MeasurementCode.*;
public final class RespiratoryAdapter extends MonitoringDevice { public RespiratoryAdapter(){super("RESPIRATORY",Set.of(RESP,RR));} }