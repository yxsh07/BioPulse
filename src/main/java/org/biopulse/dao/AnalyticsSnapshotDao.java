package org.biopulse.dao;

import java.time.Instant;
import java.util.List;

public interface AnalyticsSnapshotDao {
  record Snapshot(
      long id,
      long admissionId,
      Instant windowStart,
      Instant windowEnd,
      String algorithmVersion,
      Instant coverageStart,
      Instant coverageEnd,
      Instant generatedAt,
      String payloadJson) {}

  long create(
      long admissionId,
      Instant windowStart,
      Instant windowEnd,
      String algorithmVersion,
      Instant coverageStart,
      Instant coverageEnd,
      String payloadJson);

  List<Snapshot> history(long admissionId, Instant from, Instant to, int limit);
}
