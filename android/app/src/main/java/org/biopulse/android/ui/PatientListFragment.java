package org.biopulse.android.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.biopulse.android.R;
import org.biopulse.android.model.UiState;
import org.biopulse.android.model.Values;
import org.biopulse.android.presentation.PatientListViewModel;
import org.biopulse.android.ui.components.RowsAdapter;
import org.biopulse.android.ui.components.Ui;
import org.json.JSONArray;
import org.json.JSONObject;

public final class PatientListFragment extends BaseScreenFragment {
  private String filterVersion = "";

  public PatientListFragment() {
    super(R.layout.fragment_patient_list, "Active patients", PatientListViewModel.class);
  }

  @Override
  public void onDestroyView() {
    filterVersion = "";
    super.onDestroyView();
  }

  @Override
  protected void render(UiState s) {
    if (s.documents.isEmpty()) return;
    JSONArray wards = s.array("wards");
    String signature = wards.toString();
    if (!signature.equals(filterVersion)) {
      filterVersion = signature;
      LinearLayout box = requireView().findViewById(R.id.filters);
      box.removeAllViews();
      android.widget.EditText search =
          Ui.field(box, "Search name or patient code", model.value("search", ""));
      search.addTextChangedListener(
          new TextWatcher() {
            public void beforeTextChanged(CharSequence x, int a, int c, int n) {}

            public void onTextChanged(CharSequence x, int a, int b, int c) {
              model.set("search", x.toString());
              updateRows(latest);
            }

            public void afterTextChanged(Editable e) {}
          });
      List<String> labels = new ArrayList<>(), ids = new ArrayList<>();
      labels.add("All authorized wards");
      ids.add("");
      for (int i = 0; i < wards.length(); i++) {
        JSONObject w = wards.optJSONObject(i);
        labels.add(w.optString("name"));
        ids.add(w.optString("id"));
      }
      Ui.spinner(
          box,
          "Ward",
          labels,
          Math.max(0, ids.indexOf(model.value("ward", ""))),
          i -> {
            model.set("ward", ids.get(i));
            updateRows(latest);
          });
    }
    updateRows(s);
    pager(s.object("patients"));
  }

  private void updateRows(UiState s) {
    List<RowsAdapter.Row> data = new ArrayList<>();
    JSONArray patients = s.items("patients"),
        admissions = s.items("admissions"),
        beds = s.array("beds"),
        wards = s.array("wards");
    String query = model.value("search", "").toLowerCase(Locale.ROOT),
        ward = model.value("ward", "");
    for (int i = 0; i < patients.length(); i++) {
      JSONObject p = patients.optJSONObject(i);
      if (!(p.optString("displayName") + " " + p.optString("code"))
          .toLowerCase(Locale.ROOT)
          .contains(query)) continue;
      JSONObject a = Values.admission(admissions, p.optLong("id"));
      JSONObject bed = Values.id(beds, a.optLong("bedId")),
          w = Values.id(wards, bed.optLong("wardId"));
      if (!ward.isEmpty() && !ward.equals(w.optString("id"))) continue;
      long id = a.optLong("id");
      JSONArray latest = s.array("latest-" + id);
      JSONObject hr = Values.find(latest, "metric", "HR"),
          spo2 = Values.find(latest, "metric", "SPO2");
      String detail =
          w.optString("name", "Unassigned")
              + " / "
              + bed.optString("label", "No bed")
              + "\nHR "
              + Values.number(hr, "value")
              + " "
              + hr.optString("unit")
              + " · SpO₂ "
              + Values.number(spo2, "value")
              + " "
              + spo2.optString("unit");
      if (hr.has("observedAt"))
        detail +=
            "\n"
                + hr.optString("quality")
                + " · "
                + Values.stamp(hr.optString("observedAt"))
                + " · source "
                + hr.optLong("sourceId");
      else detail += "\nNo recorded heart-rate observation";
      data.add(
          new RowsAdapter.Row(
              p.optLong("id"),
              p.optString("code") + " / " + a.optString("status", "UNASSIGNED"),
              p.optString("displayName"),
              detail,
              id > 0 ? () -> patient(id) : null));
    }
    rows.submitList(data);
    ((android.widget.TextView) requireView().findViewById(R.id.eyebrow))
        .setText(data.size() + " PATIENTS ON THIS PAGE / AUTHORIZED SCOPE");
  }
}
