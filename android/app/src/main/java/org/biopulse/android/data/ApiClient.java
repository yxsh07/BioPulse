package org.biopulse.android.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

/** One session-bound client. Credentials, session cookies and CSRF never reach disk or logs. */
public final class ApiClient {
  public static final class ApiException extends IOException {
    public final int status;

    public ApiException(int status, String detail) {
      super(detail);
      this.status = status;
    }
  }

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  private final SessionCookies cookies = new SessionCookies();
  private final OkHttpClient http =
      new OkHttpClient.Builder()
          .cookieJar(cookies)
          .connectTimeout(10, TimeUnit.SECONDS)
          .readTimeout(20, TimeUnit.SECONDS)
          .callTimeout(30, TimeUnit.SECONDS)
          .followRedirects(false)
          .followSslRedirects(false)
          .build();
  private final HttpUrl base;
  private volatile String csrf = "";
  private volatile Runnable expired;

  public ApiClient(String origin, boolean debug) {
    base = validateOrigin(origin, debug);
  }

  public String origin() {
    return base.toString().replaceAll("/$", "");
  }

  public OkHttpClient http() {
    return http;
  }

  public void onSessionExpired(Runnable listener) {
    expired = listener;
  }

  public HttpUrl endpoint(String path) {
    if (!path.startsWith("/") || path.startsWith("//"))
      throw new IllegalArgumentException("Invalid API path");
    HttpUrl url = base.resolve("/api/v1" + path);
    if (url == null || !url.host().equals(base.host()))
      throw new IllegalArgumentException("Invalid API path");
    return url;
  }

  public String get(String path) throws IOException {
    return send("GET", path, null);
  }

  public String post(String path, JSONObject body) throws IOException {
    return send("POST", path, body);
  }

  public JSONObject session() throws IOException {
    try {
      JSONObject value = new JSONObject(get("/session"));
      csrf = value.getString("csrfToken");
      return value;
    } catch (JSONException e) {
      throw new IOException("Invalid session response", e);
    }
  }

  public JSONObject login(String username, String password) throws IOException {
    session();
    try {
      post("/session", new JSONObject().put("username", username).put("password", password));
      return session();
    } catch (JSONException e) {
      throw new IOException("Unable to sign in", e);
    }
  }

  public void logout() throws IOException {
    try {
      post("/logout", new JSONObject());
    } finally {
      clear();
    }
  }

  public void clear() {
    csrf = "";
    cookies.clear();
    http.dispatcher().cancelAll();
  }

  public String cookieHeader(HttpUrl url) {
    List<String> parts = new ArrayList<>();
    for (Cookie cookie : cookies.loadForRequest(url))
      parts.add(cookie.name() + "=" + cookie.value());
    return String.join("; ", parts);
  }

  private String send(String method, String path, JSONObject body) throws IOException {
    Request.Builder builder =
        new Request.Builder().url(endpoint(path)).header("Accept", "application/json, text/csv");
    if (!"GET".equals(method))
      builder
          .header("X-XSRF-TOKEN", csrf)
          .method(method, RequestBody.create(body == null ? "{}" : body.toString(), JSON));
    try (Response response = http.newCall(builder.build()).execute()) {
      String text = response.body() == null ? "" : response.body().string();
      if (!response.isSuccessful()) {
        String detail = "Request failed (" + response.code() + ")";
        try {
          JSONObject problem = new JSONObject(text);
          detail = problem.optString("detail", problem.optString("title", detail));
        } catch (JSONException ignored) {
          /* Never render a server HTML error page. */
        }
        if (response.code() == 401) {
          cookies.clear();
          csrf = "";
          if (expired != null) expired.run();
        }
        throw new ApiException(
            response.code(),
            detail
                .replaceAll("[\\r\\n\\t]", " ")
                .substring(0, Math.min(detail.replaceAll("[\\r\\n\\t]", " ").length(), 300)));
      }
      return text;
    }
  }

  public static HttpUrl validateOrigin(String input, boolean debug) {
    HttpUrl value = HttpUrl.parse(input.trim());
    if (value == null
        || !value.username().isEmpty()
        || !value.password().isEmpty()
        || value.query() != null
        || value.fragment() != null
        || !"/".equals(value.encodedPath()))
      throw new IllegalArgumentException("Enter a server origin, for example http://10.0.2.2:8080");
    if ("http".equals(value.scheme()) && !(debug && privateHost(value.host())))
      throw new IllegalArgumentException(
          "HTTPS is required except for local/private debug servers");
    return value;
  }

  private static boolean privateHost(String host) {
    if (host.equals("localhost") || host.equals("::1")) return true;
    String[] parts = host.split("\\.");
    if (parts.length != 4) return false;
    int[] bytes = new int[4];
    try {
      for (int i = 0; i < 4; i++) {
        bytes[i] = Integer.parseInt(parts[i]);
        if (bytes[i] < 0 || bytes[i] > 255) return false;
      }
    } catch (NumberFormatException e) {
      return false;
    }
    return bytes[0] == 10
        || bytes[0] == 127
        || bytes[0] == 192 && bytes[1] == 168
        || bytes[0] == 172 && bytes[1] >= 16 && bytes[1] <= 31;
  }

  private static final class SessionCookies implements CookieJar {
    private final Map<String, Cookie> values = new LinkedHashMap<>();

    @Override
    public synchronized void saveFromResponse(HttpUrl url, List<Cookie> incoming) {
      for (Cookie cookie : incoming)
        values.put(cookie.name() + "|" + cookie.domain() + "|" + cookie.path(), cookie);
      values.values().removeIf(c -> c.expiresAt() <= System.currentTimeMillis());
    }

    @Override
    public synchronized List<Cookie> loadForRequest(HttpUrl url) {
      List<Cookie> result = new ArrayList<>();
      for (Cookie cookie : values.values())
        if (cookie.expiresAt() > System.currentTimeMillis() && cookie.matches(url))
          result.add(cookie);
      return result;
    }

    synchronized void clear() {
      values.clear();
    }
  }
}
