package org.biopulse.service;

import java.time.*;
import java.util.*;
import org.biopulse.analytics.*;
import org.biopulse.dao.MeasurementDao;
import org.biopulse.domain.*;
import org.biopulse.exception.ApiException;
import org.biopulse.security.AccessPolicy;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
  private final MeasurementDao dao;
  private final AccessPolicy access;
  private final AnalysisModel model;

  public AnalyticsService(MeasurementDao dao, AccessPolicy access, AnalysisModel model) {
    this.dao = dao;
    this.access = access;
    this.model = model;
  }

  public Map<String, Object> analyze(
      long admission,
      MeasurementCode metric,
      Instant from,
      Instant to,
      int cadenceSeconds,
      Double low,
      Double high) {
    return analyze(admission, metric, from, to, cadenceSeconds, low, high, null);
  }

  public Map<String, Object> analyze(
      long admission,
      MeasurementCode metric,
      Instant from,
      Instant to,
      int cadenceSeconds,
      Double low,
      Double high,
      Long recordingId) {
    access.admission(admission);
    range(from, to);
    if (metric.waveform() || cadenceSeconds < 1 || cadenceSeconds > 3600)
      throw ApiException.bad("Invalid metric/cadence");
    if (low != null && !Double.isFinite(low)
        || high != null && !Double.isFinite(high)
        || low != null && high != null && low >= high)
      throw ApiException.bad("Invalid analysis bounds");
    var rows = dao.history(admission, metric, from, to, 100001, 0, recordingId);
    if (rows.size() > 100000)
      throw ApiException.bad("Range exceeds 100000 observations; narrow range");
    var unique = new java.util.TreeMap<Instant, Measurement>();
    rows.forEach(m -> unique.put(m.observedAt(), m));
    var canonical = java.util.List.copyOf(unique.values());
    var valid = canonical.stream().filter(Statistics::usable).toList();
    return Map.of(
        "summary",
        Statistics.summarize(
            admission,
            metric,
            rows,
            from,
            to,
            Duration.ofSeconds(cadenceSeconds),
            low,
            high,
            Instant.now()),
        "rollingAverage",
        Statistics.rollingAverage(canonical, Duration.ofSeconds(60)),
        "aggregationMethod",
        "sample-weighted; coverage: forward cadence capped at next observation; population SD",
        "cadenceSeconds",
        cadenceSeconds,
        "anomaly",
        model.analyze(
            valid.stream().limit(Math.max(0, valid.size() - 1)).map(Measurement::value).toList(),
            valid.isEmpty() ? null : valid.getLast().value(),
            "admission:"
                + admission
                + "; metric:"
                + metric
                + "; range:"
                + from
                + "/"
                + to
                + "; sources:"
                + valid.stream().map(Measurement::sourceId).distinct().toList()
                + "; recordings:"
                + valid.stream()
                    .map(Measurement::recordingId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList()));
  }

  public static void range(Instant from, Instant to) {
    if (from == null
        || to == null
        || !to.isAfter(from)
        || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0)
      throw ApiException.bad("Range must be positive and at most 31 days");
  }
}
