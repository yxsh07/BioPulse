package org.biopulse.android.ui;

import android.os.Bundle;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import org.biopulse.android.R;
import org.biopulse.android.model.UiState;
import org.biopulse.android.model.Values;
import org.biopulse.android.presentation.OverviewViewModel;
import org.biopulse.android.ui.components.RowsAdapter;
import org.biopulse.android.ui.components.Ui;
import org.biopulse.android.ui.components.VitalTileView;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OverviewFragment extends BaseScreenFragment {
  public OverviewFragment() {
    super(R.layout.fragment_overview, "Every signal. In\nperspective.", OverviewViewModel.class);
  }

  @Override
  protected void render(UiState s) {
    if (content == null) return;
    content.removeAllViews();
    if (s.documents.isEmpty()) return;
    JSONArray admissions = s.items("admissions"),
        alerts = s.items("alerts"),
        devices = s.array("devices"),
        patients = s.items("patients");
    int active = 0, critical = 0, warning = 0, online = 0;
    for (int i = 0; i < admissions.length(); i++)
      if (admissions.optJSONObject(i).optString("status").equals("ACTIVE")) active++;
    for (int i = 0; i < alerts.length(); i++) {
      JSONObject a = alerts.optJSONObject(i);
      if (!a.optString("state").equals("RESOLVED")) {
        if (a.optString("severity").equals("CRITICAL")) critical++;
        if (a.optString("severity").equals("WARNING")) warning++;
      }
    }
    for (int i = 0; i < devices.length(); i++)
      if (devices.optJSONObject(i).optBoolean("online")) online++;
    GridLayout grid = new GridLayout(requireContext());
    int columns = getResources().getConfiguration().fontScale >= 1.5f ? 1 : 2;
    grid.setColumnCount(columns);
    content.addView(grid);
    String[] labels = {
      "Patients monitored", "Active critical", "Active warnings", "Devices online"
    };
    String[] values = {"" + active, "" + critical, "" + warning, "" + online};
    String[] notes = {
      "Active authorized admissions",
      "Open or acknowledged events",
      "Open or acknowledged events",
      online + " / " + devices.length() + " registered devices\nConnection state, not acquisition"
    };
    for (int i = 0; i < labels.length; i++) {
      final int index = i;
      VitalTileView tile =
          new VitalTileView(
              requireContext(),
              "",
              labels[i],
              values[i],
              i == 0 ? "patients" : i == 3 ? "online" : "events",
              notes[i]);
      GridLayout.LayoutParams p = new GridLayout.LayoutParams();
      p.width = 0;
      p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
      p.setMargins(0, 0, Ui.dp(requireContext(), 12), Ui.dp(requireContext(), 12));
      grid.addView(tile, p);
      tile.setOnClickListener(
          v -> {
            if (index == 0) nav(R.id.patients_graph, null);
            else if (index == 3) {
              StringBuilder text = new StringBuilder();
              for (int j = 0; j < devices.length(); j++) {
                JSONObject d = devices.optJSONObject(j);
                text.append(d.optString("serial"))
                    .append(" · ")
                    .append(d.optBoolean("online") ? "ONLINE" : "OFFLINE")
                    .append('\n');
              }
              new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                  .setTitle("Registered device availability")
                  .setMessage(text.toString())
                  .setPositiveButton("Close", null)
                  .show();
            } else {
              Bundle b = new Bundle();
              b.putString("severity", index == 1 ? "CRITICAL" : "WARNING");
              nav(R.id.alerts, b);
            }
          });
    }
    Ui.note(
        content,
        "Counts cover the loaded authorized page; identities and sources retain their research"
            + " provenance.");
    LinearLayout recent = Ui.panel(content, "Patients at a glance");
    List<RowsAdapter.Row> rows = new ArrayList<>();
    for (int i = 0; i < Math.min(4, patients.length()); i++) {
      JSONObject p = patients.optJSONObject(i);
      JSONObject a = Values.admission(admissions, p.optLong("id"));
      long id = a.optLong("id");
      rows.add(
          new RowsAdapter.Row(
              p.optLong("id"),
              p.optString("code"),
              p.optString("displayName"),
              id > 0
                  ? "Admission " + id + " · " + a.optString("status")
                  : "No admission on this page",
              id > 0 ? () -> patient(id) : null));
    }
    previewRows(recent, rows);
    Ui.button(recent, "View directory", () -> nav(R.id.patients_graph, null));
    LinearLayout events = Ui.panel(content, "Priority events");
    List<RowsAdapter.Row> eventRows = alertRows(alerts, "", "");
    eventRows.removeIf(r -> r.badge.contains("RESOLVED"));
    previewRows(events, eventRows.subList(0, Math.min(4, eventRows.size())));
    if (eventRows.isEmpty()) Ui.note(events, "No open events in this scope.");
  }
}
