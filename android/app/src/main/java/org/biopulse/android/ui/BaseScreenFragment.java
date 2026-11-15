package org.biopulse.android.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.biopulse.android.BioPulseApp;
import org.biopulse.android.MainActivity;
import org.biopulse.android.R;
import org.biopulse.android.data.Repository;
import org.biopulse.android.model.UiState;
import org.biopulse.android.model.Values;
import org.biopulse.android.presentation.ScreenViewModel;
import org.biopulse.android.ui.components.RowsAdapter;
import org.json.JSONArray;
import org.json.JSONObject;

public abstract class BaseScreenFragment extends Fragment {
  protected ScreenViewModel model;
  protected UiState latest = UiState.empty();
  protected LinearLayout content;
  protected RowsAdapter rows;
  protected String connection = "DISCONNECTED";
  private final Class<? extends ScreenViewModel> modelClass;
  private final String title;
  private Map<String, String> lastRendered;

  public BaseScreenFragment(int layout, String title, Class<? extends ScreenViewModel> modelClass) {
    super(layout);
    this.title = title;
    this.modelClass = modelClass;
  }

  protected Repository repository() {
    return ((BioPulseApp) requireActivity().getApplication()).repository();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle saved) {
    model = new ViewModelProvider(this).get(modelClass);
    content = view.findViewById(R.id.content);
    ((TextView) view.findViewById(R.id.screen_title)).setText(title);
    MaterialButton refresh = view.findViewById(R.id.retry);
    refresh.setVisibility(View.VISIBLE);
    refresh.setText(R.string.refresh);
    refresh.setOnClickListener(v -> model.refresh());
    RecyclerView list = view.findViewById(R.id.list);
    if (list != null) {
      rows = new RowsAdapter();
      list.setLayoutManager(new LinearLayoutManager(requireContext()));
      list.setAdapter(rows);
    }
    setup(view);
    model
        .state()
        .observe(
            getViewLifecycleOwner(),
            state -> {
              latest = state;
              if (!repository().authenticated()) {
                ((MainActivity) requireActivity()).showLogin();
                return;
              }
              view.findViewById(R.id.progress)
                  .setVisibility(state.loading ? View.VISIBLE : View.GONE);
              refresh.setEnabled(!state.loading);
              refresh.setText(state.error.isEmpty() ? R.string.refresh : R.string.retry);
              ((TextView) view.findViewById(R.id.error))
                  .setText(
                      state.error.isEmpty()
                          ? ""
                          : state.error
                              + (!state.documents.isEmpty()
                                  ? "\nShowing the last received snapshot; it may be stale."
                                  : ""));
              view.findViewById(R.id.error)
                  .setVisibility(state.error.isEmpty() ? View.GONE : View.VISIBLE);
              updateSource(view);
              if (!(view.findFocus() instanceof EditText)
                  && (!state.documents.equals(lastRendered) || lastRendered == null)) {
                lastRendered = state.documents;
                render(state);
              }
            });
    model
        .connection()
        .observe(
            getViewLifecycleOwner(),
            value -> {
              connection = value;
              updateSource(view);
            });
  }

  protected void setup(View root) {}

  @Override
  public void onDestroyView() {
    lastRendered = null;
    content = null;
    rows = null;
    super.onDestroyView();
  }

  protected abstract void render(UiState state);

  private void updateSource(View root) {
    JSONObject scope = latest.object("scope"),
        session = scope.optJSONObject("session"),
        recording = scope.optJSONObject("recording");
    String text = "AUTHENTICATED / RESEARCH ONLY";
    if (session != null && session.has("id"))
      text =
          "REPLAY "
              + session.optLong("id")
              + " / "
              + session.optString("state")
              + " / "
              + (model.streaming() ? connection : "RECORDED SNAPSHOT")
              + "\nRecorded cursor "
              + Values.stamp(session.optString("cursorAt"));
    else if (model != null && Long.parseLong(model.value("admissionId", "0")) > 0)
      text =
          "ADMISSION "
              + model.value("admissionId", "")
              + " / "
              + (model.streaming() ? connection : "AUTHORIZED SCOPE");
    if (recording != null && recording.has("name"))
      text +=
          "\n"
              + recording.optString("name")
              + " / "
              + recording.optString("sourceType")
              + "\n"
              + recording.optString("provenance");
    if (latest.receivedAt > 0)
      text +=
          "\nSnapshot received "
              + Values.stamp(java.time.Instant.ofEpochMilli(latest.receivedAt).toString());
    ((TextView) root.findViewById(R.id.source_strip)).setText(text);
  }

  @Override
  public void onStart() {
    super.onStart();
    if (model != null) model.start();
  }

  @Override
  public void onStop() {
    if (model != null) model.stop();
    super.onStop();
  }

  protected void nav(int destination, Bundle arguments) {
    ((MainActivity) requireActivity()).navigate(destination, arguments);
  }

  protected Bundle admissionArgs(long id) {
    Bundle b = new Bundle();
    b.putLong("admissionId", id);
    return b;
  }

  protected long admission() {
    return Long.parseLong(model.value("admissionId", "0"));
  }

  protected void patient(long id) {
    nav(R.id.patient_detail, admissionArgs(id));
  }

  protected void event(long id) {
    Bundle b = new Bundle();
    b.putLong("eventId", id);
    nav(R.id.alert_detail, b);
  }

  protected void previewRows(LinearLayout parent, List<RowsAdapter.Row> data) {
    RecyclerView list = new RecyclerView(requireContext());
    list.setLayoutManager(new LinearLayoutManager(requireContext()));
    list.setNestedScrollingEnabled(false);
    RowsAdapter adapter = new RowsAdapter();
    list.setAdapter(adapter);
    parent.addView(list, new LinearLayout.LayoutParams(-1, -2));
    adapter.submitList(new ArrayList<>(data));
  }

  protected void pager(JSONObject page) {
    LinearLayout pager = requireView().findViewById(R.id.pager);
    if (pager == null) return;
    pager.removeAllViews();
    int current = Integer.parseInt(model.value("page", "0"));
    MaterialButton previous = new MaterialButton(requireContext());
    previous.setText(R.string.previous);
    previous.setEnabled(current > 0);
    previous.setOnClickListener(
        v -> {
          model.set("page", Integer.toString(current - 1));
          model.refresh();
        });
    pager.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
    MaterialButton next = new MaterialButton(requireContext());
    next.setText(R.string.next);
    next.setEnabled(page.optBoolean("hasNext"));
    next.setOnClickListener(
        v -> {
          model.set("page", Integer.toString(current + 1));
          model.refresh();
        });
    pager.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
  }

  protected List<RowsAdapter.Row> alertRows(JSONArray values, String severity, String state) {
    List<RowsAdapter.Row> result = new ArrayList<>();
    for (int i = 0; i < values.length(); i++) {
      JSONObject a = values.optJSONObject(i);
      if (a == null) continue;
      if (!severity.isEmpty() && !a.optString("severity").equals(severity)
          || !state.isEmpty() && !a.optString("state").equals(state)) continue;
      long id = a.optLong("id");
      result.add(
          new RowsAdapter.Row(
              id,
              a.optString("severity") + " / " + a.optString("state"),
              Values.name(a.optString("metric")) + " threshold event",
              Values.number(a, "value")
                  + " "
                  + a.optString("unit")
                  + " · admission "
                  + a.optLong("admissionId")
                  + "\n"
                  + Values.stamp(a.optString("triggeredAt"))
                  + " · "
                  + a.optString("quality"),
              () -> event(id)));
    }
    return result;
  }
}
