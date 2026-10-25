package org.biopulse.analytics;

import java.time.Instant;
import java.util.*;

public interface AnalysisModel {
  record Result(
      String label,
      String modelVersion,
      Map<String, Double> inputFeatures,
      Double score,
      Instant inferredAt,
      long inferenceDurationMicros,
      String provenance,
      String status) {
    public Result {
      inputFeatures = Map.copyOf(inputFeatures);
    }
  }

  Result analyze(List<Double> baseline, Double value, String provenance);
}
