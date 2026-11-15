package org.biopulse.android;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public final class MainActivity extends AppCompatActivity {
  private NavController navigation;
  private BottomNavigationView tabs;
  private MaterialToolbar toolbar;

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    setContentView(R.layout.activity_main);
    View root = findViewById(R.id.root);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
          v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
          return insets;
        });
    toolbar = findViewById(R.id.toolbar);
    tabs = findViewById(R.id.bottom_nav);
    navigation =
        ((NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host))
            .getNavController();
    NavigationUI.setupWithNavController(tabs, navigation);
    toolbar.setNavigationContentDescription(R.string.up);
    toolbar.setNavigationOnClickListener(v -> navigation.navigateUp());
    navigation.addOnDestinationChangedListener(
        (controller, destination, args) -> {
          boolean login = destination.getId() == R.id.login;
          tabs.setVisibility(login ? View.GONE : View.VISIBLE);
          boolean rootScreen =
              login
                  || destination.getId() == R.id.overview
                  || destination.getId() == R.id.patient_list
                  || destination.getId() == R.id.alerts
                  || destination.getId() == R.id.profile;
          toolbar.setNavigationIcon(rootScreen ? 0 : R.drawable.ic_back);
        });
    if (!app().repository().authenticated()) showLogin();
    else if (saved == null) showWorkspace();
  }

  public BioPulseApp app() {
    return (BioPulseApp) getApplication();
  }

  public void showLogin() {
    navigation.setGraph(R.navigation.login_graph);
  }

  public void showWorkspace() {
    navigation.setGraph(R.navigation.workspace_graph);
  }

  public void navigate(int destination, Bundle args) {
    if (destination == R.id.patient_detail || destination == R.id.alert_detail) {
      String host = destination == R.id.patient_detail ? "admission" : "event";
      String key = destination == R.id.patient_detail ? "admissionId" : "eventId";
      long id = args == null ? 0 : args.getLong(key);
      if (id <= 0) return;
      navigation.navigate(
          androidx.navigation.NavDeepLinkRequest.Builder.fromUri(
                  android.net.Uri.parse("biopulse-internal://" + host + "/" + id))
              .build());
    } else navigation.navigate(destination == R.id.alerts ? R.id.alerts_graph : destination, args);
  }
}
