package org.biopulse.service;

import java.util.List;
import org.biopulse.dao.MeasurementDao;
import org.biopulse.domain.Measurement;
import org.biopulse.security.AccessPolicy;
import org.springframework.stereotype.Service;

@Service
public class MonitoringService {
  private final MeasurementDao dao;
  private final AccessPolicy access;

  public MonitoringService(MeasurementDao dao, AccessPolicy access) {
    this.dao = dao;
    this.access = access;
  }

  public List<Measurement> latest(long id) {
    return latest(id, null, null);
  }

  public List<Measurement> latest(long id, Long recordingId, java.time.Instant at) {
    access.admission(id);
    return dao.latest(id, recordingId, at);
  }
}
