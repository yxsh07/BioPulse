package org.biopulse.service;

import java.time.*;
import java.util.*;
import org.biopulse.dao.*;
import org.biopulse.domain.*;
import org.biopulse.dto.Requests.*;
import org.biopulse.exception.ApiException;
import org.biopulse.security.AccessPolicy;
import org.biopulse.websocket.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeasurementIngestionService {
  private final RegistryDao registry;
  private final MeasurementDao dao;
  private final DeviceAdapterFactory factory;
  private final AlertService alerts;
  private final MonitoringEventPublisher publisher;
  private final AccessPolicy access;

  public MeasurementIngestionService(
      RegistryDao registry,
      MeasurementDao dao,
      DeviceAdapterFactory factory,
      AlertService alerts,
      MonitoringEventPublisher publisher,
      AccessPolicy access) {
    this.registry = registry;
    this.dao = dao;
    this.factory = factory;
    this.alerts = alerts;
    this.publisher = publisher;
    this.access = access;
  }

  public DataSource validate(
      long admissionId,
      long deviceId,
      MeasurementCode code,
      String unit,
      Instant time,
      long sourceId,
      Long recordingId) {
    if (code == null || time == null) throw ApiException.bad("Metric and timestamp required");
    var a =
        registry.admission(admissionId).orElseThrow(() -> ApiException.bad("Invalid admission"));
    if (a.status() != AdmissionStatus.ACTIVE) throw ApiException.bad("Admission must be active");
    var device = registry.device(deviceId).orElseThrow(() -> ApiException.bad("Invalid device"));
    var source =
        registry.source(sourceId).orElseThrow(() -> ApiException.bad("Source provenance required"));
    if (!registry.assigned(
        admissionId, deviceId, source.type() == SourceType.DEVICE ? time : Instant.now()))
      throw ApiException.bad("Device is not assigned");
    if (!factory.create(device.adapterType()).supports(code))
      throw ApiException.bad("Device does not support metric");
    if (!code.unit().equals(unit)) throw ApiException.bad("Expected canonical unit " + code.unit());
    if (time.isAfter(Instant.now().plusSeconds(30)))
      throw ApiException.bad("Timestamp is in the future");
    if (source.type() == SourceType.DEVICE && time.isBefore(a.admittedAt()))
      throw ApiException.bad("Timestamp predates admission");
    if (source.provenance().isBlank()) throw ApiException.bad("Source provenance required");
    if (source.type() != SourceType.DEVICE && recordingId == null)
      throw ApiException.bad("Dataset or simulation recording required");
    if (recordingId != null) {
      var recording =
          registry.recording(recordingId).orElseThrow(() -> ApiException.bad("Invalid recording"));
      if (recording.sourceId() != sourceId
          || time.isBefore(recording.startAt())
          || !time.isBefore(recording.endAt()))
        throw ApiException.bad("Recording provenance or timestamp mismatch");
    }
    return source;
  }

  @Transactional
  public List<Measurement> ingest(List<Observation> input) {
    if (input.isEmpty() || input.size() > 1000)
      throw ApiException.bad("Batch size must be 1..1000");
    input.stream()
        .map(Observation::admissionId)
        .distinct()
        .sorted()
        .forEach(registry::lockAdmission);
    List<Measurement> rows = new ArrayList<>();
    Map<Long, DataSource> sources = new HashMap<>();
    for (var r : input) {
      DataSource source =
          validate(
              r.admissionId(),
              r.deviceId(),
              r.metric(),
              r.unit(),
              r.observedAt(),
              r.sourceId(),
              r.recordingId());
      if (r.metric().waveform()) throw ApiException.bad("Use waveform chunk ingestion");
      if (r.quality() == null) throw ApiException.bad("Quality required");
      boolean missing = r.quality() == QualityFlag.MISSING || r.quality() == QualityFlag.INVALID;
      if (missing && r.value() != null
          || !missing && (r.value() == null || !Double.isFinite(r.value())))
        throw ApiException.bad("Quality and numerical value are inconsistent");
      if (r.eventId() == null || r.eventId().isBlank())
        throw ApiException.bad("Event identity required");
      sources.put(r.sourceId(), source);
      rows.add(
          new Measurement(
              0,
              r.eventId(),
              r.admissionId(),
              r.deviceId(),
              r.metric(),
              r.value(),
              r.unit(),
              r.observedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
              Instant.now(),
              r.sourceId(),
              r.recordingId(),
              r.quality()));
    }
    rows = dao.insertBatch(rows);
    for (var m : rows.stream().sorted(Comparator.comparing(Measurement::observedAt)).toList()) {
      alerts.evaluate(m);
      publisher.publish(
          MonitoringEvent.of(
              "READING",
              m.admissionId(),
              m.metric(),
              m.value(),
              m.unit(),
              sources.get(m.sourceId()).type(),
              m.quality(),
              m));
    }
    access.audit("INGEST_BATCH", "measurements:" + rows.size());
    return List.copyOf(rows);
  }

  @Transactional
  public WaveformChunk ingestWaveform(Waveform r) {
    registry.lockAdmission(r.admissionId());
    var source =
        validate(
            r.admissionId(),
            r.deviceId(),
            r.metric(),
            r.unit(),
            r.observedAt(),
            r.sourceId(),
            r.recordingId());
    if (!r.metric().waveform()
        || r.samples() == null
        || r.samples().isEmpty()
        || r.samples().size() > 4096
        || r.samples().stream().anyMatch(v -> v == null || !Double.isFinite(v))
        || !Double.isFinite(r.sampleRateHz())
        || r.sampleRateHz() < 0.1
        || r.sampleRateHz() > 2000
        || r.quality() == null
        || r.quality() == QualityFlag.MISSING
        || r.quality() == QualityFlag.INVALID)
      throw ApiException.bad("Invalid waveform chunk; omit missing spans");
    if (r.recordingId() != null) {
      var rec = registry.recording(r.recordingId()).orElseThrow();
      if (r.observedAt()
          .plusNanos((long) (r.samples().size() / r.sampleRateHz() * 1e9))
          .isAfter(rec.endAt())) throw ApiException.bad("Chunk exceeds recording");
    }
    var c =
        new WaveformChunk(
            0,
            r.eventId(),
            r.admissionId(),
            r.deviceId(),
            r.metric(),
            r.unit(),
            r.observedAt(),
            Instant.now(),
            r.sampleRateHz(),
            r.samples(),
            r.sourceId(),
            r.recordingId(),
            r.quality());
    c = dao.insertWaveform(c);
    publisher.publish(
        MonitoringEvent.of(
            "WAVEFORM",
            r.admissionId(),
            r.metric(),
            null,
            r.unit(),
            source.type(),
            r.quality(),
            c));
    return c;
  }
}
