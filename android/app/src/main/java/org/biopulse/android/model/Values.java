package org.biopulse.android.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Values {
  private Values() {}

  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss 'IST'", Locale.ENGLISH)
          .withZone(ZoneId.of("Asia/Kolkata"));

  public static long time(String value) {
    try {
      return Instant.parse(value).toEpochMilli();
    } catch (RuntimeException e) {
      return 0;
    }
  }

  public static String stamp(String value) {
    try {
      return STAMP.format(Instant.parse(value));
    } catch (RuntimeException e) {
      return "Unavailable";
    }
  }

  public static String number(JSONObject value, String key) {
    if (value == null || value.isNull(key)) return "—";
    double n = value.optDouble(key, Double.NaN);
    if (!Double.isFinite(n)) return "—";
    return String.format(Locale.ENGLISH, "%.2f", n).replaceAll("\\.?0+$", "");
  }

  public static JSONObject find(JSONArray values, String key, String match) {
    for (int i = 0; i < values.length(); i++) {
      JSONObject value = values.optJSONObject(i);
      if (value != null && value.optString(key).equals(match)) return value;
    }
    return new JSONObject();
  }

  public static JSONObject id(JSONArray values, long id) {
    return find(values, "id", Long.toString(id));
  }

  public static JSONObject admission(JSONArray values, long patientId) {
    JSONObject latest = new JSONObject();
    for (int i = 0; i < values.length(); i++) {
      JSONObject value = values.optJSONObject(i);
      if (value != null && value.optLong("patientId") == patientId) {
        if (value.optString("status").equals("ACTIVE")) return value;
        if (time(value.optString("admittedAt")) >= time(latest.optString("admittedAt")))
          latest = value;
      }
    }
    return latest;
  }

  public static String name(String code) {
    switch (code) {
      case "HR":
        return "Heart rate";
      case "SPO2":
        return "Oxygen saturation";
      case "RR":
        return "Respiratory rate";
      case "BP":
        return "Blood pressure";
      case "TEMP":
        return "Temperature";
      case "PULSE":
        return "Pulse rate";
      case "ECG":
        return "ECG";
      case "PPG":
        return "PPG";
      case "RESP":
        return "Respiration";
      default:
        return code;
    }
  }
}
