package org.biopulse.dao;

import java.util.*;
import org.biopulse.domain.Report;
import org.biopulse.dto.Requests.ReportCreate;

public interface ReportDao {
  record Artifact(long reportId, String mediaType, String fileUri, byte[] binaryContent) {
    public Artifact {
      if (binaryContent != null) binaryContent = binaryContent.clone();
    }

    @Override
    public byte[] binaryContent() {
      return binaryContent == null ? null : binaryContent.clone();
    }
  }

  Report create(ReportCreate r, String actor, String content);

  Report createArtifact(
      ReportCreate r, String actor, String mediaType, String fileUri, byte[] binaryContent);

  Optional<Artifact> artifactForActor(long reportId, String actor);

  Optional<Report> get(long id);

  String content(long id);

  List<Report> list(String actor, boolean admin, int limit, int offset);
}
