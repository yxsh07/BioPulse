package org.biopulse.android.ui.components;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import org.biopulse.android.R;

public final class RowsAdapter extends ListAdapter<RowsAdapter.Row, RowsAdapter.Holder> {
  public static final class Row {
    public final long id;
    public final String badge, title, detail;
    public final Runnable click;

    public Row(long id, String badge, String title, String detail, Runnable click) {
      this.id = id;
      this.badge = badge;
      this.title = title;
      this.detail = detail;
      this.click = click;
    }
  }

  public RowsAdapter() {
    super(
        new DiffUtil.ItemCallback<Row>() {
          public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.id == b.id;
          }

          public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.badge.equals(b.badge) && a.title.equals(b.title) && a.detail.equals(b.detail);
          }
        });
    setHasStableIds(true);
    setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
  }

  @Override
  public long getItemId(int position) {
    return getItem(position).id;
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
    return new Holder(
        LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull Holder h, int position) {
    Row r = getItem(position);
    h.badge.setText(r.badge);
    h.title.setText(r.title);
    h.detail.setText(r.detail);
    h.itemView.setOnClickListener(
        v -> {
          if (r.click != null) r.click.run();
        });
    h.itemView.setClickable(r.click != null);
    h.itemView.setContentDescription(r.badge + ". " + r.title + ". " + r.detail);
  }

  static final class Holder extends RecyclerView.ViewHolder {
    final TextView badge, title, detail;

    Holder(View view) {
      super(view);
      badge = view.findViewById(R.id.row_badge);
      title = view.findViewById(R.id.row_title);
      detail = view.findViewById(R.id.row_detail);
    }
  }
}
