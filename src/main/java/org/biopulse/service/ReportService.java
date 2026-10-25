package org.biopulse.service;

import java.util.List;
import org.biopulse.dao.ReportDao;
import org.biopulse.domain.Report;
import org.biopulse.dto.Requests.ReportCreate;
import org.biopulse.exception.ApiException;
import org.biopulse.jdbc.Sql;
import org.biopulse.reporting.ReportGenerator;
import org.biopulse.security.AccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
  private final ReportDao dao;
  private final Sql sql;
  private final ReportGenerator generator;
  private final AccessPolicy access;

  public ReportService(ReportDao dao, Sql sql, ReportGenerator generator, AccessPolicy access) {
    this.dao = dao;
    this.sql = sql;
    this.generator = generator;
    this.access = access;
  }

  @Transactional
  public Report create(ReportCreate r) {
    AnalyticsService.range(r.from(), r.to());
    if ((r.admissionId() == null) == (r.wardId() == null))
      throw ApiException.bad("Choose exactly one admission or ward");
    List<Long> ids;
    if (r.admissionId() != null) {
      access.admission(r.admissionId());
      ids = List.of(r.admissionId());
    } else {
      access.ward(r.wardId());
      ids =
          sql.query(
              "SELECT a.id FROM admissions a JOIN beds b ON b.id=a.bed_id WHERE b.ward_id=? ORDER"
                  + " BY a.id",
              x -> x.getLong(1),
              r.wardId());
    }
    Report report = dao.create(r, access.actor(), generator.generate(ids, r.from(), r.to()));
    access.audit("GENERATE_REPORT", "report:" + report.id());
    return report;
  }

  public Report get(long id) {
    var r = dao.get(id).orElseThrow(() -> ApiException.missing("Report"));
    if (r.admissionId() != null) access.admission(r.admissionId());
    else access.ward(r.wardId());
    return r;
  }

  public String download(long id) {
    get(id);
    access.audit("DOWNLOAD_REPORT", "report:" + id);
    return dao.content(id);
  }
}
