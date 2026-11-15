package org.biopulse.android.ui;

import org.biopulse.android.R;
import org.biopulse.android.model.UiState;
import org.biopulse.android.presentation.PatientDetailViewModel;
import org.biopulse.android.ui.components.Ui;

public final class PatientDetailFragment extends RecordedFragment {
  public PatientDetailFragment() {
    super(R.layout.fragment_patient_detail, "Patient workstation", PatientDetailViewModel.class);
  }

  @Override
  protected void render(UiState s) {
    content.removeAllViews();
    if (s.documents.isEmpty()) return;
    identity(s);
    replaySelection(s);
    vitals(s);
    Ui.button(content, "Open monitoring", () -> nav(R.id.monitor, admissionArgs(admission())));
    Ui.button(content, "Historical trends", () -> nav(R.id.trends, admissionArgs(admission())));
    Ui.button(content, "Patient alerts", () -> nav(R.id.alerts, admissionArgs(admission())));
    Ui.button(content, "Monitoring report", () -> nav(R.id.report, admissionArgs(admission())));
    signals(s);
  }
}
