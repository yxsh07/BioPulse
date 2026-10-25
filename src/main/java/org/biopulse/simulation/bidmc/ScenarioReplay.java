package org.biopulse.simulation.bidmc;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.biopulse.domain.*;

/**
 * Converts persisted replay rows into session-scoped simulated rows. The original recording stays
 * untouched; only the synthetic output source receives scenario changes.
 */
public final class ScenarioReplay {
  private final ScenarioTransformer transformer = new ScenarioTransformer();

  public ReplayData apply(
      SimulationSession session,
      SourceRecording original,
      SourceRecording output,
      List<Measurement> measurements,
      List<WaveformChunk> waveforms) {
    if (!session.scenario().synthetic()) {
      return new ReplayData(
          measurements.stream().map(m -> withEvent(m, "sim:" + session.id() + ":" + m.id())).toList(),
          waveforms.stream().map(c -> withEvent(c, "sim:" + session.id() + ":" + c.id())).toList(),
          0,
          0,
          List.of());
    }

    Map<NumericKey, Measurement> numericSources = new HashMap<>();
    for (var measurement : measurements) {
      numericSources.put(
          new NumericKey(
              measurement.metric(), offsetNanos(original.startAt(), measurement.observedAt())),
          measurement);
    }
    Map<MeasurementCode, List<WaveformChunk>> waveformSources = new EnumMap<>(MeasurementCode.class);
    for (var waveform : waveforms)
      waveformSources.computeIfAbsent(waveform.metric(), ignored -> new ArrayList<>()).add(waveform);
    waveformSources.values().forEach(list -> list.sort(Comparator.comparing(WaveformChunk::observedAt)));

    double duration =
        Duration.between(original.startAt(), original.endAt()).toNanos() / 1_000_000_000.0;
    var source =
        new BidmcRecording(
            original.name(),
            null,
            null,
            Map.of(),
            waveforms.stream()
                .map(
                    c ->
                        new BidmcRecording.WaveformFrame(
                            c.metric(),
                            c.metric().name(),
                            c.unit(),
                            offsetSeconds(original.startAt(), c.observedAt()),
                            c.sampleRateHz(),
                            c.samples(),
                            c.quality()))
                .toList(),
            measurements.stream()
                .map(
                    m ->
                        new BidmcRecording.NumericObservation(
                            m.metric(),
                            m.metric().name(),
                            offsetSeconds(original.startAt(), m.observedAt()),
                            m.value(),
                            m.quality()))
                .toList(),
            List.of(),
            duration);
    var scenario = session.scenario();
    var result =
        transformer.apply(
            source,
            scenario.mode(),
            scenario.fromSeconds(),
            Math.min(scenario.toSeconds(), duration),
            scenario.magnitude());

    Instant received = Instant.now();
    List<Measurement> changedMeasurements = new ArrayList<>();
    for (var value : result.recording().numerics()) {
      long nanos = nanos(value.offsetSeconds());
      var origin = numericSources.get(new NumericKey(value.metric(), nanos));
      if (origin == null)
        throw new IllegalStateException(
            "Scenario numeric row has no source at " + value.metric() + "@" + value.offsetSeconds());
      changedMeasurements.add(
          new Measurement(
              0,
              "sim:" + session.id() + ":m:" + origin.id(),
              session.admissionId(),
              origin.deviceId(),
              value.metric(),
              value.value(),
              origin.unit(),
              original.startAt().plusNanos(nanos),
              received,
              output.sourceId(),
              output.id(),
              value.quality()));
    }

    List<WaveformChunk> changedWaveforms = new ArrayList<>();
    Map<Long, Integer> segmentCounts = new HashMap<>();
    for (var frame : result.recording().waveforms()) {
      long nanos = nanos(frame.offsetSeconds());
      Instant observedAt = original.startAt().plusNanos(nanos);
      var origin = containing(waveformSources.get(frame.metric()), observedAt);
      if (origin == null)
        throw new IllegalStateException(
            "Scenario waveform frame has no source at " + frame.metric() + "@" + frame.offsetSeconds());
      int segment = segmentCounts.merge(origin.id(), 1, Integer::sum) - 1;
      changedWaveforms.add(
          new WaveformChunk(
              0,
              "sim:" + session.id() + ":w:" + origin.id() + "." + segment,
              session.admissionId(),
              origin.deviceId(),
              frame.metric(),
              frame.sourceUnit(),
              observedAt,
              received,
              frame.sampleRateHz(),
              frame.samples(),
              output.sourceId(),
              output.id(),
              frame.quality()));
    }
    return new ReplayData(
        List.copyOf(changedMeasurements),
        List.copyOf(changedWaveforms),
        result.modificationLog().isEmpty() ? 0 : 1,
        result.telemetryDelaySeconds(),
        result.modificationLog());
  }

  private WaveformChunk containing(List<WaveformChunk> chunks, Instant at) {
    if (chunks == null) return null;
    for (var chunk : chunks) {
      long rel = Duration.between(chunk.observedAt(), at).toNanos();
      long length = nanos(chunk.samples().size() / chunk.sampleRateHz());
      if (rel >= 0 && rel < length) return chunk;
    }
    return null;
  }

  private Measurement withEvent(Measurement m, String eventId) {
    return new Measurement(
        m.id(),
        eventId,
        m.admissionId(),
        m.deviceId(),
        m.metric(),
        m.value(),
        m.unit(),
        m.observedAt(),
        m.receivedAt(),
        m.sourceId(),
        m.recordingId(),
        m.quality());
  }

  private WaveformChunk withEvent(WaveformChunk c, String eventId) {
    return new WaveformChunk(
        c.id(),
        eventId,
        c.admissionId(),
        c.deviceId(),
        c.metric(),
        c.unit(),
        c.observedAt(),
        c.receivedAt(),
        c.sampleRateHz(),
        c.samples(),
        c.sourceId(),
        c.recordingId(),
        c.quality());
  }

  private static long offsetNanos(Instant start, Instant at) {
    return Duration.between(start, at).toNanos();
  }

  private static double offsetSeconds(Instant start, Instant at) {
    return offsetNanos(start, at) / 1_000_000_000.0;
  }

  private static long nanos(double seconds) {
    return Math.round(seconds * 1_000_000_000.0);
  }

  private record NumericKey(MeasurementCode metric, long nanos) {}

  public record ReplayData(
      List<Measurement> measurements,
      List<WaveformChunk> waveforms,
      int affectedBatches,
      int telemetryDelaySeconds,
      List<String> modificationLog) {
    public ReplayData {
      measurements = List.copyOf(measurements);
      waveforms = List.copyOf(waveforms);
      modificationLog = List.copyOf(modificationLog);
    }
  }
}
