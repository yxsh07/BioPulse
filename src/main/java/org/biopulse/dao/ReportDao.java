package org.biopulse.dao;

import java.util.*;
import org.biopulse.domain.Report;
import org.biopulse.dto.Requests.ReportCreate;

public interface ReportDao {
  Report create(ReportCreate r, String actor, String content);

  Optional<Report> get(long id);

  String content(long id);

  List<Report> list(String actor, boolean admin, int limit, int offset);
}
