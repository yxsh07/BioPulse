package org.biopulse.android.presentation;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;

public final class AlertsViewModel extends ScreenViewModel {
  public AlertsViewModel(Application app, SavedStateHandle state) {
    super(app, state, "alerts");
  }
}
