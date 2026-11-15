package org.biopulse.android.ui;

import android.widget.LinearLayout;
import org.biopulse.android.R;
import org.biopulse.android.model.UiState;
import org.biopulse.android.model.Values;
import org.biopulse.android.presentation.AlertDetailViewModel;
import org.biopulse.android.ui.components.Ui;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class AlertDetailFragment extends BaseScreenFragment {
  public AlertDetailFragment() {
    super(R.layout.fragment_alert_detail, "Event information", AlertDetailViewModel.class);
  }

  @Override
  protected void render(UiState s) {
    content.removeAllViews();
    JSONObject detail = s.object("detail"), a = detail.optJSONObject("alert");
    if (a == null) return;
    LinearLayout event =
        Ui.panel(content, Values.name(a.optString("metric")) + " event / " + a.optLong("id"));
    Ui.note(
        event,
        a.optString("severity")
            + " / "
            + a.optString("state")
            + " / version "
            + a.optLong("version"));
    event.addView(
        Ui.text(
            requireContext(),
            Values.number(a, "value") + " " + a.optString("unit"),
            R.style.Text_BioPulse_Title));
    Ui.note(
        event,
        "Admission "
            + a.optLong("admissionId")
            + "\nTriggered "
            + Values.stamp(a.optString("triggeredAt"))
            + "\nStarted "
            + Values.stamp(a.optString("startedAt"))
            + "\n"
            + a.optString("quality")
            + " quality · source "
            + a.optLong("sourceId"));
    JSONArray rules = detail.optJSONArray("rules");
    if (rules != null)
      for (int i = 0; i < rules.length(); i++) {
        JSONObject r = rules.optJSONObject(i);
        Ui.note(
            event,
            "Rule "
                + r.optLong("id")
                + ": lower "
                + Values.number(r, "lowerBound")
                + ", upper "
                + Values.number(r, "upperBound")
                + " "
                + a.optString("unit")
                + "\nPersistence "
                + r.optInt("persistenceSeconds")
                + "s · evidence gap ≤ "
                + r.optInt("maxGapSeconds")
                + "s");
      }
    for (String action : new String[] {"acknowledge", "resolve"}) {
      com.google.android.material.button.MaterialButton b =
          Ui.button(
              event,
              action,
              () -> {
                try {
                  model.mutate(
                      "/alerts/" + a.optLong("id") + "/" + action,
                      new JSONObject().put("expectedVersion", a.optLong("version")),
                      result -> {});
                } catch (JSONException e) {
                  model.error("Unable to prepare event transition.");
                }
              });
      b.setId(action.equals("acknowledge") ? R.id.acknowledge_event : R.id.resolve_event);
      b.setEnabled(
          !a.optString("state").equals("RESOLVED")
              && (!action.equals("acknowledge") || a.optString("state").equals("OPEN")));
    }
    Ui.note(
        event,
        "Acknowledgment and resolution are separate server-validated actions. On a version"
            + " conflict, refresh before retrying.");
    Ui.button(event, "Open patient workstation", () -> patient(a.optLong("admissionId")));
    LinearLayout timeline = Ui.panel(content, "Event timeline");
    JSONArray events = detail.optJSONArray("events");
    if (events != null)
      for (int i = 0; i < events.length(); i++) {
        JSONObject e = events.optJSONObject(i);
        Ui.note(
            timeline,
            e.optString("action")
                + " · "
                + e.optString("actor")
                + "\n"
                + Values.stamp(e.optString("occurredAt")));
      }
  }
}
