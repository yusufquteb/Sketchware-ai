package dev.aldi.sayuti.editor.manage;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;

/**
 * RecyclerView adapter for the Local Library Manager.
 *
 * Visual structure per item:
 *   ┌──────────────────────────────────────────────────────────┐
 *   │  [⚠]  library_name  2.9.0                    [⋮] [🔘]  │
 *   │        Current: 2.9.0  │  Latest: 2.11.0 ●             │
 *   │        Used in 3 projects                                │
 *   │        [  Update to 2.11.0  ]                            │
 *   │        ████████░░ Updating...                            │
 *   └──────────────────────────────────────────────────────────┘
 *
 * [🔘] = MaterialSwitch: ON means the library is active in the current project
 *        (or generally enabled if not in project context).
 *        [🗑] Delete has been REMOVED from the icon row — use the ⋮ menu.
 */
public class LocalLibraryAdapter
        extends RecyclerView.Adapter<LocalLibraryAdapter.ViewHolder> {

    // ─────────────────────────────────────────────────────────────────────────
    //  Callback interface
    // ─────────────────────────────────────────────────────────────────────────

    public interface ActionListener {
        /** ⋮ overflow menu */
        void onMenuRequested(LocalLibraryItem item, View anchorView);

        /** Switch toggled — add or remove library from current project */
        void onToggleEnabled(LocalLibraryItem item, boolean enabled);

        /** "Update" button */
        void onUpdateRequested(LocalLibraryItem item);

        /** Item card tapped */
        void onItemClicked(LocalLibraryItem item);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Data
    // ─────────────────────────────────────────────────────────────────────────

    private List<LocalLibraryItem> items = new ArrayList<>();
    private final ActionListener listener;

    /** When true, progress bar shown at the bottom (pagination loading) */
    private boolean isLoadingMore = false;

    private static final int TYPE_ITEM    = 0;
    private static final int TYPE_LOADING = 1;

    public LocalLibraryAdapter(ActionListener listener) {
        this.listener = listener;
        setHasStableIds(false);
    }

    public void setLoadingMore(boolean loading) {
        if (this.isLoadingMore == loading) return;
        if (loading) {
            this.isLoadingMore = true;
            notifyItemInserted(items.size());  // footer inserted after last real item
        } else {
            this.isLoadingMore = false;
            notifyItemRemoved(items.size());   // footer was at items.size() (now gone)
        }
    }

    /** Replace dataset with DiffUtil animation */
    public void submitList(List<LocalLibraryItem> newItems) {
        // If loading footer is active, remove it first before diffing
        if (isLoadingMore) {
            isLoadingMore = false;
            notifyItemRemoved(this.items.size());
        }
        List<LocalLibraryItem> oldItems = this.items;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldItems.size(); }
            @Override public int getNewListSize() { return newItems.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                return oldItems.get(o).name.equals(newItems.get(n).name);
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                LocalLibraryItem a = oldItems.get(o);
                LocalLibraryItem b = newItems.get(n);
                return a.version.equals(b.version)
                        && a.latestVersion.equals(b.latestVersion)
                        && a.projectCount == b.projectCount
                        && a.isUpdating == b.isUpdating
                        && a.isEnabledInProject == b.isEnabledInProject;
            }
        });
        this.items = new ArrayList<>(newItems);
        diff.dispatchUpdatesTo(this);
    }

    public void notifyItemChanged(String libName) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).name.equals(libName)) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RecyclerView.Adapter
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        if (position == items.size() && isLoadingMore) return TYPE_LOADING;
        return TYPE_ITEM;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_LOADING) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_loading_more, parent, false);
            return new ViewHolder(v);
        }
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_local_library, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        if (getItemViewType(position) == TYPE_LOADING) return;
        LocalLibraryItem item = items.get(position);
        Context ctx = h.itemView.getContext();

        // ── Name ──────────────────────────────────────────────────────────────
        h.tvLibraryName.setText(item.getDisplayName());

        // ── Built-in warning icon ─────────────────────────────────────────────
        if (h.ivBuiltInWarning != null)
            h.ivBuiltInWarning.setVisibility(item.isBuiltIn ? View.VISIBLE : View.GONE);

        // ── Version row ───────────────────────────────────────────────────────
        if (h.tvCurrentVersion != null)
            h.tvCurrentVersion.setText(ctx.getString(R.string.lib_current_version, item.version));

        if (h.tvLatestVersion != null) {
            if (item.latestVersion != null && !item.latestVersion.trim().isEmpty()) {
                h.tvLatestVersion.setVisibility(View.VISIBLE);
                if (item.isUpdateAvailable) {
                    h.tvLatestVersion.setText(ctx.getString(
                            R.string.lib_latest_version, item.latestVersion));
                    h.tvLatestVersion.setTextColor(
                            ContextCompat.getColor(ctx, R.color.lib_version_update_available));
                } else {
                    h.tvLatestVersion.setText(ctx.getString(R.string.lib_up_to_date));
                    h.tvLatestVersion.setTextColor(
                            ContextCompat.getColor(ctx, R.color.lib_version_up_to_date));
                }
            } else {
                h.tvLatestVersion.setVisibility(View.GONE);
            }
        }

        // ── Project count ─────────────────────────────────────────────────────
        if (h.tvProjectCount != null) {
            if (item.projectCount > 0) {
                h.tvProjectCount.setVisibility(View.VISIBLE);
                h.tvProjectCount.setText(ctx.getResources().getQuantityString(
                        R.plurals.lib_used_in_projects, item.projectCount, item.projectCount));
            } else {
                h.tvProjectCount.setVisibility(View.GONE);
            }
        }

        // ── Update button ─────────────────────────────────────────────────────
        if (h.btnUpdate != null) {
            if (item.isUpdateAvailable && !item.isUpdating) {
                h.btnUpdate.setVisibility(View.VISIBLE);
                h.btnUpdate.setOnClickListener(v -> {
                    if (listener != null) listener.onUpdateRequested(item);
                });
            } else {
                h.btnUpdate.setVisibility(View.GONE);
            }
        }

        // ── Updating progress ─────────────────────────────────────────────────
        if (h.progressUpdate != null)
            h.progressUpdate.setVisibility(item.isUpdating ? View.VISIBLE : View.GONE);
        if (h.tvUpdatingLabel != null)
            h.tvUpdatingLabel.setVisibility(item.isUpdating ? View.VISIBLE : View.GONE);

        // ── Card stroke for built-in ──────────────────────────────────────────
        if (h.card != null) {
            if (item.isBuiltIn) {
                h.card.setStrokeColor(ContextCompat.getColor(ctx, R.color.lib_builtin_border));
                h.card.setStrokeWidth(2);
            } else {
                h.card.setStrokeWidth(0);
            }
        }

        // ── MaterialSwitch: library enable/disable in current project ─────────
        if (h.switchEnabled != null) {
            // Suppress listener during bind to avoid spurious callbacks
            h.switchEnabled.setOnCheckedChangeListener(null);
            h.switchEnabled.setChecked(item.isEnabledInProject);
            h.switchEnabled.setOnCheckedChangeListener((btn, checked) -> {
                item.isEnabledInProject = checked;
                if (listener != null) listener.onToggleEnabled(item, checked);
            });
        }

        // ── Click handlers ────────────────────────────────────────────────────
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClicked(item);
        });

        if (h.ivMenu != null) {
            h.ivMenu.setOnClickListener(v -> {
                if (listener != null) listener.onMenuRequested(item, v);
            });
            h.ivMenu.setOnLongClickListener(v -> true);
        }

        if (h.switchEnabled != null) {
            h.switchEnabled.setOnLongClickListener(v -> true);
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + (isLoadingMore ? 1 : 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ViewHolder
    // ─────────────────────────────────────────────────────────────────────────

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView          card;
        final ImageView                 ivBuiltInWarning;
        final TextView                  tvLibraryName;
        final TextView                  tvCurrentVersion;
        final TextView                  tvLatestVersion;
        final TextView                  tvProjectCount;
        final MaterialButton            btnUpdate;
        final ImageView                 ivMenu;
        final MaterialSwitch            switchEnabled;   // replaces ivDelete
        final LinearProgressIndicator   progressUpdate;
        final TextView                  tvUpdatingLabel;

        ViewHolder(@NonNull View v) {
            super(v);
            card             = v.findViewById(R.id.card_library);
            ivBuiltInWarning = v.findViewById(R.id.iv_builtin_warning);
            tvLibraryName    = v.findViewById(R.id.tv_library_name);
            tvCurrentVersion = v.findViewById(R.id.tv_current_version);
            tvLatestVersion  = v.findViewById(R.id.tv_latest_version);
            tvProjectCount   = v.findViewById(R.id.tv_project_count);
            btnUpdate        = v.findViewById(R.id.btn_update);
            ivMenu           = v.findViewById(R.id.iv_menu);
            switchEnabled    = v.findViewById(R.id.switch_enabled);  // NEW
            progressUpdate   = v.findViewById(R.id.progress_update);
            tvUpdatingLabel  = v.findViewById(R.id.tv_updating_label);
        }
    }
}
