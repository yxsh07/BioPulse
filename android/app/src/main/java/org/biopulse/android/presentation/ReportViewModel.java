package org.biopulse.android.presentation;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;

public final class ReportViewModel extends ScreenViewModel {
  public ReportViewModel(Application app, SavedStateHandle state) {
    super(app, state, "reports");
  }

  private byte[] prepared;
  private long preparedId;

  public byte[] preparedCsv() {
    return prepared == null ? null : prepared.clone();
  }

  public long preparedId() {
    return preparedId;
  }

  public void download(long id, java.util.function.Consumer<byte[]> complete) {
    repository.io.execute(
        () -> {
          try {
            byte[] data =
                repository
                    .api()
                    .get("/reports/" + id + "/download")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            new android.os.Handler(android.os.Looper.getMainLooper())
                .post(
                    () -> {
                      prepared = data;
                      preparedId = id;
                      complete.accept(data.clone());
                    });
          } catch (Exception e) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                .post(
                    () ->
                        error(
                            e.getMessage() == null
                                ? "Unable to download report."
                                : e.getMessage()));
          }
        });
  }

  @Override
  protected void onCleared() {
    prepared = null;
    super.onCleared();
  }
}
