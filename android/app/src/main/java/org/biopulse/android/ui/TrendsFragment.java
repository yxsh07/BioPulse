package org.biopulse.android.ui;

import android.widget.LinearLayout;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.biopulse.android.R;
import org.biopulse.android.model.UiState;
import org.biopulse.android.model.Values;
import org.biopulse.android.presentation.TrendsViewModel;
import org.biopulse.android.ui.components.SignalChartView;
import org.biopulse.android.ui.components.Ui;
import org.json.JSONArray;
import org.json.JSONObject;

public final class TrendsFragment extends RecordedFragment {
  public TrendsFragment() {
    super(R.layout.fragment_trends, "Historical trends", TrendsViewModel.class);
  }

  @Override
  protected void render(UiState s) {
    content.removeAllViews();
    if (s.documents.isEmpty()) return;
    identity(s);
    replaySelection(s);
    interval(s);
    List<String> metrics = Arrays.asList("HR", "SPO2", "RR", "SBP", "DBP", "TEMP", "PULSE");
    Ui.spinner(
            content,
            "Metric",
            metrics,
            Math.max(0, metrics.indexOf(model.value("metric", "HR"))),
            i -> {
              if (!metrics.get(i).equals(model.value("metric", "HR"))) {
                model.set("metric", metrics.get(i));
                model.set("page", "0");
                model.refresh();
              }
            })
        .setId(R.id.metric_picker);
    JSONObject analytics = s.object("analytics"), summary = analytics.optJSONObject("summary");
    if (summary == null) summary = new JSONObject();
    JSONArray history = s.items("history");
    drawHistory(content, history, model.value("metric", "HR"), R.color.ecg, R.id.trend_chart);
    LinearLayout stats = Ui.panel(content, "Data availability");
    String[] keys = {
      "mean", "median", "minimum", "maximum", "standardDeviation", "availabilityPercent"
    };
    String[] labels = {
      "Mean", "Median", "Minimum", "Maximum", "Standard deviation", "Available observations (%)"
    };
    for (int i = 0; i < keys.length; i++)
      Ui.note(stats, labels[i] + ": " + Values.number(summary, keys[i]));
    Ui.note(
        stats,
        "Usable observations: "
            + summary.optLong("count")
            + "\n"
            + analytics.optString("aggregationMethod")
            + "\nExpected cadence: "
            + analytics.optInt("cadenceSeconds")
            + " seconds\n"
            + (summary.optBoolean("fresh")
                ? "Recent recorded observations"
                : "Historical / stale observations"));
    JSONArray gaps = summary.optJSONArray("gaps");
    if (gaps != null)
      for (int i = 0; i < Math.min(50, gaps.length()); i++) {
        JSONObject gap = gaps.optJSONObject(i);
        Ui.note(
            stats,
            "Missing: "
                + Values.stamp(gap.optString("from"))
                + " → "
                + Values.stamp(gap.optString("to"))
                + " / "
                + Values.number(gap, "seconds")
                + "s");
      }
    if (gaps != null && gaps.length() > 50)
      Ui.note(stats, "First 50 gaps shown. Narrow the analysis interval for remaining gaps.");
    JSONObject anomaly = analytics.optJSONObject("anomaly");
    if (anomaly != null) {
      LinearLayout experiment = Ui.panel(content, "Baseline anomaly analysis / RESEARCH_ONLY");
      Ui.note(
          experiment,
          "Score: "
              + Values.number(anomaly, "score")
              + "\n"
              + anomaly.optString("status")
              + " · "
              + anomaly.optString("modelVersion")
              + "\n"
              + anomaly.optString("provenance")
              + "\nInferred "
              + Values.stamp(anomaly.optString("inferredAt")));
    }
    List<String> compare = new ArrayList<>();
    compare.add("None");
    compare.addAll(metrics);
    Ui.spinner(
        content,
        "Compare a second metric (separate units)",
        compare,
        Math.max(0, compare.indexOf(model.value("compareMetric", "None"))),
        i -> {
          String value = i == 0 ? "" : compare.get(i);
          if (!value.equals(model.value("compareMetric", ""))) {
            model.set("compareMetric", value);
            model.refresh();
          }
        });
    if (s.documents.containsKey("comparison"))
      drawHistory(
          content,
          s.items("comparison"),
          model.value("compareMetric", ""),
          R.color.ppg,
          ViewIds.COMPARISON);
    if (s.object("history").optBoolean("hasNext"))
      Ui.note(
          content,
          "First 1000 observations on this page; narrow the interval or open the next page.");
    LinearLayout pages = Ui.panel(content, "History pages");
    int page = Integer.parseInt(model.value("page", "0"));
    Ui.button(
            pages,
            "Previous",
            () -> {
              model.set("page", Integer.toString(Math.max(0, page - 1)));
              model.refresh();
            })
        .setEnabled(page > 0);
    Ui.button(
            pages,
            "Next",
            () -> {
              model.set("page", Integer.toString(page + 1));
              model.refresh();
            })
        .setEnabled(s.object("history").optBoolean("hasNext"));
    Ui.button(
        content,
        "Generate a monitoring report",
        () -> {
          android.os.Bundle args = admissionArgs(admission());
          args.putString("from", s.object("scope").optString("from"));
          args.putString("to", s.object("scope").optString("to"));
          args.putString("replay", model.value("replay", "auto"));
          nav(R.id.report, args);
        });
  }

  private void drawHistory(
      LinearLayout parent, JSONArray history, String metric, int color, int viewId) {
    LinearLayout box = Ui.panel(parent, Values.name(metric));
    List<SignalChartView.Point> points = new ArrayList<>();
    String unit = "Unit unavailable";
    StringBuilder metadata = new StringBuilder();
    for (int i = 0; i < history.length(); i++) {
      JSONObject m = history.optJSONObject(i);
      unit = m.optString("unit");
      boolean usable = Arrays.asList("GOOD", "FAIR").contains(m.optString("quality"));
      Double value = m.isNull("value") || !usable ? null : m.optDouble("value");
      points.add(new SignalChartView.Point(Values.time(m.optString("observedAt")), value));
      metadata
          .append(Values.stamp(m.optString("observedAt")))
          .append(" · ")
          .append(Values.number(m, "value"))
          .append(' ')
          .append(unit)
          .append(" · ")
          .append(m.optString("quality"))
          .append(" · source ")
          .append(m.optLong("sourceId"))
          .append('\n');
    }
    SignalChartView chart = new SignalChartView(requireContext());
    chart.setId(viewId);
    chart.setSeries(
        points,
        ContextCompat.getColor(requireContext(), color),
        15000,
        Values.name(metric)
            + " history. "
            + history.length()
            + " recorded observations. "
            + unit
            + ". Missing and unusable values remain gaps.");
    chart.setViewport(
        Values.time(latest.object("scope").optString("from")),
        Values.time(latest.object("scope").optString("to")));
    box.addView(chart, new LinearLayout.LayoutParams(-1, Ui.dp(requireContext(), 170)));
    Ui.note(
        box,
        unit
            + " / "
            + latest.object("scope").optString("from")
            + " → "
            + latest.object("scope").optString("to"));
    String details = metadata.toString(), finalUnit = unit;
    Ui.button(
        box,
        "Recorded observations and quality",
        () -> showPoints(Values.name(metric), points, finalUnit, details));
  }

  private static final class ViewIds {
    static final int COMPARISON = android.view.View.generateViewId();
  }
}
