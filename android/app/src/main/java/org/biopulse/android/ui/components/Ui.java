package org.biopulse.android.ui.components;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.core.widget.TextViewCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.List;
import java.util.function.Consumer;
import org.biopulse.android.R;

public final class Ui {
  private Ui() {}

  public static int dp(Context c, float value) {
    return Math.round(c.getResources().getDisplayMetrics().density * value);
  }

  public static TextView text(Context c, String value, int appearance) {
    TextView t = new TextView(c);
    t.setText(value);
    TextViewCompat.setTextAppearance(t, appearance);
    t.setPadding(0, dp(c, 4), 0, dp(c, 4));
    return t;
  }

  public static LinearLayout panel(LinearLayout parent, String title) {
    Context c = parent.getContext();
    LinearLayout box = new LinearLayout(c);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
    box.setBackgroundResource(R.drawable.panel_border);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(c, 16);
    parent.addView(box, p);
    if (!title.isEmpty()) box.addView(text(c, title, R.style.Text_BioPulse_Section));
    return box;
  }

  public static void note(LinearLayout parent, String value) {
    parent.addView(text(parent.getContext(), value, R.style.Text_BioPulse_Metadata));
  }

  public static MaterialButton button(LinearLayout parent, String label, Runnable click) {
    MaterialButton b = new MaterialButton(parent.getContext());
    b.setText(label);
    b.setMinHeight(dp(parent.getContext(), 48));
    b.setOnClickListener(v -> click.run());
    parent.addView(b, new LinearLayout.LayoutParams(-1, -2));
    return b;
  }

  public static TextInputEditText field(LinearLayout parent, String hint, String value) {
    TextInputLayout layout = new TextInputLayout(parent.getContext());
    layout.setHint(hint);
    TextInputEditText edit = new TextInputEditText(parent.getContext());
    edit.setSingleLine(true);
    edit.setText(value);
    edit.setTextSize(14);
    layout.addView(edit, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(parent.getContext(), 8);
    parent.addView(layout, p);
    return edit;
  }

  public static Spinner spinner(
      LinearLayout parent,
      String label,
      List<String> labels,
      int selected,
      Consumer<Integer> changed) {
    Context c = parent.getContext();
    parent.addView(text(c, label, R.style.Text_BioPulse_Metadata));
    Spinner spinner = new Spinner(c);
    spinner.setContentDescription(label);
    spinner.setMinimumHeight(dp(c, 48));
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, labels);
    spinner.setAdapter(adapter);
    spinner.setSelection(Math.max(0, Math.min(selected, labels.size() - 1)));
    parent.addView(spinner, new LinearLayout.LayoutParams(-1, -2));
    spinner.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          public void onItemSelected(
              android.widget.AdapterView<?> p, View v, int position, long id) {
            changed.accept(position);
          }

          public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    return spinner;
  }

  public static void empty(LinearLayout parent, String explanation) {
    LinearLayout box = panel(parent, "No observations in this scope");
    note(box, explanation);
  }
}
