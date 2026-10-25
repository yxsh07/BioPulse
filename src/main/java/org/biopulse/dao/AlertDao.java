package org.biopulse.dao;

import java.time.Instant;
import java.util.*;
import org.biopulse.domain.*;
import org.biopulse.dto.Requests.RuleCreate;

public interface AlertDao {
  List<AlertRule> rules(long admissionId);

  AlertRule createRule(RuleCreate r);

  void lockRule(long id);

  List<Alert> list(Long admissionId, String actor, boolean admin, int limit, int offset);

  Optional<Alert> get(long id);

  Optional<Alert> active(long ruleId);

  Alert open(AlertRule rule, Measurement m, Instant start);

  boolean transition(long id, long version, AlertState state);

  void event(long id, String actor, String action);

  List<AlertEvent> events(long id);
}
