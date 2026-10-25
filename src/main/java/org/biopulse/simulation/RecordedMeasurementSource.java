package org.biopulse.simulation;

import java.time.Instant;
import java.util.List;
import org.biopulse.dao.MeasurementDao;
import org.biopulse.domain.Measurement;
import org.springframework.stereotype.Component;

@Component
public class RecordedMeasurementSource implements MeasurementSource {
  private final MeasurementDao dao;

  public RecordedMeasurementSource(MeasurementDao dao) {
    this.dao = dao;
  }

  public List<Measurement> read(long recording, Instant from, Instant to, int limit) {
    return dao.recordingMeasurements(recording, from, to, limit);
  }
}
