package org.biopulse.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.biopulse.dao.MeasurementDao;
import org.biopulse.domain.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMeasurementDao implements MeasurementDao {
  private final Sql sql;
  private final ObjectMapper json;

  public JdbcMeasurementDao(Sql sql, ObjectMapper json) {
    this.sql = sql;
    this.json = json;
  }

  public long insertConverted(ConvertedObservation m) {
    if (m.metric() == null
        || m.metric().waveform()
        || m.recordedAt() == null
        || m.receivedAt() == null
        || m.quality() == null
        || m.canonicalUnit() == null
        || (m.valueNumeric() == null
            && m.quality() != QualityFlag.MISSING
            && m.quality() != QualityFlag.INVALID)
        || (m.valueNumeric() != null
            && (m.originalValue() == null
                || m.originalUnit() == null
                || m.conversionMethod() == null
                || m.conversionMethod().isBlank())))
      throw new IllegalArgumentException("Invalid converted observation");
    String canonical =
        sql
            .query(
                "SELECT canonical_unit FROM measurement_types WHERE metric_code=?",
                r -> r.getString(1),
                m.metric())
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown metric"));
    if (!canonical.equals(m.canonicalUnit()))
      throw new IllegalArgumentException("Noncanonical unit");
    return sql.insert(
        "INSERT INTO"
            + " measurements(event_id,admission_id,device_id,metric,value,value_numeric,unit,original_value,original_unit,conversion_method,observed_at,received_at,source_id,recording_id,quality)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        m.ingestionEventId(),
        m.admissionId(),
        m.deviceId(),
        m.metric(),
        m.valueNumeric(),
        m.valueNumeric(),
        m.canonicalUnit(),
        m.originalValue(),
        m.originalUnit(),
        m.conversionMethod(),
        m.recordedAt(),
        m.receivedAt(),
        m.sourceId(),
        m.sourceRecordingId(),
        m.quality());
  }

  private final Sql.Mapper<Measurement> mapper =
      r ->
          new Measurement(
              r.getLong("id"),
              r.getString("event_id"),
              r.getLong("admission_id"),
              r.getLong("device_id"),
              MeasurementCode.valueOf(r.getString("metric")),
              Sql.number(r, "value"),
              r.getString("unit"),
              Sql.instant(r, "observed_at"),
              Sql.instant(r, "received_at"),
              r.getLong("source_id"),
              Sql.nullableLong(r, "recording_id"),
              QualityFlag.valueOf(r.getString("quality")));

  private WaveformChunk waveform(java.sql.ResultSet r) throws java.sql.SQLException {
    try {
      return new WaveformChunk(
          r.getLong("id"),
          r.getString("event_id"),
          r.getLong("admission_id"),
          r.getLong("device_id"),
          MeasurementCode.valueOf(r.getString("metric")),
          r.getString("unit"),
          Sql.instant(r, "observed_at"),
          Sql.instant(r, "received_at"),
          r.getDouble("sample_rate_hz"),
          samples(r),
          r.getLong("source_id"),
          Sql.nullableLong(r, "recording_id"),
          QualityFlag.valueOf(r.getString("quality")));
    } catch (java.io.IOException e) {
      throw new java.sql.SQLException("Invalid stored samples", e);
    }
  }

  private List<Double> samples(java.sql.ResultSet r)
      throws java.sql.SQLException, java.io.IOException {
    String legacy = r.getString("samples");
    if (legacy != null) return json.readValue(legacy, new TypeReference<List<Double>>() {});
    byte[] encoded = r.getBytes("compressed_payload");
    if (encoded == null) throw new java.sql.SQLException("Waveform has no payload");
    try {
      if (!Arrays.equals(
          MessageDigest.getInstance("SHA-256").digest(encoded), r.getBytes("checksum")))
        throw new java.sql.SQLException("Waveform checksum mismatch");
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new java.sql.SQLException(e);
    }
    InputStream input = new java.io.ByteArrayInputStream(encoded);
    if ("F64_LE_GZIP".equals(r.getString("encoding"))) input = new GZIPInputStream(input);
    else if (!"F64_LE".equals(r.getString("encoding")))
      throw new java.sql.SQLException("Unknown waveform encoding");
    int count = r.getInt("sample_count");
    if (count < 0 || count > 500_000)
      throw new java.sql.SQLException("Chunk too large for legacy JSON API");
    byte[] raw;
    try (InputStream stream = input) {
      raw = stream.readNBytes(count * 8 + 1);
    }
    if (raw.length != count * 8) throw new java.sql.SQLException("Waveform sample count mismatch");
    ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
    List<Double> values = new ArrayList<>(count);
    for (int i = 0; i < count; i++) values.add(buffer.getDouble());
    return values;
  }

  public List<Measurement> insertBatch(List<Measurement> values) {
    var keys =
        sql.batch(
            "INSERT INTO"
                + " measurements(event_id,admission_id,device_id,metric,value,unit,observed_at,received_at,source_id,recording_id,quality)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            values.stream()
                .map(
                    m ->
                        new Object[] {
                          m.eventId(),
                          m.admissionId(),
                          m.deviceId(),
                          m.metric(),
                          m.value(),
                          m.unit(),
                          m.observedAt(),
                          m.receivedAt(),
                          m.sourceId(),
                          m.recordingId(),
                          m.quality()
                        })
                .toList());
    List<Measurement> saved = new ArrayList<>();
    for (int i = 0; i < values.size(); i++) {
      var m = values.get(i);
      saved.add(
          new Measurement(
              keys.get(i),
              m.eventId(),
              m.admissionId(),
              m.deviceId(),
              m.metric(),
              m.value(),
              m.unit(),
              m.observedAt(),
              m.receivedAt(),
              m.sourceId(),
              m.recordingId(),
              m.quality()));
    }
    return List.copyOf(saved);
  }

  public WaveformChunk insertWaveform(WaveformChunk c) {
    try {
      long id =
          sql.insert(
              "INSERT INTO"
                  + " waveform_chunks(event_id,admission_id,device_id,metric,unit,observed_at,received_at,sample_rate_hz,samples,source_id,recording_id,quality)"
                  + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
              c.eventId(),
              c.admissionId(),
              c.deviceId(),
              c.metric(),
              c.unit(),
              c.observedAt(),
              c.receivedAt(),
              c.sampleRateHz(),
              json.writeValueAsString(c.samples()),
              c.sourceId(),
              c.recordingId(),
              c.quality());
      return new WaveformChunk(
          id,
          c.eventId(),
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
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public List<Measurement> latest(long id) {
    return sql.query(
        "SELECT * FROM (SELECT m.*,ROW_NUMBER() OVER(PARTITION BY metric ORDER BY observed_at"
            + " DESC,id DESC) rn FROM measurements m WHERE admission_id=?) ranked WHERE rn=1 ORDER"
            + " BY metric",
        mapper,
        id);
  }

  public List<Measurement> latestValid(long id) {
    return sql.query(
        "SELECT * FROM (SELECT m.*,ROW_NUMBER() OVER(PARTITION BY metric ORDER BY observed_at"
            + " DESC,id DESC) rn FROM measurements m WHERE admission_id=? AND quality IN"
            + " ('GOOD','FAIR')) ranked WHERE rn=1 ORDER BY metric",
        mapper,
        id);
  }

  public List<Measurement> history(
      long id, MeasurementCode code, Instant from, Instant to, int limit, int offset) {
    return sql.query(
        "SELECT * FROM measurements WHERE admission_id=? AND metric=? AND observed_at>=? AND"
            + " observed_at<? ORDER BY observed_at,id LIMIT ? OFFSET ?",
        mapper,
        id,
        code,
        from,
        to,
        limit,
        offset);
  }

  public List<WaveformChunk> waveforms(
      long id, MeasurementCode code, Instant from, Instant to, int limit, int offset) {
    return sql.query(
        "SELECT * FROM waveform_chunks WHERE admission_id=? AND metric=? AND observed_at>=? AND"
            + " observed_at<? ORDER BY observed_at,id LIMIT ? OFFSET ?",
        this::waveform,
        id,
        code,
        from,
        to,
        limit,
        offset);
  }

  public List<Measurement> recordingMeasurements(long id, Instant from, Instant to, int limit) {
    return sql.query(
        "SELECT * FROM measurements WHERE recording_id=? AND observed_at>=? AND observed_at<? ORDER"
            + " BY observed_at,id LIMIT ?",
        mapper,
        id,
        from,
        to,
        limit);
  }

  public List<WaveformChunk> recordingWaveforms(long id, Instant from, Instant to, int limit) {
    return sql.query(
        "SELECT * FROM waveform_chunks WHERE recording_id=? AND observed_at>=? AND observed_at<?"
            + " ORDER BY observed_at,id LIMIT ?",
        this::waveform,
        id,
        from,
        to,
        limit);
  }
}
