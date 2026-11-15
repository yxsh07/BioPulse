package org.biopulse.android.presentation;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;

public final class MonitorViewModel extends ScreenViewModel {
  public MonitorViewModel(Application app, SavedStateHandle state) {
    super(app, state, "monitor");
  }
}
