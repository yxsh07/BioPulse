package org.biopulse.jdbc;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import javax.sql.DataSource;
import org.biopulse.dao.WaveformDao;
import org.biopulse.domain.MeasurementCode;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcWaveformDao implements WaveformDao {
  private final DataSource dataSource;
  private final TransactionTemplate transactions;

  public JdbcWaveformDao(DataSource dataSource, TransactionTemplate transactions) {
    this.dataSource = dataSource;
    this.transactions = transactions;
  }

  public long insert(
      Chunk chunk,
      String eventId,
      long sourceId,
      String unit,
      InputStream payload,
      long byteLength) {
    if (chunk.id() != 0
        || chunk.metric() == null
        || !chunk.metric().waveform()
        || chunk.sourceRecordingId() == null
        || chunk.start() == null
        || chunk.end() == null
        || !chunk.end().isAfter(chunk.start())
        || chunk.sampleRateHz() <= 0
        || chunk.sampleCount() <= 0
        || chunk.sampleOffset() < 0
        || payload == null
        || byteLength <= 0
        || chunk.encoding() == null
        || !(chunk.encoding().equals("F64_LE") || chunk.encoding().equals("F64_LE_GZIP")))
      throw new IllegalArgumentException("Invalid waveform metadata");
    double actualSeconds =
        java.time.Duration.between(chunk.start(), chunk.end()).toNanos() / 1_000_000_000.0;
    double expectedSeconds = chunk.sampleCount() / chunk.sampleRateHz();
    if (Math.abs(actualSeconds - expectedSeconds) > 0.000002)
      throw new IllegalArgumentException("Chunk duration does not match sample count and rate");
    if (chunk.encoding().equals("F64_LE") && byteLength != (long) chunk.sampleCount() * 8)
      throw new IllegalArgumentException("Raw payload length does not match sample count");
    return transactions.execute(
        status -> {
          Connection c = DataSourceUtils.getConnection(dataSource);
          String statement =
              "INSERT INTO"
                  + " waveform_chunks(event_id,admission_id,device_id,metric,unit,observed_at,received_at,sample_rate_hz,samples,source_id,recording_id,quality,sample_count,encoding,compressed_payload,checksum,sample_offset,chunk_end_at)"
                  + " VALUES(?,?,?,?,?,?,?, ?,NULL,?,?,'GOOD',?,?,?,?,?,?)";
          try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream checked = new DigestInputStream(payload, sha);
                PreparedStatement ps =
                    c.prepareStatement(statement, Statement.RETURN_GENERATED_KEYS)) {
              ps.setString(1, eventId);
              ps.setLong(2, chunk.admissionId());
              ps.setLong(3, chunk.deviceId());
              ps.setString(4, chunk.metric().name());
              ps.setString(5, unit);
              ps.setTimestamp(6, Timestamp.from(chunk.start()));
              ps.setTimestamp(7, Timestamp.from(Instant.now()));
              ps.setDouble(8, chunk.sampleRateHz());
              ps.setLong(9, sourceId);
              if (chunk.sourceRecordingId() == null) ps.setNull(10, java.sql.Types.BIGINT);
              else ps.setLong(10, chunk.sourceRecordingId());
              ps.setInt(11, chunk.sampleCount());
              ps.setString(12, chunk.encoding());
              ps.setBinaryStream(13, checked, byteLength);
              ps.setBytes(14, chunk.sha256());
              ps.setLong(15, chunk.sampleOffset());
              ps.setTimestamp(16, Timestamp.from(chunk.end()));
              ps.executeUpdate();
              if (!Arrays.equals(sha.digest(), chunk.sha256()))
                throw new IllegalArgumentException("Waveform SHA-256 mismatch");
              try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new IllegalStateException("Waveform key missing");
                return keys.getLong(1);
              }
            }
          } catch (java.sql.SQLException e) {
            status.setRollbackOnly();
            DataAccessException translated =
                new SQLStateSQLExceptionTranslator().translate("waveform insert", statement, e);
            throw translated == null
                ? new org.springframework.jdbc.UncategorizedSQLException(
                    "waveform insert", statement, e)
                : translated;
          } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
            status.setRollbackOnly();
            throw new IllegalStateException(e);
          } finally {
            DataSourceUtils.releaseConnection(c, dataSource);
          }
        });
  }

  public void streamRange(
      long admissionId, MeasurementCode metric, Instant from, Instant to, ChunkConsumer consumer) {
    if (from == null || to == null || !to.isAfter(from))
      throw new IllegalArgumentException("Invalid range");
    String statement =
        "SELECT"
            + " id,admission_id,device_id,metric,recording_id,observed_at,chunk_end_at,sample_rate_hz,sample_count,sample_offset,encoding,checksum,compressed_payload"
            + " FROM waveform_chunks WHERE admission_id=? AND metric=? AND observed_at<? AND"
            + " chunk_end_at>? AND compressed_payload IS NOT NULL ORDER BY observed_at,id";
    Connection c = DataSourceUtils.getConnection(dataSource);
    try (PreparedStatement ps = c.prepareStatement(statement)) {
      ps.setLong(1, admissionId);
      ps.setString(2, metric.name());
      ps.setTimestamp(3, Timestamp.from(to));
      ps.setTimestamp(4, Timestamp.from(from));
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          long recording = rs.getLong("recording_id");
          Long recordingId = rs.wasNull() ? null : recording;
          Chunk chunk =
              new Chunk(
                  rs.getLong("id"),
                  rs.getLong("admission_id"),
                  rs.getLong("device_id"),
                  MeasurementCode.valueOf(rs.getString("metric")),
                  recordingId,
                  rs.getTimestamp("observed_at").toInstant(),
                  rs.getTimestamp("chunk_end_at").toInstant(),
                  rs.getDouble("sample_rate_hz"),
                  rs.getInt("sample_count"),
                  rs.getLong("sample_offset"),
                  rs.getString("encoding"),
                  rs.getBytes("checksum"));
          try (InputStream stream = rs.getBinaryStream("compressed_payload")) {
            consumer.accept(chunk, stream);
          }
        }
      }
    } catch (java.sql.SQLException e) {
      DataAccessException translated =
          new SQLStateSQLExceptionTranslator().translate("waveform range", statement, e);
      throw translated == null
          ? new org.springframework.jdbc.UncategorizedSQLException("waveform range", statement, e)
          : translated;
    } catch (Exception e) {
      throw new IllegalStateException("Waveform consumer failed", e);
    } finally {
      DataSourceUtils.releaseConnection(c, dataSource);
    }
  }
}
