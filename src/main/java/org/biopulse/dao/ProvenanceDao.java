package org.biopulse.dao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.biopulse.domain.SourceType;

public interface ProvenanceDao {
  record SourceInput(
      String name,
      SourceType type,
      String provenance,
      String provenanceClass,
      String publisher,
      String datasetName,
      String datasetVersion,
      String licenseName,
      String attribution,
      String originalUrl) {}

  record RecordingInput(
      long sourceId,
      String externalRecordingId,
      String name,
      String originalSubjectRef,
      String originalRecordingRef,
      List<String> availableSignals,
      BigDecimal sampleRateHz,
      Instant startAt,
      Instant endAt) {}

  record ReplayRecording(
      long id,
      String externalRecordingId,
      String name,
      String publisher,
      String datasetName,
      String datasetVersion,
      String licenseName,
      String attribution,
      String originalUrl) {}

  long createSource(SourceInput source);

  long createRecording(RecordingInput recording);

  List<ReplayRecording> replayCatalog(int limit, int offset);
}
