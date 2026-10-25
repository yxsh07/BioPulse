package org.biopulse.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.biopulse.dao.ProvenanceDao;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProvenanceDao implements ProvenanceDao {
  private final Sql sql;
  private final ObjectMapper json;

  public JdbcProvenanceDao(Sql sql, ObjectMapper json) {
    this.sql = sql;
    this.json = json;
  }

  public long createSource(SourceInput source) {
    if (source.name() == null
        || source.name().isBlank()
        || source.type() == null
        || source.provenance() == null
        || source.provenanceClass() == null)
      throw new IllegalArgumentException("Source identity and provenance are required");
    return sql.insert(
        "INSERT INTO"
            + " data_sources(name,type,provenance,provenance_class,publisher,dataset_name,dataset_version,license_name,attribution,original_url)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
        source.name(),
        source.type(),
        source.provenance(),
        source.provenanceClass(),
        source.publisher(),
        source.datasetName(),
        source.datasetVersion(),
        source.licenseName(),
        source.attribution(),
        source.originalUrl());
  }

  public long createRecording(RecordingInput recording) {
    if (recording.externalRecordingId() == null
        || recording.externalRecordingId().isBlank()
        || recording.startAt() == null
        || recording.endAt() == null
        || !recording.endAt().isAfter(recording.startAt())
        || (recording.sampleRateHz() != null && recording.sampleRateHz().signum() <= 0))
      throw new IllegalArgumentException("Invalid recording metadata");
    try {
      BigDecimal duration =
          BigDecimal.valueOf(ChronoUnit.MICROS.between(recording.startAt(), recording.endAt()), 6);
      return sql.insert(
          "INSERT INTO"
              + " source_recordings(source_id,external_recording_id,name,original_subject_ref,original_recording_ref,available_signals,sample_rate_hz,duration_seconds,start_at,end_at)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?)",
          recording.sourceId(),
          recording.externalRecordingId(),
          recording.name(),
          recording.originalSubjectRef(),
          recording.originalRecordingRef(),
          json.writeValueAsString(recording.availableSignals()),
          recording.sampleRateHz(),
          duration,
          recording.startAt(),
          recording.endAt());
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid signal list", e);
    }
  }

  public List<ReplayRecording> replayCatalog(int limit, int offset) {
    return sql.query(
        "SELECT"
            + " r.id,r.external_recording_id,r.name,s.publisher,s.dataset_name,s.dataset_version,s.license_name,s.attribution,s.original_url"
            + " FROM source_recordings r JOIN data_sources s ON s.id=r.source_id WHERE"
            + " s.provenance_class='ORIGINAL_RESEARCH' ORDER BY r.imported_at DESC,r.id DESC LIMIT"
            + " ? OFFSET ?",
        r ->
            new ReplayRecording(
                r.getLong("id"),
                r.getString("external_recording_id"),
                r.getString("name"),
                r.getString("publisher"),
                r.getString("dataset_name"),
                r.getString("dataset_version"),
                r.getString("license_name"),
                r.getString("attribution"),
                r.getString("original_url")),
        limit,
        offset);
  }
}
