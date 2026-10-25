package org.biopulse.dao;

import java.time.Instant;
import java.util.*;
import org.biopulse.domain.*;

public interface MeasurementDao {
  List<Measurement> insertBatch(List<Measurement> values);

  WaveformChunk insertWaveform(WaveformChunk chunk);

  List<Measurement> latest(long admissionId);

  List<Measurement> history(
      long admissionId, MeasurementCode metric, Instant from, Instant to, int limit, int offset);

  List<WaveformChunk> waveforms(
      long admissionId, MeasurementCode metric, Instant from, Instant to, int limit, int offset);

  List<Measurement> recordingMeasurements(long recordingId, Instant from, Instant to, int limit);

  List<WaveformChunk> recordingWaveforms(long recordingId, Instant from, Instant to, int limit);
}
