package org.biopulse.analytics;

import java.util.Map;
import org.biopulse.domain.WaveformChunk;

public interface SignalAnalyzer {
  Map<String, Object> analyze(WaveformChunk chunk);
}
