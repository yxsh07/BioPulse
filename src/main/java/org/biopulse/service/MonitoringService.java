package org.biopulse.service;
import org.springframework.stereotype.Service;import org.biopulse.dao.MeasurementDao;import org.biopulse.security.AccessPolicy;import org.biopulse.domain.Measurement;import java.util.List;
@Service public class MonitoringService {
 private final MeasurementDao dao;private final AccessPolicy access;public MonitoringService(MeasurementDao dao,AccessPolicy access){this.dao=dao;this.access=access;}public List<Measurement> latest(long id){access.admission(id);return dao.latest(id);}
}