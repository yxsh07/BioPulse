package org.biopulse.service;

import org.biopulse.alerts.AlertEvaluator;
import org.biopulse.dao.*;
import org.biopulse.domain.*;
import org.biopulse.dto.Requests.RuleCreate;
import org.biopulse.exception.ApiException;
import org.biopulse.security.AccessPolicy;
import org.biopulse.websocket.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {
  private final AlertDao dao;
  private final RegistryDao registry;
  private final MeasurementDao observations;
  private final AlertEvaluator evaluator;
  private final AccessPolicy access;
  private final MonitoringEventPublisher events;

  public AlertService(
      AlertDao dao,
      RegistryDao registry,
      MeasurementDao observations,
      AlertEvaluator evaluator,
      AccessPolicy access,
      MonitoringEventPublisher events) {
    this.dao = dao;
    this.registry = registry;
    this.observations = observations;
    this.evaluator = evaluator;
    this.access = access;
    this.events = events;
  }

  @Transactional
  public void evaluate(Measurement m) {
    for (var rule : dao.rules(m.admissionId())) {
      if (rule.metric() != m.metric()) continue;
      dao.lockRule(rule.id());
      if (dao.active(rule.id()).isPresent()) continue;
      var from = m.observedAt().minusSeconds(rule.persistenceSeconds() + rule.maxGapSeconds());
      var rows =
          observations.history(
              m.admissionId(), m.metric(), from, m.observedAt().plusNanos(1000), 100001, 0);
      if (rows.size() > 100000) throw ApiException.bad("Rule window exceeds 100000 observations");
      var start = evaluator.violationStart(rows, rule, m.observedAt());
      if (start != null) {
        var alert = dao.open(rule, m, start);
        dao.event(alert.id(), "system", "OPEN");
        events.publish(
            MonitoringEvent.of(
                "ALERT",
                m.admissionId(),
                m.metric(),
                m.value(),
                m.unit(),
                registry.source(m.sourceId()).orElseThrow().type(),
                m.quality(),
                alert));
      }
    }
  }

  @Transactional
  public Alert transition(long id, long version, AlertState state) {
    var a = dao.get(id).orElseThrow(() -> ApiException.missing("Alert"));
    access.admission(a.admissionId());
    if (!dao.transition(id, version, state))
      throw ApiException.conflict("Stale version or invalid alert transition");
    dao.event(id, access.actor(), state.name());
    access.audit(state.name(), "alert:" + id);
    var result = dao.get(id).orElseThrow();
    events.publish(
        MonitoringEvent.of(
            "ALERT",
            a.admissionId(),
            a.metric(),
            a.value(),
            a.unit(),
            registry.source(a.sourceId()).orElseThrow().type(),
            a.quality(),
            result));
    return result;
  }

  @Transactional
  public org.biopulse.domain.AlertRule createRule(RuleCreate r) {
    access.admission(r.admissionId());
    if (r.metric().waveform()
        || (r.lowerBound() == null && r.upperBound() == null)
        || (r.lowerBound() != null && !Double.isFinite(r.lowerBound()))
        || (r.upperBound() != null && !Double.isFinite(r.upperBound()))
        || (r.lowerBound() != null && r.upperBound() != null && r.lowerBound() >= r.upperBound()))
      throw ApiException.bad("Invalid bounds or metric");
    var rule = dao.createRule(r);
    access.audit("CREATE_RULE", "rule:" + rule.id());
    return rule;
  }
}
