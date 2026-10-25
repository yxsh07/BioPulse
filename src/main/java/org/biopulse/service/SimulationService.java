package org.biopulse.service;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.biopulse.dao.*;
import org.biopulse.domain.*;
import org.biopulse.dto.Requests.*;
import org.biopulse.exception.ApiException;
import org.biopulse.security.AccessPolicy;
import org.biopulse.simulation.MeasurementSource;
import org.biopulse.simulation.ReplayClock;
import org.biopulse.websocket.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SimulationService {
  private final SimulationDao dao;
  private final RegistryDao registry;
  private final MeasurementDao measurements;
  private final MeasurementSource source;
  private final MeasurementIngestionService ingestion;
  private final MonitoringEventPublisher events;
  private final AccessPolicy access;
  private final Map<Long, ReplayClock> clocks = new ConcurrentHashMap<>();

  public SimulationService(
      SimulationDao dao,
      RegistryDao registry,
      MeasurementDao measurements,
      MeasurementSource source,
      MeasurementIngestionService ingestion,
      MonitoringEventPublisher events,
      AccessPolicy access) {
    this.dao = dao;
    this.registry = registry;
    this.measurements = measurements;
    this.source = source;
    this.ingestion = ingestion;
    this.events = events;
    this.access = access;
  }

  public SimulationSession get(long id) {
    var s = dao.get(id, false).orElseThrow(() -> ApiException.missing("Simulation"));
    access.admission(s.admissionId());
    return s;
  }

  @Transactional
  public SimulationSession create(SimulationCreate r) {
    access.admission(r.admissionId());
    var rec =
        registry.recording(r.recordingId()).orElseThrow(() -> ApiException.missing("Recording"));
    var admission =
        registry.admission(r.admissionId()).orElseThrow(() -> ApiException.missing("Admission"));
    try {
      new ReplayClock(rec.startAt(), rec.endAt(), r.speed());
    } catch (IllegalArgumentException invalid) {
      throw ApiException.bad(invalid.getMessage());
    }
    if (admission.status() != AdmissionStatus.ACTIVE)
      throw ApiException.bad("Replay requires an active admission");
    for (long device : dao.recordedDeviceIds(rec.id()))
      if (!registry.assigned(r.admissionId(), device, Instant.now()))
        throw ApiException.bad(
            "Recorded device " + device + " is not assigned to the target admission");
    var src =
        registry.createSource(
            new SourceCreate(
                "Replay " + rec.name(),
                SourceType.SIMULATION,
                "Recorded playback; original recording:"
                    + rec.id()
                    + "; original source:"
                    + rec.sourceId()));
    var output =
        registry.createRecording(
            new RecordingCreate(src.id(), "Replay " + rec.name(), rec.startAt(), rec.endAt()));
    var s = dao.create(r.admissionId(), rec.id(), output.id(), r.speed(), rec.startAt());
    access.audit("CREATE_SIMULATION", "session:" + s.id());
    return s;
  }

  @Transactional
  public SimulationSession transition(long id, String action) {
    var s = dao.get(id, true).orElseThrow(() -> ApiException.missing("Simulation"));
    access.admission(s.admissionId());
    SimulatorState next =
        switch (action) {
          case "start" -> {
            if (s.state() != SimulatorState.CREATED)
              throw ApiException.conflict("Only CREATED sessions can start");
            yield SimulatorState.RUNNING;
          }
          case "pause" -> {
            if (s.state() != SimulatorState.RUNNING)
              throw ApiException.conflict("Only RUNNING sessions can pause");
            yield SimulatorState.PAUSED;
          }
          case "resume" -> {
            if (s.state() != SimulatorState.PAUSED)
              throw ApiException.conflict("Only PAUSED sessions can resume");
            yield SimulatorState.RUNNING;
          }
          case "stop" -> {
            if (s.state() == SimulatorState.STOPPED || s.state() == SimulatorState.COMPLETED)
              throw ApiException.conflict("Session already terminal");
            yield SimulatorState.STOPPED;
          }
          default -> throw ApiException.bad("Invalid simulation action");
        };
    dao.state(id, s.state(), next);
    var result = dao.get(id, false).orElseThrow();
    var state = next;
    var recordingEnd =
        state == SimulatorState.RUNNING
            ? registry.recording(result.recordingId()).orElseThrow().endAt()
            : null;
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            clocks.remove(id);
            if (state == SimulatorState.RUNNING) {
              ReplayClock clock = new ReplayClock(result.cursorAt(), recordingEnd, result.speed());
              clock.start();
              clocks.put(id, clock);
            }
          }
        });
    publish(result);
    access.audit("SIMULATION_" + action, "session:" + id);
    return result;
  }

  @Transactional
  public void tick(long id) {
    var s = dao.get(id, true).orElseThrow();
    if (s.state() != SimulatorState.RUNNING) return;
    var original = registry.recording(s.recordingId()).orElseThrow();
    var output = registry.recording(s.outputRecordingId()).orElseThrow();
    ReplayClock clock =
        clocks.computeIfAbsent(
            id,
            ignored -> {
              ReplayClock resumed =
                  new ReplayClock(s.cursorAt(), original.endAt(), s.speed());
              resumed.start();
              return resumed;
            });
    Instant to = clock.cursor();
    Instant windowEnd = s.cursorAt().plusSeconds(10);
    if (to.isAfter(windowEnd)) to = windowEnd;
    if (to.isAfter(original.endAt())) to = original.endAt();
    if (!to.isAfter(s.cursorAt())) return;
    var rows = source.read(original.id(), s.cursorAt(), to, 1001);
    var chunks = measurements.recordingWaveforms(original.id(), s.cursorAt(), to, 1001);
    if (rows.size() > 1000 || chunks.size() > 1000)
      throw ApiException.bad("Replay window too dense; reduce speed");
    if (!rows.isEmpty())
      ingestion.ingest(
          rows.stream()
              .map(
                  m ->
                      new Observation(
                          "sim:" + id + ":" + m.id(),
                          s.admissionId(),
                          m.deviceId(),
                          m.metric(),
                          m.value(),
                          m.unit(),
                          m.observedAt(),
                          output.sourceId(),
                          output.id(),
                          m.quality()))
              .toList());
    for (var c : chunks)
      ingestion.ingestWaveform(
          new Waveform(
              "sim:" + id + ":" + c.id(),
              s.admissionId(),
              c.deviceId(),
              c.metric(),
              c.unit(),
              c.observedAt(),
              c.sampleRateHz(),
              c.samples(),
              output.sourceId(),
              output.id(),
              c.quality()));
    dao.cursor(
        id, to, to.equals(original.endAt()) ? SimulatorState.COMPLETED : SimulatorState.RUNNING);
    if (to.equals(original.endAt()))
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              clocks.remove(id);
            }
          });
    publish(dao.get(id, false).orElseThrow());
  }

  private void publish(SimulationSession s) {
    events.publish(
        MonitoringEvent.of(
            "SIMULATION", s.admissionId(), null, null, null, SourceType.SIMULATION, null, s));
  }
}
