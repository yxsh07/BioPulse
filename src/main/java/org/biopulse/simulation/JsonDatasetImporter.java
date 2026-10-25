package org.biopulse.simulation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.*;
import org.biopulse.dto.Requests.Observation;
import org.springframework.stereotype.Component;

/**
 * Imports explicit canonical observations; never infers absent channels or replaces missing values.
 */
@Component
public class JsonDatasetImporter implements DatasetImporter {
  private final ObjectMapper json;

  public JsonDatasetImporter(ObjectMapper json) {
    this.json = json;
  }

  public List<Observation> parse(InputStream input) throws IOException {
    return json.readValue(input, new TypeReference<List<Observation>>() {});
  }
}
