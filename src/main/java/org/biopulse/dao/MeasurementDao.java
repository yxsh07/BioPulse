package org.biopulse.dao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.biopulse.domain.*;

public interface MeasurementDao {
  record ConvertedObservation(
      String ingestionEventId,
      long admissionId,
      long deviceId,
      MeasurementCode metric,
      Instant recordedAt,
      Instant receivedAt,
      BigDecimal valueNumeric,
      String canonicalUnit,
      BigDecimal originalValue,
      String originalUnit,
      String conversionMethod,
      QualityFlag quality,
      long sourceId,
      Long sourceRecordingId) {}

  long insertConverted(ConvertedObservation observation);

  List<Measurement> insertBatch(List<Measurement> values);

  WaveformChunk insertWaveform(WaveformChunk chunk);

  List<Measurement> latest(long admissionId);

  List<Measurement> latest(long admissionId, Long recordingId, Instant at);

  record RecordingView(
      long id,
      long sourceId,
      String name,
      Instant startAt,
      Instant endAt,
      SourceType sourceType,
      String provenance) {}

  List<RecordingView> recordings(long admissionId);

  /** Latest GOOD/FAIR reading per metric, excluding missing/invalid observations. */
  List<Measurement> latestValid(long admissionId);

  List<Measurement> history(
      long admissionId, MeasurementCode metric, Instant from, Instant to, int limit, int offset);

  List<Measurement> history(
      long admissionId,
      MeasurementCode metric,
      Instant from,
      Instant to,
      int limit,
      int offset,
      Long recordingId);

  List<WaveformChunk> waveforms(
      long admissionId, MeasurementCode metric, Instant from, Instant to, int limit, int offset);

  List<WaveformChunk> waveforms(
      long admissionId,
      MeasurementCode metric,
      Instant from,
      Instant to,
      int limit,
      int offset,
      Long recordingId);

  List<Measurement> recordingMeasurements(long recordingId, Instant from, Instant to, int limit);

  List<WaveformChunk> recordingWaveforms(long recordingId, Instant from, Instant to, int limit);
}
