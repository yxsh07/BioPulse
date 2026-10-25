package org.biopulse.analytics;

import java.util.*;
import org.biopulse.domain.WaveformChunk;
import org.springframework.stereotype.Component;

@Component
public class BasicSignalAnalyzer implements SignalAnalyzer {
  public Map<String, Object> analyze(WaveformChunk c) {
    var stats = c.samples().stream().mapToDouble(Double::doubleValue).summaryStatistics();
    return Map.of(
        "samples",
        stats.getCount(),
        "minimum",
        stats.getMin(),
        "maximum",
        stats.getMax(),
        "flatline",
        stats.getMin() == stats.getMax(),
        "durationSeconds",
        c.samples().size() / c.sampleRateHz(),
        "quality",
        c.quality(),
        "clinicalValidation",
        false);
  }
}
