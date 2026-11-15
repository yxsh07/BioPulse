package org.biopulse.android.presentation;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import org.biopulse.android.BioPulseApp;
import org.biopulse.android.data.Repository;

public final class LoginViewModel extends AndroidViewModel {
  private final SavedStateHandle saved;
  private final Repository repository;
  private final MutableLiveData<String> message = new MutableLiveData<>("");
  private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
  private final MutableLiveData<Boolean> signedIn = new MutableLiveData<>(false);

  public LoginViewModel(Application app, SavedStateHandle saved) {
    super(app);
    this.saved = saved;
    repository = ((BioPulseApp) app).repository();
  }

  public LiveData<String> message() {
    return message;
  }

  public LiveData<Boolean> busy() {
    return busy;
  }

  public LiveData<Boolean> signedIn() {
    return signedIn;
  }

  public String email() {
    String value = saved.get("email");
    return value == null ? "" : value;
  }

  public String server() {
    return repository.api().origin();
  }

  public void login(String server, String email, String password) {
    if (Boolean.TRUE.equals(busy.getValue())) return;
    saved.set("email", email);
    try {
      repository.configure(server);
    } catch (IllegalArgumentException e) {
      message.setValue(e.getMessage());
      return;
    }
    if (email.trim().isEmpty() || password.isEmpty()) {
      message.setValue("Enter your staff email and password.");
      return;
    }
    busy.setValue(true);
    message.setValue("");
    repository.io.execute(
        () -> {
          String error = "";
          boolean ok = false;
          try {
            ok = repository.login(email.trim(), password).optBoolean("authenticated");
            if (!ok) error = "Sign in was not confirmed.";
          } catch (Exception e) {
            error =
                e.getMessage() == null
                    ? "Unable to reach the server. Check connection settings and retry."
                    : e.getMessage();
          }
          final String result = error;
          final boolean authenticated = ok;
          new Handler(Looper.getMainLooper())
              .post(
                  () -> {
                    busy.setValue(false);
                    message.setValue(result);
                    signedIn.setValue(authenticated);
                  });
        });
  }
}
