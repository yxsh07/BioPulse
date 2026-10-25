package org.biopulse.dao;

import java.io.InputStream;
import java.time.Instant;
import org.biopulse.domain.MeasurementCode;

/**
 * Binary storage contract. Payload bytes are IEEE 754 float64 little-endian, optionally GZIP
 * compressed.
 */
public interface WaveformDao {
  record Chunk(
      long id,
      long admissionId,
      long deviceId,
      MeasurementCode metric,
      Long sourceRecordingId,
      Instant start,
      Instant end,
      double sampleRateHz,
      int sampleCount,
      long sampleOffset,
      String encoding,
      byte[] sha256) {
    public Chunk {
      sha256 = sha256.clone();
    }

    @Override
    public byte[] sha256() {
      return sha256.clone();
    }
  }

  long insert(
      Chunk chunk,
      String ingestionEventId,
      long sourceId,
      String unit,
      InputStream payload,
      long byteLength);

  /**
   * Streams each matching compressed payload without materializing it as SQL rows or a Java list.
   */
  void streamRange(
      long admissionId, MeasurementCode metric, Instant from, Instant to, ChunkConsumer consumer);

  @FunctionalInterface
  interface ChunkConsumer {
    void accept(Chunk chunk, InputStream payload) throws Exception;
  }
}
