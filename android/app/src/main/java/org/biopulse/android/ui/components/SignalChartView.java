package org.biopulse.android.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.biopulse.android.R;

/** Draws only timestamped server points. Nulls and cadence gaps break the path. */
public final class SignalChartView extends View {
  public static final class Point {
    public final long at;
    public final Double value;

    public Point(long at, Double value) {
      this.at = at;
      this.value = value;
    }
  }

  private List<Point> points = Collections.emptyList();
  private int color;
  private long gap = 1000;
  private long viewportStart, viewportEnd;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();

  public SignalChartView(Context c) {
    this(c, null);
  }

  public SignalChartView(Context c, @Nullable AttributeSet attributes) {
    super(c, attributes);
    color = ContextCompat.getColor(c, R.color.ecg);
    setMinimumHeight(Ui.dp(c, 160));
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  public void setSeries(List<Point> values, int color, long gap, String description) {
    this.points = Collections.unmodifiableList(new ArrayList<>(values));
    this.color = color;
    this.gap = gap;
    setContentDescription(description);
    invalidate();
  }

  public void setViewport(long from, long to) {
    viewportStart = from;
    viewportEnd = to;
    invalidate();
  }

  @Override
  protected void onMeasure(int width, int height) {
    setMeasuredDimension(MeasureSpec.getSize(width), resolveSize(Ui.dp(getContext(), 170), height));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float width = getWidth(), height = getHeight(), margin = Ui.dp(getContext(), 10);
    paint.setStrokeWidth(Ui.dp(getContext(), 0.5f));
    paint.setColor(ContextCompat.getColor(getContext(), R.color.boundary));
    for (float x = margin; x < width; x += Ui.dp(getContext(), 32))
      canvas.drawLine(x, margin, x, height - margin, paint);
    for (float y = margin; y < height; y += Ui.dp(getContext(), 28))
      canvas.drawLine(margin, y, width - margin, y, paint);
    double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
    long first = Long.MAX_VALUE, last = Long.MIN_VALUE;
    for (Point p : points)
      if (p.value != null && Double.isFinite(p.value)) {
        min = Math.min(min, p.value);
        max = Math.max(max, p.value);
        first = Math.min(first, p.at);
        last = Math.max(last, p.at);
      }
    if (first == Long.MAX_VALUE) {
      paint.setColor(ContextCompat.getColor(getContext(), R.color.metadata));
      paint.setTextSize(Ui.dp(getContext(), 12));
      paint.setStyle(Paint.Style.FILL);
      canvas.drawText("No usable recorded samples", margin, height / 2, paint);
      return;
    }
    if (viewportEnd > viewportStart) {
      first = viewportStart;
      last = viewportEnd;
    }
    path.reset();
    boolean connected = false;
    long prior = 0;
    for (Point p : points) {
      if (p.value == null || !Double.isFinite(p.value)) {
        connected = false;
        continue;
      }
      float x =
          (float)
              (margin + (p.at - first) / (double) Math.max(1, last - first) * (width - 2 * margin));
      float y =
          (float)
              (height
                  - margin
                  - (p.value - min) / Math.max(0.000001, max - min) * (height - 2 * margin));
      if (connected && p.at - prior <= gap) path.lineTo(x, y);
      else path.moveTo(x, y);
      prior = p.at;
      connected = true;
    }
    paint.setColor(color);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(Ui.dp(getContext(), 1.4f));
    canvas.drawPath(path, paint);
    paint.setStyle(Paint.Style.FILL);
  }
}
