package org.biopulse.android.presentation;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;

public final class PatientDetailViewModel extends ScreenViewModel {
  public PatientDetailViewModel(Application app, SavedStateHandle state) {
    super(app, state, "patient");
  }
}
