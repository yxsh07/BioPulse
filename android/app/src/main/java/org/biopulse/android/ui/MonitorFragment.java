package org.biopulse.android.ui;

import android.widget.LinearLayout;
import android.widget.SeekBar;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.biopulse.android.R;
import org.biopulse.android.model.UiState;
import org.biopulse.android.model.Values;
import org.biopulse.android.presentation.MonitorViewModel;
import org.biopulse.android.ui.components.Ui;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class MonitorFragment extends RecordedFragment {
  public MonitorFragment() {
    super(R.layout.fragment_monitor, "Signal monitoring", MonitorViewModel.class);
  }

  @Override
  protected void render(UiState s) {
    content.removeAllViews();
    if (s.documents.isEmpty()) return;
    identity(s);
    replaySelection(s);
    JSONObject scope = s.object("scope"),
        session = scope.optJSONObject("session"),
        recording = scope.optJSONObject("recording");
    LinearLayout playback = Ui.panel(content, "Recorded playback");
    Ui.note(
        playback,
        "The source clock and observation timestamps come from the server. Online means connected"
            + " to replay, not live acquisition.");
    if (recording != null && recording.has("startAt")) {
      long start = Values.time(recording.optString("startAt")),
          duration = Math.max(1, (Values.time(recording.optString("endAt")) - start) / 1000);
      SeekBar position = new SeekBar(requireContext());
      position.setMax((int) Math.min(Integer.MAX_VALUE, duration));
      position.setContentDescription("Inspect recorded position in source seconds");
      position.setProgress(
          (int)
              Math.min(
                  duration,
                  Math.max(
                      0,
                      (Values.time(
                                  session == null
                                      ? recording.optString("startAt")
                                      : session.optString("cursorAt"))
                              - start)
                          / 1000)));
      playback.addView(position);
      position.setOnSeekBarChangeListener(
          new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int n, boolean user) {}

            public void onStartTrackingTouch(SeekBar b) {}

            public void onStopTrackingTouch(SeekBar b) {
              Instant at = Instant.ofEpochMilli(start).plusSeconds(b.getProgress());
              model.set("inspectFrom", at.toString());
              model.set("inspectTo", at.plusSeconds(10).toString());
              model.refresh();
            }
          });
      Ui.note(
          playback, "Inspection position only; scrubbing does not move the server replay cursor.");
      Ui.button(
          playback,
          "Follow replay cursor",
          () -> {
            model.remove("inspectFrom");
            model.remove("inspectTo");
            model.refresh();
          });
    }
    if (repository().admin()) {
      JSONArray recordings = s.array("recordings");
      List<JSONObject> sources = new ArrayList<>();
      List<String> labels = new ArrayList<>();
      for (int i = 0; i < recordings.length(); i++) {
        JSONObject r = recordings.optJSONObject(i);
        if (!r.optString("sourceType").equals("SIMULATION")) {
          sources.add(r);
          labels.add(r.optString("name") + " / " + r.optString("sourceType"));
        }
      }
      if (!sources.isEmpty()) {
        int selected = 0;
        for (int i = 0; i < sources.size(); i++)
          if (sources.get(i).optString("id").equals(model.value("sourceRecording", "")))
            selected = i;
        final int defaultIndex = selected;
        Ui.spinner(
                playback,
                "Source recording",
                labels,
                selected,
                i -> model.set("sourceRecording", sources.get(i).optString("id")))
            .setId(R.id.source_picker);
        List<String> speeds = Arrays.asList("0.5", "1", "2", "5", "10");
        Ui.spinner(
                playback,
                "Speed (fixed at creation)",
                speeds,
                Math.max(0, speeds.indexOf(model.value("speed", "1"))),
                i -> model.set("speed", speeds.get(i)))
            .setId(R.id.speed_picker);
        Ui.button(
                playback,
                "Create replay session",
                () -> {
                  try {
                    JSONObject body =
                        new JSONObject()
                            .put("admissionId", admission())
                            .put(
                                "recordingId",
                                Long.parseLong(
                                    model.value(
                                        "sourceRecording",
                                        sources.get(defaultIndex).optString("id"))))
                            .put("speed", Double.parseDouble(model.value("speed", "1")));
                    model.mutate(
                        "/simulation-sessions",
                        body,
                        result -> {
                          try {
                            model.selectReplay(new JSONObject(result).getString("id"));
                          } catch (JSONException e) {
                            model.error(
                                "Replay created; refresh and select it from the session list.");
                          }
                        });
                  } catch (JSONException | NumberFormatException e) {
                    model.error("Choose a valid recording and speed.");
                  }
                })
            .setId(R.id.create_replay);
      } else Ui.note(playback, "No source recording is associated with this admission.");
      if (session != null && session.has("id")) {
        String state = session.optString("state");
        String[] actions = {"start", "pause", "resume", "stop"};
        int[] ids = {R.id.start_replay, R.id.pause_replay, R.id.resume_replay, R.id.stop_replay};
        for (int i = 0; i < actions.length; i++) {
          String action = actions[i];
          boolean enabled =
              action.equals("start")
                  ? state.equals("CREATED")
                  : action.equals("pause")
                      ? state.equals("RUNNING")
                      : action.equals("resume")
                          ? state.equals("PAUSED")
                          : Arrays.asList("CREATED", "RUNNING", "PAUSED").contains(state);
          com.google.android.material.button.MaterialButton b =
              Ui.button(
                  playback,
                  action,
                  () ->
                      model.mutate(
                          "/simulation-sessions/" + session.optLong("id") + "/" + action,
                          new JSONObject(),
                          r -> {}));
          b.setId(ids[i]);
          b.setEnabled(enabled);
        }
      }
    } else Ui.note(playback, "Only administrators can create or control server replay sessions.");
    signals(s);
  }
}
