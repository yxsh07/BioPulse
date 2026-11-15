package org.biopulse.android.ui;

import android.widget.LinearLayout;
import java.util.Arrays;
import java.util.List;
import org.biopulse.android.R;
import org.biopulse.android.model.UiState;
import org.biopulse.android.presentation.AlertsViewModel;
import org.biopulse.android.ui.components.Ui;

public final class AlertsFragment extends BaseScreenFragment {
  private boolean initialized;

  public AlertsFragment() {
    super(R.layout.fragment_alerts, "Alert center", AlertsViewModel.class);
  }

  @Override
  protected void render(UiState s) {
    if (!initialized) {
      initialized = true;
      LinearLayout filters = requireView().findViewById(R.id.filters);
      List<String> severities = Arrays.asList("All severities", "CRITICAL", "WARNING", "INFO"),
          states = Arrays.asList("All states", "OPEN", "ACKNOWLEDGED", "RESOLVED");
      Ui.spinner(
          filters,
          "Severity",
          severities,
          Math.max(0, severities.indexOf(model.value("severity", "All severities"))),
          i -> {
            model.set("severity", i == 0 ? "" : severities.get(i));
            update();
          });
      Ui.spinner(
          filters,
          "State",
          states,
          Math.max(0, states.indexOf(model.value("workflow", "All states"))),
          i -> {
            model.set("workflow", i == 0 ? "" : states.get(i));
            update();
          });
      Ui.note(
          filters,
          "Filters apply to the loaded page. Physiological rules and technical device availability"
              + " are separate.");
    }
    update();
    pager(s.object("alerts"));
  }

  private void update() {
    if (rows != null)
      rows.submitList(
          alertRows(
              latest.items("alerts"), model.value("severity", ""), model.value("workflow", "")));
  }

  @Override
  public void onDestroyView() {
    initialized = false;
    super.onDestroyView();
  }
}
