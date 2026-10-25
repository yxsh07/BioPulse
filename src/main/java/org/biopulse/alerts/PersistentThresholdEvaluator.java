package org.biopulse.alerts;

import java.time.*;
import java.util.*;
import org.biopulse.domain.Measurement;
import org.springframework.stereotype.Component;

@Component
public class PersistentThresholdEvaluator implements AlertEvaluator {
  private final AlertRule strategy;

  public PersistentThresholdEvaluator(AlertRule strategy) {
    this.strategy = strategy;
  }

  public Instant violationStart(
      List<Measurement> rows, org.biopulse.domain.AlertRule rule, Instant at) {
    var byTime = new TreeMap<Instant, Measurement>();
    rows.stream()
        .filter(m -> !m.observedAt().isAfter(at))
        .sorted(Comparator.comparingLong(Measurement::id))
        .forEach(m -> byTime.put(m.observedAt(), m));
    Instant start = null, previous = null;
    for (var m : byTime.values()) {
      if (!strategy.violated(m, rule)) {
        start = null;
        previous = m.observedAt();
        continue;
      }
      if (start == null
          || previous == null
          || Duration.between(previous, m.observedAt())
                  .compareTo(Duration.ofSeconds(rule.maxGapSeconds()))
              > 0) start = m.observedAt();
      previous = m.observedAt();
    }
    if (start == null
        || previous == null
        || !previous.equals(at)
        || Duration.between(start, at).getSeconds() < rule.persistenceSeconds()) return null;
    return start;
  }
}
