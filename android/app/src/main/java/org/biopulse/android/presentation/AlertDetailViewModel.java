package org.biopulse.android.presentation;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;

public final class AlertDetailViewModel extends ScreenViewModel {
  public AlertDetailViewModel(Application app, SavedStateHandle state) {
    super(app, state, "alert-detail");
  }
}
