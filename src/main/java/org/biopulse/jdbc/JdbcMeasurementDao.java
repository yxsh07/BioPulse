package org.biopulse.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
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
          json.readValue(r.getString("samples"), new TypeReference<List<Double>>() {}),
          r.getLong("source_id"),
          Sql.nullableLong(r, "recording_id"),
          QualityFlag.valueOf(r.getString("quality")));
    } catch (java.io.IOException e) {
      throw new java.sql.SQLException("Invalid stored samples", e);
    }
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
