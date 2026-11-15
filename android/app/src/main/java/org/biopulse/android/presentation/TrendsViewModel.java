package org.biopulse.android.presentation;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;

public final class TrendsViewModel extends ScreenViewModel {
  public TrendsViewModel(Application app, SavedStateHandle state) {
    super(app, state, "trends");
  }
}
