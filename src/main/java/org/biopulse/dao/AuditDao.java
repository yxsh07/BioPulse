package org.biopulse.dao;

import java.time.Instant;
import java.util.List;

public interface AuditDao {
  record Entry(
      long id,
      String actor,
      String action,
      String entityType,
      String entityId,
      Instant occurredAt) {}

  long append(String actor, String action, String entityType, String entityId);

  List<Entry> page(int offset, int limit);

  List<Entry> byActor(String actor, int limit);

  List<Entry> byEntity(String entityType, String entityId, int limit);
}
