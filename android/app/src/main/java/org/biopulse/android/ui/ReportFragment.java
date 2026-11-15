package org.biopulse.android.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.biopulse.android.R;
import org.biopulse.android.model.UiState;
import org.biopulse.android.model.Values;
import org.biopulse.android.presentation.ReportViewModel;
import org.biopulse.android.ui.components.Ui;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ReportFragment extends RecordedFragment {
  private final ActivityResultLauncher<String> save =
      registerForActivityResult(
          new ActivityResultContracts.CreateDocument("text/csv"),
          uri -> {
            if (uri != null) save(uri);
          });

  public ReportFragment() {
    super(R.layout.fragment_report, "Monitoring reports", ReportViewModel.class);
  }

  @Override
  protected void render(UiState s) {
    content.removeAllViews();
    if (s.documents.isEmpty()) return;
    identity(s);
    replaySelection(s);
    interval(s);
    Ui.note(
        content,
        "Stored server CSV snapshots. The API does not offer PDF generation. Save and Share require"
            + " an explicit system action.");
    Ui.button(
            content,
            "Generate report",
            () -> {
              try {
                JSONObject scope = s.object("scope");
                String from =
                    ((android.widget.EditText) content.findViewById(R.id.from_input))
                        .getText()
                        .toString();
                String to =
                    ((android.widget.EditText) content.findViewById(R.id.to_input))
                        .getText()
                        .toString();
                org.biopulse.android.data.Repository.validateRange(from, to);
                model.set("from", from);
                model.set("to", to);
                finishIntervalEditing();
                JSONObject request =
                    new JSONObject()
                        .put("admissionId", admission())
                        .put("wardId", JSONObject.NULL)
                        .put("from", from)
                        .put("to", to);
                if (scope.has("recordingId"))
                  request.put("recordingId", scope.getLong("recordingId"));
                model.mutate("/reports", request, r -> model.set("page", "0"));
              } catch (JSONException | IllegalArgumentException e) {
                model.error("Choose a valid report interval.");
              }
            })
        .setId(R.id.generate_report);
    JSONArray reports = s.items("reports");
    for (int i = 0; i < reports.length(); i++) {
      JSONObject report = reports.optJSONObject(i);
      long id = report.optLong("id");
      LinearLayout box = Ui.panel(content, "BP-" + id);
      Ui.note(
          box,
          (report.isNull("admissionId")
                  ? "Ward " + report.optLong("wardId")
                  : "Admission " + report.optLong("admissionId"))
              + "\nObserved interval "
              + Values.stamp(report.optString("from"))
              + " → "
              + Values.stamp(report.optString("to"))
              + "\nGenerated "
              + Values.stamp(report.optString("createdAt"))
              + " · "
              + report.optString("createdBy"));
      Ui.button(
          box,
          "Preview CSV",
          () ->
              download(
                  id,
                  data -> {
                    android.widget.TextView text =
                        Ui.text(
                            requireContext(),
                            new String(data, StandardCharsets.UTF_8),
                            R.style.Text_BioPulse_Metadata);
                    text.setTextIsSelectable(true);
                    android.widget.ScrollView scroll =
                        new android.widget.ScrollView(requireContext());
                    scroll.addView(text);
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                            requireContext())
                        .setTitle("Report BP-" + id + " / RESEARCH_ONLY")
                        .setView(scroll)
                        .setPositiveButton("Close", null)
                        .show();
                  }));
      Ui.button(
              box,
              "Save CSV",
              () -> download(id, data -> save.launch("biopulse-report-" + id + ".csv")))
          .setId(i == 0 ? R.id.save_report : android.view.View.generateViewId());
      Ui.button(box, "Share CSV", () -> download(id, data -> share(id, data)))
          .setId(i == 0 ? R.id.share_report : android.view.View.generateViewId());
    }
    if (reports.length() == 0) Ui.empty(content, "No stored reports in this page of your scope.");
    int page = Integer.parseInt(model.value("page", "0"));
    Ui.button(
            content,
            "Previous reports",
            () -> {
              model.set("page", Integer.toString(Math.max(0, page - 1)));
              model.refresh();
            })
        .setEnabled(page > 0);
    Ui.button(
            content,
            "Next reports",
            () -> {
              model.set("page", Integer.toString(page + 1));
              model.refresh();
            })
        .setEnabled(s.object("reports").optBoolean("hasNext"));
  }

  private void download(long id, java.util.function.Consumer<byte[]> complete) {
    ((ReportViewModel) model)
        .download(
            id,
            data -> {
              if (isAdded() && getView() != null) complete.accept(data);
            });
  }

  private void save(Uri uri) {
    byte[] data = ((ReportViewModel) model).preparedCsv();
    if (data == null) {
      model.error("The report is no longer in memory. Download it again before saving.");
      return;
    }
    android.content.Context context = requireContext().getApplicationContext();
    repository()
        .io
        .execute(
            () -> {
              try (OutputStream stream = context.getContentResolver().openOutputStream(uri)) {
                if (stream == null)
                  throw new java.io.IOException("The destination could not be opened.");
                stream.write(data);
                new Handler(Looper.getMainLooper())
                    .post(
                        () -> {
                          if (isAdded())
                            android.widget.Toast.makeText(
                                    context, "CSV saved", android.widget.Toast.LENGTH_SHORT)
                                .show();
                        });
              } catch (Exception e) {
                new Handler(Looper.getMainLooper())
                    .post(
                        () ->
                            model.error("The CSV could not be saved. Choose another destination."));
              }
            });
  }

  private void share(long id, byte[] data) {
    android.content.Context context = requireContext();
    repository()
        .io
        .execute(
            () -> {
              try {
                File folder = new File(context.getCacheDir(), "exports");
                if (!folder.exists() && !folder.mkdirs())
                  throw new java.io.IOException("Cannot create private export folder");
                File file = new File(folder, "biopulse-report-" + id + ".csv");
                try (FileOutputStream stream = new FileOutputStream(file)) {
                  stream.write(data);
                }
                Uri uri =
                    FileProvider.getUriForFile(context, context.getPackageName() + ".files", file);
                new Handler(Looper.getMainLooper())
                    .post(
                        () -> {
                          if (isAdded()) {
                            Intent intent =
                                new Intent(Intent.ACTION_SEND)
                                    .setType("text/csv")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.setClipData(
                                android.content.ClipData.newRawUri("BioPulse CSV", uri));
                            startActivity(Intent.createChooser(intent, "Share research report"));
                          }
                        });
              } catch (Exception e) {
                new Handler(Looper.getMainLooper())
                    .post(() -> model.error("The report could not be prepared for sharing."));
              }
            });
  }
}
