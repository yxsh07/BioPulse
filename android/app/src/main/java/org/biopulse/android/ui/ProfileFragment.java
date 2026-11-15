package org.biopulse.android.ui;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationManagerCompat;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.Arrays;
import java.util.List;
import org.biopulse.android.BioPulseApp;
import org.biopulse.android.MainActivity;
import org.biopulse.android.R;
import org.biopulse.android.model.UiState;
import org.biopulse.android.presentation.ProfileViewModel;
import org.biopulse.android.ui.components.Ui;
import org.json.JSONObject;

public final class ProfileFragment extends BaseScreenFragment {
  public ProfileFragment() {
    super(R.layout.fragment_profile, "Profile & preferences", ProfileViewModel.class);
  }

  @Override
  protected void render(UiState s) {
    content.removeAllViews();
    JSONObject identity =
        s.documents.containsKey("session") ? s.object("session") : repository().session();
    android.widget.LinearLayout account =
        Ui.panel(content, identity.optString("username", "Authenticated staff"));
    Ui.note(
        account,
        identity.optJSONArray("roles") == null
            ? "Role unavailable"
            : identity.optJSONArray("roles").toString().replace("ROLE_", ""));
    Ui.note(
        account,
        "Ward and admission access, report access and session expiry are enforced by the server.");
    Ui.note(account, "Connected to " + repository().api().origin());
    BioPulseApp app = (BioPulseApp) requireActivity().getApplication();
    android.widget.LinearLayout preferences =
        Ui.panel(content, "Workspace preferences / this device");
    List<String> themes = Arrays.asList("Dark", "Light", "System");
    int saved = app.preferences().getInt("theme", AppCompatDelegate.MODE_NIGHT_YES);
    int selected =
        saved == AppCompatDelegate.MODE_NIGHT_NO
            ? 1
            : saved == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM ? 2 : 0;
    Ui.spinner(
            preferences,
            "Appearance",
            themes,
            selected,
            i -> {
              int mode =
                  i == 1
                      ? AppCompatDelegate.MODE_NIGHT_NO
                      : i == 2
                          ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                          : AppCompatDelegate.MODE_NIGHT_YES;
              if (mode != app.preferences().getInt("theme", AppCompatDelegate.MODE_NIGHT_YES)) {
                app.preferences().edit().putInt("theme", mode).apply();
                AppCompatDelegate.setDefaultNightMode(mode);
              }
            })
        .setId(R.id.theme_picker);
    android.widget.LinearLayout notifications = Ui.panel(content, "Notification preferences");
    Ui.note(
        notifications,
        "Saved on this device only. These preferences do not change alert records or enable"
            + " background push delivery. No background notification transport is configured.");
    String[] keys = {"critical", "warning", "technical"};
    for (String key : keys) {
      MaterialSwitch toggle = new MaterialSwitch(requireContext());
      toggle.setText(key.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + key.substring(1) + " category preference");
      toggle.setChecked(app.preferences().getBoolean("notification-" + key, true));
      toggle.setOnCheckedChangeListener(
          (button, value) -> {
            app.preferences().edit().putBoolean("notification-" + key, value).apply();
            android.widget.Toast.makeText(
                    requireContext(),
                    "Device-only preference saved",
                    android.widget.Toast.LENGTH_SHORT)
                .show();
          });
      notifications.addView(toggle);
    }
    Ui.note(
        notifications,
        NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
            ? "OS notifications are allowed; background delivery remains unconfigured."
            : "OS notifications are disabled for this app.");
    Ui.button(
        notifications,
        "Open app notification settings",
        () ->
            startActivity(
                new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(
                        android.provider.Settings.EXTRA_APP_PACKAGE,
                        requireContext().getPackageName())));
    Ui.button(
            account,
            "Sign out",
            () -> {
              repository()
                  .io
                  .execute(
                      () -> {
                        String notice = "";
                        try {
                          repository().logout();
                        } catch (Exception ignored) {
                          repository().clearSession();
                          notice = "Local session cleared; server sign-out could not be confirmed.";
                        }
                        final String message = notice;
                        new Handler(Looper.getMainLooper())
                            .post(
                                () -> {
                                  if (isAdded()) {
                                    if (!message.isEmpty())
                                      android.widget.Toast.makeText(
                                              requireContext(),
                                              message,
                                              android.widget.Toast.LENGTH_LONG)
                                          .show();
                                    ((MainActivity) requireActivity()).showLogin();
                                  }
                                });
                      });
            })
        .setId(R.id.sign_out);
    Ui.button(
        account,
        "Change server and clear local session",
        () -> {
          repository().clearSession();
          ((MainActivity) requireActivity()).showLogin();
        });
    Ui.note(
        content,
        "Academic research workspace. Sources, recorded times and missing measurements remain"
            + " visible. Experimental outputs have no clinical validation.");
  }
}
