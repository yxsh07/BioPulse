package org.biopulse.alerts;

import java.time.Instant;
import java.util.List;
import org.biopulse.domain.Measurement;

public interface AlertEvaluator {
  Instant violationStart(List<Measurement> rows, org.biopulse.domain.AlertRule rule, Instant at);
}
