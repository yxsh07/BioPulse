package org.biopulse.android.presentation;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;

public final class PatientListViewModel extends ScreenViewModel {
  public PatientListViewModel(Application app, SavedStateHandle state) {
    super(app, state, "patients");
  }
}
