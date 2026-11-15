package org.biopulse.android.ui;

import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import org.biopulse.android.R;
import org.biopulse.android.model.UiState;
import org.biopulse.android.model.Values;
import org.biopulse.android.presentation.ScreenViewModel;
import org.biopulse.android.ui.components.SignalChartView;
import org.biopulse.android.ui.components.Ui;
import org.biopulse.android.ui.components.VitalTileView;
import org.json.JSONArray;
import org.json.JSONObject;

public abstract class RecordedFragment extends BaseScreenFragment {
  public RecordedFragment(int layout, String title, Class<? extends ScreenViewModel> type) {
    super(layout, title, type);
  }

  protected void identity(UiState state) {
    JSONObject p = state.object("patient"),
        a = state.object("admission"),
        bed = Values.id(state.array("beds"), a.optLong("bedId")),
        ward = Values.id(state.array("wards"), bed.optLong("wardId"));
    LinearLayout box = Ui.panel(content, p.optString("code") + " / " + p.optString("displayName"));
    Ui.note(
        box,
        ward.optString("name", "Ward unavailable")
            + " · "
            + bed.optString("label", "Bed unavailable")
            + " · admission "
            + a.optLong("id")
            + "\n"
            + a.optString("status")
            + " · research patient context");
  }

  protected void replaySelection(UiState state) {
    JSONArray sessions = state.items("sessions");
    List<String> labels = new ArrayList<>(), ids = new ArrayList<>();
    labels.add(
        sessions.length() > 0
            ? "Follow newest · session " + sessions.optJSONObject(0).optLong("id")
            : "Follow newest · none");
    ids.add("auto");
    labels.add("Latest observations (all sources)");
    ids.add("latest");
    for (int i = 0; i < sessions.length(); i++) {
      JSONObject s = sessions.optJSONObject(i);
      labels.add("Session " + s.optLong("id") + " · " + s.optString("state"));
      ids.add(s.optString("id"));
    }
    Spinner picker =
        Ui.spinner(
            content,
            "Replay session",
            labels,
            Math.max(0, ids.indexOf(model.value("replay", "auto"))),
            i -> {
              String value = ids.get(i);
              if (!model.value("replay", "auto").equals(value)) model.selectReplay(value);
            });
    picker.setId(R.id.replay_picker);
  }

  protected void vitals(UiState state) {
    JSONArray latest = state.array("latest");
    GridLayout grid = new GridLayout(requireContext());
    grid.setColumnCount(getResources().getConfiguration().fontScale >= 1.5f ? 1 : 2);
    content.addView(grid);
    String[] codes = {"HR", "SPO2", "RR", "BP", "TEMP", "PULSE"};
    int[] ids = {
      R.id.card_hr, R.id.card_spo2, R.id.card_rr, R.id.card_bp, R.id.card_temp, R.id.card_pulse
    };
    for (int i = 0; i < codes.length; i++) {
      String code = codes[i];
      JSONObject m = Values.find(latest, "metric", code.equals("BP") ? "SBP" : code);
      String value = Values.number(m, "value"),
          unit = m.optString("unit"),
          metadata = measurementMetadata(m);
      if (code.equals("BP")) {
        JSONObject other = Values.find(latest, "metric", "DBP");
        value =
            m.isNull("value") && other.isNull("value")
                ? "—"
                : Values.number(m, "value") + "/" + Values.number(other, "value");
        if (unit.isEmpty()) unit = other.optString("unit");
        metadata =
            "Systolic: " + measurementMetadata(m) + "\nDiastolic: " + measurementMetadata(other);
      }
      VitalTileView tile =
          new VitalTileView(requireContext(), code, Values.name(code), value, unit, metadata);
      tile.setId(ids[i]);
      GridLayout.LayoutParams p = new GridLayout.LayoutParams();
      p.width = 0;
      p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
      p.setMargins(
          0,
          0,
          i % grid.getColumnCount() == grid.getColumnCount() - 1 ? 0 : Ui.dp(requireContext(), 12),
          Ui.dp(requireContext(), 12));
      grid.addView(tile, p);
    }
  }

  private String measurementMetadata(JSONObject m) {
    if (!m.has("id")) return "No measurement\nNo recorded observation";
    return m.optString("quality")
        + " quality\nRecorded "
        + Values.stamp(m.optString("observedAt"))
        + "\nReceived "
        + Values.stamp(m.optString("receivedAt"))
        + "\nSource "
        + m.optLong("sourceId");
  }

  protected void signals(UiState state) {
    JSONObject scope = state.object("scope");
    long from = Values.time(scope.optString("signalFrom")),
        to = Values.time(scope.optString("signalTo"));
    String[] metrics = {"ECG", "PPG", "RESP"};
    int[] colors = {R.color.ecg, R.color.ppg, R.color.resp},
        ids = {R.id.chart_ecg, R.id.chart_ppg, R.id.chart_resp};
    for (int index = 0; index < metrics.length; index++) {
      String metric = metrics[index];
      JSONArray chunks = state.items(metric);
      List<SignalChartView.Point> points = new ArrayList<>();
      StringBuilder metadata = new StringBuilder();
      String unit = "Unit unavailable", quality = "MISSING";
      for (int i = 0; i < chunks.length(); i++) {
        JSONObject c = chunks.optJSONObject(i).optJSONObject("chunk");
        if (c == null) continue;
        unit = c.optString("unit");
        quality = c.optString("quality");
        JSONArray samples = c.optJSONArray("samples");
        double rate = c.optDouble("sampleRateHz");
        long start = Values.time(c.optString("observedAt"));
        if (samples == null || !Double.isFinite(rate) || rate <= 0) continue;
        if (!points.isEmpty()) points.add(new SignalChartView.Point(start - 1, null));
        metadata
            .append(Values.stamp(c.optString("observedAt")))
            .append(" · ")
            .append(rate)
            .append(" Hz · ")
            .append(samples.length())
            .append(" samples · ")
            .append(quality)
            .append(" · source ")
            .append(c.optLong("sourceId"))
            .append('\n');
        for (int j = 0; j < samples.length(); j++) {
          long at = start + Math.round(j / rate * 1000);
          if (at >= from && at < to) {
            Double value =
                samples.isNull(j) || quality.equals("MISSING") || quality.equals("INVALID")
                    ? null
                    : samples.optDouble(j);
            points.add(new SignalChartView.Point(at, value));
          }
        }
      }
      LinearLayout box = Ui.panel(content, Values.name(metric));
      Ui.note(box, unit + " / " + quality + " / " + chunks.length() + " recorded chunks");
      SignalChartView chart = new SignalChartView(requireContext());
      chart.setId(ids[index]);
      chart.setSeries(
          points,
          ContextCompat.getColor(requireContext(), colors[index]),
          1000,
          metric
              + " recorded waveform. "
              + unit
              + ". "
              + quality
              + ". "
              + points.size()
              + " timestamped points. Missing spans remain gaps.");
      chart.setViewport(from, to);
      box.addView(chart, new LinearLayout.LayoutParams(-1, Ui.dp(requireContext(), 170)));
      Ui.note(
          box,
          Values.stamp(scope.optString("signalFrom"))
              + " → "
              + Values.stamp(scope.optString("signalTo"))
              + "\nRecorded time; no fabricated continuation.");
      String details = metadata.toString();
      String finalUnit = unit;
      Ui.button(
          box,
          "Recorded samples and metadata",
          () -> showPoints(metric, points, finalUnit, details));
      if (state.object(metric).optBoolean("hasNext"))
        Ui.note(box, "First 100 chunks only. Narrow the interval for full display.");
    }
  }

  protected void showPoints(
      String title, List<SignalChartView.Point> points, String unit, String metadata) {
    StringBuilder data = new StringBuilder(metadata);
    int limit = Math.min(2000, points.size());
    for (int i = 0; i < limit; i++) {
      SignalChartView.Point p = points.get(i);
      data.append(java.time.Instant.ofEpochMilli(p.at))
          .append("  ")
          .append(p.value == null ? "Missing" : p.value.toString())
          .append(' ')
          .append(unit)
          .append('\n');
    }
    if (points.size() > limit)
      data.append("First 2000 display points. Narrow the interval to inspect remaining samples.");
    android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext());
    android.widget.TextView text =
        Ui.text(requireContext(), data.toString(), R.style.Text_BioPulse_Metadata);
    text.setTextIsSelectable(true);
    text.setPadding(Ui.dp(requireContext(), 16), 0, Ui.dp(requireContext(), 16), 0);
    scroll.addView(text);
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(title + " recorded data")
        .setView(scroll)
        .setPositiveButton("Close", null)
        .show();
  }

  protected void interval(UiState s) {
    JSONObject scope = s.object("scope");
    LinearLayout box = Ui.panel(content, "Analysis window");
    android.widget.EditText from = Ui.field(box, "From (UTC, ISO 8601)", scope.optString("from"));
    from.setId(R.id.from_input);
    android.widget.EditText to = Ui.field(box, "To (UTC, exclusive)", scope.optString("to"));
    to.setId(R.id.to_input);
    Ui.button(
            box,
            "Apply interval",
            () -> {
              try {
                org.biopulse.android.data.Repository.validateRange(
                    from.getText().toString(), to.getText().toString());
                model.set("from", from.getText().toString());
                model.set("to", to.getText().toString());
                finishIntervalEditing();
                model.set("page", "0");
                model.refresh();
              } catch (RuntimeException e) {
                model.error("Enter valid UTC instants and a positive interval of at most 31 days.");
              }
            })
        .setId(R.id.apply_range);
  }

  protected void finishIntervalEditing() {
    content.setFocusableInTouchMode(true);
    content.requestFocus();
    android.view.inputmethod.InputMethodManager keyboard =
        (android.view.inputmethod.InputMethodManager)
            requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
    keyboard.hideSoftInputFromWindow(content.getWindowToken(), 0);
  }
}
