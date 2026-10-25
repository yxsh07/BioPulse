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
import org.biopulse.simulation.bidmc.ScenarioReplay;
import org.biopulse.websocket.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SimulationService {
  private final SimulationDao dao;
  private final RegistryDao registry;
  private final ProvenanceDao provenance;
  private final MeasurementDao measurements;
  private final MeasurementSource source;
  private final MeasurementIngestionService ingestion;
  private final MonitoringEventPublisher events;
  private final AccessPolicy access;
  private final ScenarioReplay scenarioReplay = new ScenarioReplay();
  private final Map<Long, ReplayClock> clocks = new ConcurrentHashMap<>();

  public SimulationService(
      SimulationDao dao,
      RegistryDao registry,
      ProvenanceDao provenance,
      MeasurementDao measurements,
      MeasurementSource source,
      MeasurementIngestionService ingestion,
      MonitoringEventPublisher events,
      AccessPolicy access) {
    this.dao = dao;
    this.registry = registry;
    this.provenance = provenance;
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

  public List<SimulationSession> list(int limit, int offset) {
    return dao.list(access.actor(), access.admin(), limit, offset);
  }

  public List<SimulationSession> list(int limit, int offset, Long admissionId) {
    if (admissionId != null) access.admission(admissionId);
    return dao.list(access.actor(), access.admin(), limit, offset, admissionId);
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
    ScenarioSpec scenario = validateScenario(r.scenario(), rec);
    for (long device : dao.recordedDeviceIds(rec.id()))
      if (!registry.assigned(r.admissionId(), device, Instant.now()))
        throw ApiException.bad(
            "Recorded device " + device + " is not assigned to the target admission");
    String name =
        fit(
            "Replay " + rec.name() + (scenario.synthetic() ? " [" + scenario.mode() + "]" : ""),
            100);
    String sourceProvenance =
        "Recorded playback; original recording:"
            + rec.id()
            + "; original source:"
            + rec.sourceId()
            + (scenario.synthetic()
                ? "; synthetic scenario="
                    + scenario.mode()
                    + " range=["
                    + scenario.fromSeconds()
                    + ","
                    + scenario.toSeconds()
                    + ")s magnitude="
                    + scenario.magnitude()
                    + "; values changed only in simulated output"
                : "");
    long sourceId =
        provenance.createSource(
            new ProvenanceDao.SourceInput(
                name,
                SourceType.SIMULATION,
                sourceProvenance,
                scenario.synthetic() ? "SYNTHETIC_CLINICAL" : "MANUAL_SIMULATION",
                null,
                null,
                null,
                null,
                null,
                null));
    long outputId =
        provenance.createRecording(
            new ProvenanceDao.RecordingInput(
                sourceId,
                "sim:" + UUID.randomUUID(),
                name,
                null,
                Long.toString(rec.id()),
                List.of(),
                null,
                rec.startAt(),
                rec.endAt()));
    var output = registry.recording(outputId).orElseThrow();
    var s = dao.create(r.admissionId(), rec.id(), output.id(), r.speed(), rec.startAt(), scenario);
    access.audit("CREATE_SIMULATION", "session:" + s.id());
    return s;
  }

  private static String fit(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max);
  }

  private ScenarioSpec validateScenario(ScenarioSpec requested, SourceRecording rec) {
    ScenarioSpec scenario = requested == null ? ScenarioSpec.NORMAL : requested;
    if (!scenario.synthetic()) return ScenarioSpec.NORMAL;
    Double from = scenario.fromSeconds();
    Double to = scenario.toSeconds();
    Double magnitude = scenario.magnitude();
    if (from == null
        || to == null
        || magnitude == null
        || !Double.isFinite(from)
        || !Double.isFinite(to)
        || !Double.isFinite(magnitude))
      throw ApiException.bad("Scenario range and magnitude are required");
    double duration = Duration.between(rec.startAt(), rec.endAt()).toNanos() / 1_000_000_000.0;
    if (from < 0 || to <= from || to > duration + 0.000001)
      throw ApiException.bad("Scenario range must be within the recording duration");
    if (magnitude < 0 || magnitude > 100)
      throw ApiException.bad("Scenario magnitude must be between 0 and 100");
    return new ScenarioSpec(scenario.mode(), from, to, magnitude);
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
              ReplayClock resumed = new ReplayClock(s.cursorAt(), original.endAt(), s.speed());
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
    var replay = scenarioReplay.apply(s, original, output, rows, chunks);
    if (!replay.measurements().isEmpty())
      ingestion.ingest(
          replay.measurements().stream()
              .map(
                  m ->
                      new Observation(
                          m.eventId(),
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
    for (var c : replay.waveforms())
      ingestion.ingestWaveform(
          new Waveform(
              c.eventId(),
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
