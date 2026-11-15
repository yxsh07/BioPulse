package org.biopulse.android.presentation;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;

public final class OverviewViewModel extends ScreenViewModel {
  public OverviewViewModel(Application app, SavedStateHandle state) {
    super(app, state, "overview");
  }
}
