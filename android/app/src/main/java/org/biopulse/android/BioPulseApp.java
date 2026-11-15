package org.biopulse.android;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import org.biopulse.android.data.Repository;

public final class BioPulseApp extends Application {
  private Repository repository;

  @Override
  public void onCreate() {
    super.onCreate();
    AppCompatDelegate.setDefaultNightMode(
        preferences().getInt("theme", AppCompatDelegate.MODE_NIGHT_YES));
    repository = new Repository(this);
  }

  public SharedPreferences preferences() {
    return getSharedPreferences("workspace", MODE_PRIVATE);
  }

  public Repository repository() {
    return repository;
  }
}
