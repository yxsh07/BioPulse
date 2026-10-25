package org.biopulse.simulation;

import java.time.Instant;
import java.util.List;
import org.biopulse.domain.Measurement;

public interface MeasurementSource {
  List<Measurement> read(long recordingId, Instant from, Instant to, int limit);
}
