package org.biopulse.alerts;

import org.biopulse.analytics.Statistics;
import org.biopulse.domain.Measurement;
import org.springframework.stereotype.Component;

@Component
public class ThresholdRule implements AlertRule {
  public boolean violated(Measurement m, org.biopulse.domain.AlertRule r) {
    return Statistics.usable(m)
        && m.metric() == r.metric()
        && ((r.lowerBound() != null && m.value() < r.lowerBound())
            || (r.upperBound() != null && m.value() > r.upperBound()));
  }
}
