package org.biopulse.android.presentation;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;

public final class ProfileViewModel extends ScreenViewModel {
  public ProfileViewModel(Application app, SavedStateHandle state) {
    super(app, state, "profile");
  }
}
