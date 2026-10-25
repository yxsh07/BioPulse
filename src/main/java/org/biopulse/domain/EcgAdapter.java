package org.biopulse.domain;
import java.util.Set;import static org.biopulse.domain.MeasurementCode.*;
public final class EcgAdapter extends MonitoringDevice { public EcgAdapter(){super("ECG",Set.of(ECG,HR));} }