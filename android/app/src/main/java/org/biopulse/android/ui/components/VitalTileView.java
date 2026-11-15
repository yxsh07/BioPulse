package org.biopulse.android.ui.components;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.card.MaterialCardView;
import java.util.Locale;
import org.biopulse.android.R;

public final class VitalTileView extends MaterialCardView {
  public VitalTileView(
      Context context, String metric, String label, String value, String unit, String metadata) {
    super(context);
    setRadius(0);
    setCardElevation(0);
    setCardBackgroundColor(ContextCompat.getColor(context, R.color.panel));
    setStrokeWidth(Ui.dp(context, 1));
    setStrokeColor(ContextCompat.getColor(context, R.color.boundary));
    LayoutInflater.from(context).inflate(R.layout.view_vital_tile, this, true);
    ((TextView) findViewById(R.id.vital_label)).setText(label.toUpperCase(Locale.ROOT));
    TextView reading = findViewById(R.id.vital_value);
    reading.setText(value);
    if (metric.equals("BP")) reading.setTextSize(32);
    ((TextView) findViewById(R.id.vital_unit)).setText(unit);
    ((TextView) findViewById(R.id.vital_metadata)).setText(metadata);
    setContentDescription(label + ", " + value + " " + unit + ". " + metadata);
  }
}
