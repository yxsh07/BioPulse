package org.biopulse.analytics;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class BaselineAnomalyModel implements AnalysisModel {
  public Result analyze(List<Double> baseline, Double value, String provenance) {
    long started = System.nanoTime();
    var valid = baseline.stream().filter(Objects::nonNull).filter(Double::isFinite).toList();
    if (valid.size() < 5 || value == null || !Double.isFinite(value))
      return new Result(
          "RESEARCH_ONLY",
          "baseline-zscore/1.0",
          Map.of(),
          null,
          Instant.now(),
          (System.nanoTime() - started) / 1000,
          provenance,
          "INCOMPLETE");
    double mean = valid.stream().mapToDouble(x -> x).average().orElseThrow();
    double sd =
        Math.sqrt(valid.stream().mapToDouble(x -> (x - mean) * (x - mean)).average().orElseThrow());
    return new Result(
        "RESEARCH_ONLY",
        "baseline-zscore/1.0",
        Map.of(
            "baselineMean",
            mean,
            "baselineSd",
            sd,
            "observedValue",
            value,
            "baselineCount",
            (double) valid.size()),
        sd == 0 ? null : Math.abs(value - mean) / sd,
        Instant.now(),
        (System.nanoTime() - started) / 1000,
        provenance,
        sd == 0 ? "UNAVAILABLE_ZERO_VARIANCE" : "AVAILABLE");
  }
}
