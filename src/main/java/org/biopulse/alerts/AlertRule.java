package org.biopulse.alerts;
import org.biopulse.domain.Measurement;
public interface AlertRule {boolean violated(Measurement measurement,org.biopulse.domain.AlertRule rule);}
