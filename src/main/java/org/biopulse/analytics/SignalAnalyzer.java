package org.biopulse.analytics;
import org.biopulse.domain.WaveformChunk;import java.util.Map;
public interface SignalAnalyzer { Map<String,Object> analyze(WaveformChunk chunk); }
