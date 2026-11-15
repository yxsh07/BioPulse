package org.biopulse.android.presentation;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import java.util.Map;
import java.util.function.Consumer;
import org.biopulse.android.BioPulseApp;
import org.biopulse.android.data.ApiClient;
import org.biopulse.android.data.Repository;
import org.biopulse.android.model.UiState;
import org.json.JSONObject;

public abstract class ScreenViewModel extends AndroidViewModel {
  protected final Repository repository;
  protected final SavedStateHandle saved;
  private final String screen;
  private final MutableLiveData<UiState> state = new MutableLiveData<>(UiState.empty());
  private final MutableLiveData<String> connection = new MutableLiveData<>("DISCONNECTED");
  private final Handler main = new Handler(Looper.getMainLooper());
  private Repository.Stream stream;
  private boolean active, cleared, loading, mutating;
  private int generation;
  private String scope = "";
  private String inFlight = "";
  private final Runnable poll =
      new Runnable() {
        @Override
        public void run() {
          if (active) {
            refresh();
            main.postDelayed(this, 3000);
          }
        }
      };

  public ScreenViewModel(Application application, SavedStateHandle saved, String screen) {
    super(application);
    this.saved = saved;
    this.screen = screen;
    repository = ((BioPulseApp) application).repository();
  }

  public LiveData<UiState> state() {
    return state;
  }

  public LiveData<String> connection() {
    return connection;
  }

  public boolean streaming() {
    return java.util.Arrays.asList("patient", "monitor", "trends").contains(screen);
  }

  public String value(String key, String fallback) {
    Object v = saved.get(key);
    return v == null ? fallback : String.valueOf(v);
  }

  public void set(String key, String value) {
    saved.set(key, value);
  }

  public void remove(String key) {
    saved.remove(key);
  }

  public void selectReplay(String id) {
    set("replay", id);
    for (String key : new String[] {"from", "to", "inspectFrom", "inspectTo"}) saved.remove(key);
    set("page", "0");
    refresh();
  }

  public void start() {
    active = true;
    refresh();
    long admission = 0;
    try {
      admission = Long.parseLong(value("admissionId", "0"));
    } catch (NumberFormatException ignored) {
    }
    if (admission > 0 && java.util.Arrays.asList("patient", "monitor", "trends").contains(screen)) {
      stream = repository.stream(admission, connection::setValue, this::refresh);
      main.removeCallbacks(poll);
      main.postDelayed(poll, 3000);
    }
  }

  public void stop() {
    active = false;
    main.removeCallbacks(poll);
    if (stream != null) {
      stream.close();
      stream = null;
    }
    connection.setValue("DISCONNECTED");
  }

  public void refresh() {
    if (cleared || mutating) return;
    Map<String, String> parameters = new java.util.TreeMap<>();
    for (String key : saved.keys()) {
      Object value = saved.get(key);
      if (value != null) parameters.put(key, String.valueOf(value));
    }
    if (loading && inFlight.equals(parameters.toString())) return;
    inFlight = parameters.toString();
    String nextScope =
        parameters.getOrDefault("admissionId", "")
            + ":"
            + parameters.getOrDefault("eventId", "")
            + ":"
            + parameters.getOrDefault("replay", "auto");
    UiState old = state.getValue() == null ? UiState.empty() : state.getValue();
    if (!scope.equals(nextScope)) {
      old = UiState.empty();
      scope = nextScope;
    }
    final UiState previous = old;
    final int token = ++generation;
    loading = true;
    state.setValue(new UiState(true, "", old.documents, old.receivedAt));
    repository.io.execute(
        () -> {
          try {
            Map<String, String> data = repository.load(screen, parameters);
            main.post(
                () -> {
                  if (!cleared && token == generation) {
                    loading = false;
                    state.setValue(new UiState(false, "", data, System.currentTimeMillis()));
                  }
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (!cleared && token == generation) {
                    loading = false;
                    String message =
                        e.getMessage() == null
                            ? "Unable to reach the server. Retry when connected."
                            : e.getMessage();
                    if (e instanceof ApiClient.ApiException
                        && ((ApiClient.ApiException) e).status == 401) {
                      repository.clearSession();
                      message = "Session expired. Sign in again.";
                    }
                    state.setValue(
                        new UiState(false, message, previous.documents, previous.receivedAt));
                  }
                });
          }
        });
  }

  public void mutate(String path, JSONObject body, Consumer<String> complete) {
    if (cleared || mutating) return;
    mutating = true;
    generation++;
    UiState previous = state.getValue() == null ? UiState.empty() : state.getValue();
    loading = true;
    state.setValue(new UiState(true, "", previous.documents, previous.receivedAt));
    repository.io.execute(
        () -> {
          try {
            String result = repository.api().post(path, body);
            main.post(
                () -> {
                  if (!cleared) {
                    loading = false;
                    mutating = false;
                    complete.accept(result);
                    refresh();
                  }
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (!cleared) {
                    loading = false;
                    mutating = false;
                    if (e instanceof ApiClient.ApiException
                        && ((ApiClient.ApiException) e).status == 401) repository.clearSession();
                    state.setValue(
                        new UiState(
                            false,
                            e.getMessage() == null
                                ? "Action could not be confirmed. Refresh before retrying."
                                : e.getMessage(),
                            previous.documents,
                            previous.receivedAt));
                  }
                });
          }
        });
  }

  public void error(String message) {
    UiState old = state.getValue();
    if (old != null) state.setValue(new UiState(false, message, old.documents, old.receivedAt));
  }

  @Override
  protected void onCleared() {
    cleared = true;
    generation++;
    stop();
    main.removeCallbacksAndMessages(null);
    super.onCleared();
  }
}
