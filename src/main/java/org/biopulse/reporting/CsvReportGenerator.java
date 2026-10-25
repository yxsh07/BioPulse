package org.biopulse.reporting;

import java.time.*;
import java.util.*;
import org.biopulse.analytics.Statistics;
import org.biopulse.dao.MeasurementDao;
import org.biopulse.domain.*;
import org.biopulse.exception.ApiException;
import org.springframework.stereotype.Component;

@Component
public class CsvReportGenerator implements ReportGenerator {
  private final MeasurementDao dao;

  public CsvReportGenerator(MeasurementDao dao) {
    this.dao = dao;
  }

  public String contentType() {
    return "text/csv";
  }

  public String generate(List<Long> admissions, Instant from, Instant to) {
    return generate(admissions, from, to, null);
  }

  public String generate(List<Long> admissions, Instant from, Instant to, Long recordingId) {
    StringBuilder out =
        new StringBuilder(
            "RESEARCH_ONLY - educational report; no clinical validation\nWindow,"
                + from
                + ","
                + to
                + "\nRecording,"
                + (recordingId == null ? "ALL" : recordingId)
                + "\n"
                + "Aggregation,sample-weighted,population SD,coverage uses explicit 10 second"
                + " cadence\n"
                + "Admission,Metric,Unit,Count,Minimum,Maximum,Mean,Median,SD,AvailablePercent,MissingIntervals,SourceIds,MissingRangesUTC\n");
    for (long id : admissions)
      for (var metric : MeasurementCode.values()) {
        if (metric.waveform()) continue;
        var rows = dao.history(id, metric, from, to, 100001, 0, recordingId);
        if (rows.size() > 100000) throw ApiException.bad("Report range too large");
        var s =
            Statistics.summarize(
                id, metric, rows, from, to, Duration.ofSeconds(10), null, null, Instant.now());
        out.append(id)
            .append(',')
            .append(metric)
            .append(',')
            .append(metric.unit())
            .append(',')
            .append(s.count())
            .append(',')
            .append(cell(s.minimum()))
            .append(',')
            .append(cell(s.maximum()))
            .append(',')
            .append(cell(s.mean()))
            .append(',')
            .append(cell(s.median()))
            .append(',')
            .append(cell(s.standardDeviation()))
            .append(',')
            .append(s.availabilityPercent())
            .append(',')
            .append(s.gaps().size())
            .append(',')
            .append(
                rows.stream()
                    .map(m -> Long.toString(m.sourceId()))
                    .distinct()
                    .reduce((a, b) -> a + ";" + b)
                    .orElse(""))
            .append(',')
            .append(
                s.gaps().stream()
                    .map(g -> g.from() + "/" + g.to())
                    .reduce((a, b) -> a + ";" + b)
                    .orElse(""))
            .append('\n');
      }
    return out.toString();
  }

  private String cell(Double d) {
    return d == null ? "" : d.toString();
  }
}
