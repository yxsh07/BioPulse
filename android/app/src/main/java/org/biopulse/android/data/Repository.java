package org.biopulse.android.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.biopulse.android.BuildConfig;
import org.biopulse.android.model.Values;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class Repository {
  public final ExecutorService io = Executors.newFixedThreadPool(4);
  private final Context context;
  private volatile ApiClient api;
  private volatile String identity = "{}";

  public Repository(Context context) {
    this.context = context.getApplicationContext();
    api =
        new ApiClient(
            context
                .getSharedPreferences("workspace", Context.MODE_PRIVATE)
                .getString("server", BuildConfig.DEFAULT_SERVER),
            BuildConfig.DEBUG);
    watchSession(api);
  }

  public ApiClient api() {
    return api;
  }

  public boolean authenticated() {
    return session().optBoolean("authenticated");
  }

  public JSONObject session() {
    try {
      return new JSONObject(identity);
    } catch (JSONException e) {
      return new JSONObject();
    }
  }

  public boolean admin() {
    return session().optJSONArray("roles") != null
        && session().optJSONArray("roles").toString().contains("ROLE_ADMIN");
  }

  public void configure(String origin) {
    ApiClient next = new ApiClient(origin, BuildConfig.DEBUG);
    api.clear();
    api = next;
    identity = "{}";
    watchSession(next);
    context
        .getSharedPreferences("workspace", Context.MODE_PRIVATE)
        .edit()
        .putString("server", api.origin())
        .apply();
  }

  public JSONObject login(String email, String password) throws IOException {
    JSONObject s = api.login(email, password);
    identity = s.toString();
    return s;
  }

  private void watchSession(ApiClient client) {
    client.onSessionExpired(
        () -> {
          if (api == client) {
            identity = "{}";
            clearExports();
          }
        });
  }

  public void clearSession() {
    api.clear();
    identity = "{}";
    clearExports();
  }

  public void logout() throws IOException {
    try {
      api.logout();
    } finally {
      clearSession();
    }
  }

  private void clearExports() {
    java.io.File directory = new java.io.File(context.getCacheDir(), "exports");
    java.io.File[] files = directory.listFiles();
    if (files != null) for (java.io.File file : files) if (file.isFile()) file.delete();
  }

  public Map<String, String> load(String screen, Map<String, String> p) throws IOException {
    Map<String, String> out = new LinkedHashMap<>();
    try {
      switch (screen) {
        case "overview":
        case "patients":
          fetch(out, "patients", "/patients?size=25&page=" + page(p));
          fetch(out, "admissions", "/admissions?size=100");
          fetch(out, "beds", "/beds");
          fetch(out, "wards", "/wards");
          fetch(out, "devices", "/devices");
          fetch(out, "alerts", "/alerts?size=100");
          if (screen.equals("patients")) {
            JSONArray patients = object(out, "patients").getJSONArray("items");
            JSONArray admissions = object(out, "admissions").getJSONArray("items");
            JSONArray sessions =
                new JSONObject(api.get("/simulation-sessions?size=100")).getJSONArray("items");
            for (int i = 0; i < patients.length(); i++) {
              long id = patients.getJSONObject(i).getLong("id");
              for (int j = 0; j < admissions.length(); j++) {
                JSONObject a = admissions.getJSONObject(j);
                if (a.optLong("patientId") == id && a.optString("status").equals("ACTIVE")) {
                  long admission = a.getLong("id");
                  JSONObject s = Values.find(sessions, "admissionId", Long.toString(admission));
                  String filter =
                      s.has("id")
                          ? "?recordingId="
                              + s.getLong("outputRecordingId")
                              + "&at="
                              + q(s.getString("cursorAt"))
                          : "";
                  fetch(
                      out,
                      "latest-" + admission,
                      "/admissions/" + admission + "/measurements/latest" + filter);
                  break;
                }
              }
            }
          }
          break;
        case "patient":
        case "monitor":
        case "trends":
        case "reports":
          JSONObject scope = scope(out, p);
          long admission = scope.getLong("admissionId");
          if (screen.equals("patient") || screen.equals("monitor")) {
            String filter =
                scope.has("recordingId")
                    ? "?recordingId="
                        + scope.getLong("recordingId")
                        + "&at="
                        + q(scope.getString("cursorAt"))
                    : "";
            fetch(out, "latest", "/admissions/" + admission + "/measurements/latest" + filter);
            for (String metric : new String[] {"ECG", "PPG", "RESP"})
              fetch(
                  out,
                  metric,
                  "/admissions/"
                      + admission
                      + "/waveforms?metric="
                      + metric
                      + "&size=100&from="
                      + q(scope.getString("signalFrom"))
                      + "&to="
                      + q(scope.getString("signalTo"))
                      + recordingFilter(scope));
          } else if (screen.equals("trends")) {
            String metric = p.getOrDefault("metric", "HR");
            String query =
                "metric="
                    + q(metric)
                    + "&from="
                    + q(scope.getString("from"))
                    + "&to="
                    + q(scope.getString("to"))
                    + recordingFilter(scope);
            fetch(
                out,
                "analytics",
                "/admissions/"
                    + admission
                    + "/analytics?"
                    + query
                    + "&cadenceSeconds="
                    + q(p.getOrDefault("cadence", "10")));
            fetch(
                out,
                "history",
                "/admissions/"
                    + admission
                    + "/measurements?"
                    + query
                    + "&size=1000&page="
                    + page(p));
            if (!p.getOrDefault("compareMetric", "").isEmpty())
              fetch(
                  out,
                  "comparison",
                  "/admissions/"
                      + admission
                      + "/measurements?metric="
                      + q(p.get("compareMetric"))
                      + "&from="
                      + q(scope.getString("from"))
                      + "&to="
                      + q(scope.getString("to"))
                      + recordingFilter(scope)
                      + "&size=1000&page="
                      + page(p));
          } else fetch(out, "reports", "/reports?size=25&page=" + page(p));
          break;
        case "alerts":
          fetch(
              out,
              "alerts",
              p.containsKey("admissionId")
                  ? "/admissions/" + positive(p, "admissionId") + "/alerts?size=25&page=" + page(p)
                  : "/alerts?size=25&page=" + page(p));
          break;
        case "alert-detail":
          fetch(out, "detail", "/alerts/" + positive(p, "eventId"));
          break;
        case "profile":
          JSONObject s = api.session();
          identity = s.toString();
          out.put("session", s.toString());
          break;
        default:
          throw new IOException("Unknown screen");
      }
      return Collections.unmodifiableMap(out);
    } catch (JSONException | IllegalArgumentException e) {
      throw new IOException("Invalid or incomplete server response", e);
    }
  }

  private JSONObject scope(Map<String, String> out, Map<String, String> p)
      throws IOException, JSONException {
    long admission = positive(p, "admissionId");
    fetch(out, "admission", "/admissions/" + admission);
    fetch(out, "patient", "/patients/" + object(out, "admission").getLong("patientId"));
    fetch(out, "beds", "/beds");
    fetch(out, "wards", "/wards");
    fetch(out, "recordings", "/admissions/" + admission + "/recordings");
    fetch(out, "sessions", "/simulation-sessions?admissionId=" + admission + "&size=100");
    JSONArray sessions = object(out, "sessions").getJSONArray("items");
    JSONArray recordings = new JSONArray(out.get("recordings"));
    String selected = p.getOrDefault("replay", "auto");
    JSONObject session = new JSONObject();
    if (selected.equals("auto") && sessions.length() > 0) session = sessions.getJSONObject(0);
    else if (!selected.equals("auto") && !selected.equals("latest")) {
      session = new JSONObject(api.get("/simulation-sessions/" + positive(p, "replay")));
      if (session.getLong("admissionId") != admission)
        throw new IOException("Replay does not belong to this admission");
    }
    JSONObject recording =
        session.has("id")
            ? Values.id(recordings, session.getLong("outputRecordingId"))
            : new JSONObject();
    if (!session.has("id"))
      for (int i = 0; i < recordings.length(); i++) {
        JSONObject candidate = recordings.getJSONObject(i);
        if (Values.time(candidate.optString("endAt")) > Values.time(recording.optString("endAt")))
          recording = candidate;
      }
    Instant end =
        recording.has("endAt") ? Instant.parse(recording.getString("endAt")) : Instant.now();
    Instant start =
        recording.has("startAt")
            ? Instant.parse(recording.getString("startAt"))
            : end.minusSeconds(60);
    Instant cursor = session.has("id") ? Instant.parse(session.getString("cursorAt")) : end;
    if (cursor.isAfter(end)) cursor = end;
    Instant signalTo = cursor.plusMillis(1);
    if (!signalTo.isAfter(start)) signalTo = start.plusMillis(1);
    Instant signalFrom = signalTo.minusSeconds(10);
    if (signalFrom.isBefore(start)) signalFrom = start;
    if (p.containsKey("inspectFrom") && p.containsKey("inspectTo")) {
      signalFrom = Instant.parse(p.get("inspectFrom"));
      signalTo = Instant.parse(p.get("inspectTo"));
    }
    String from = p.getOrDefault("from", start.toString()),
        to = p.getOrDefault("to", end.toString());
    validateRange(from, to);
    JSONObject scope =
        new JSONObject()
            .put("admissionId", admission)
            .put("recording", recording)
            .put("session", session)
            .put("from", from)
            .put("to", to)
            .put("signalFrom", signalFrom.toString())
            .put("signalTo", signalTo.toString());
    if (session.has("id"))
      scope
          .put("recordingId", session.getLong("outputRecordingId"))
          .put("cursorAt", session.getString("cursorAt"));
    out.put("scope", scope.toString());
    return scope;
  }

  public static void validateRange(String from, String to) {
    Instant first = Instant.parse(from), last = Instant.parse(to);
    if (!last.isAfter(first)
        || java.time.Duration.between(first, last).compareTo(java.time.Duration.ofDays(31)) > 0)
      throw new IllegalArgumentException("Choose a positive interval of at most 31 days");
  }

  private void fetch(Map<String, String> out, String key, String path) throws IOException {
    out.put(key, api.get(path));
  }

  private static JSONObject object(Map<String, String> out, String key) throws JSONException {
    return new JSONObject(out.get(key));
  }

  private static String recordingFilter(JSONObject scope) {
    return scope.has("recordingId") ? "&recordingId=" + scope.optLong("recordingId") : "";
  }

  public static String q(String text) {
    try {
      return URLEncoder.encode(text, "UTF-8");
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static int page(Map<String, String> p) {
    return Math.max(0, Integer.parseInt(p.getOrDefault("page", "0")));
  }

  private static long positive(Map<String, String> p, String name) throws IOException {
    try {
      long id = Long.parseLong(p.getOrDefault(name, "0"));
      if (id > 0) return id;
    } catch (NumberFormatException ignored) {
    }
    throw new IOException("Select a valid record");
  }

  public Stream stream(long admission, Consumer<String> status, Runnable refresh) {
    return new Stream(admission, status, refresh);
  }

  public final class Stream extends WebSocketListener implements AutoCloseable {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long admission;
    private final Consumer<String> status;
    private final Runnable refresh;
    private WebSocket socket;
    private volatile boolean closed;
    private long delay = 1000;
    private boolean pending;

    Stream(long admission, Consumer<String> status, Runnable refresh) {
      this.admission = admission;
      this.status = status;
      this.refresh = refresh;
      connect();
    }

    private void connect() {
      if (closed) return;
      status.accept("CONNECTING");
      HttpUrl url = HttpUrl.get(api.origin() + "/ws/monitoring?admissionId=" + admission);
      socket =
          api.http()
              .newWebSocket(
                  new Request.Builder().url(url).header("Cookie", api.cookieHeader(url)).build(),
                  this);
    }

    @Override
    public void onOpen(WebSocket socket, Response response) {
      handler.post(
          () -> {
            if (!closed) {
              delay = 1000;
              status.accept("CONNECTED");
              refresh.run();
            }
          });
    }

    @Override
    public void onMessage(WebSocket socket, String text) {
      try {
        String type = new JSONObject(text).optString("type");
        if (java.util.Arrays.asList("READING", "WAVEFORM", "SIMULATION", "ALERT", "DEVICE_STATUS")
            .contains(type))
          handler.post(
              () -> {
                if (!closed && !pending) {
                  pending = true;
                  handler.postDelayed(
                      () -> {
                        pending = false;
                        if (!closed) refresh.run();
                      },
                      500);
                }
              });
      } catch (JSONException ignored) {
        /* A malformed notification never becomes a fabricated observation. */
      }
    }

    @Override
    public void onClosed(WebSocket s, int code, String reason) {
      reconnect();
    }

    @Override
    public void onFailure(WebSocket s, Throwable t, Response r) {
      reconnect();
    }

    private void reconnect() {
      handler.post(
          () -> {
            if (closed) return;
            status.accept("DISCONNECTED");
            handler.postDelayed(this::connect, delay);
            delay = Math.min(delay * 2, 30000);
          });
    }

    @Override
    public void close() {
      closed = true;
      handler.removeCallbacksAndMessages(null);
      if (socket != null) socket.close(1000, "Screen stopped");
    }
  }
}
