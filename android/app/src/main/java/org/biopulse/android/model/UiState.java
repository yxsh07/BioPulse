package org.biopulse.android.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Documents are immutable strings; each consumer receives its own parsed copy. */
public final class UiState {
  public final boolean loading;
  public final String error;
  public final long receivedAt;
  public final Map<String, String> documents;

  public UiState(boolean loading, String error, Map<String, String> documents, long receivedAt) {
    this.loading = loading;
    this.error = error;
    this.receivedAt = receivedAt;
    this.documents = Collections.unmodifiableMap(new LinkedHashMap<>(documents));
  }

  public static UiState empty() {
    return new UiState(true, "", Collections.emptyMap(), 0);
  }

  public JSONObject object(String key) {
    try {
      return new JSONObject(documents.getOrDefault(key, "{}"));
    } catch (JSONException e) {
      return new JSONObject();
    }
  }

  public JSONArray array(String key) {
    try {
      return new JSONArray(documents.getOrDefault(key, "[]"));
    } catch (JSONException e) {
      return new JSONArray();
    }
  }

  public JSONArray items(String key) {
    return object(key).optJSONArray("items") == null
        ? new JSONArray()
        : object(key).optJSONArray("items");
  }
}
