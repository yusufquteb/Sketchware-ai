package pro.sketchware.ai.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.offline.LocalModelCatalog;
import pro.sketchware.ai.offline.LocalModelManager;
import pro.sketchware.ai.offline.LocalModelState;

/**
 * RecyclerView adapter for the "Offline AI Models" card in {@code AiSettingsActivity}.
 *
 * <p>Flat list, no per-family group headers/titles — the outer "Offline AI Models" section
 * itself is already collapsible (see {@code offline_models_header}/{@code
 * offline_models_content} in {@code activity_ai_settings.xml}), so a second layer of
 * collapsible group headers per family (previously "Gemma models" / "Qwen models") was
 * redundant. Every entry is just its own card (icon, name, ACTIVE badge, status line) whose
 * body — capability note, progress, error, and the action button — expands/collapses on tap.
 */
public class OfflineModelAdapter extends RecyclerView.Adapter<OfflineModelAdapter.ViewHolder> {

    public interface Callback {
        void onDownload(LocalModelCatalog model);
        void onCancelDownload(LocalModelCatalog model);
        void onPauseDownload(LocalModelCatalog model);
        void onResumeDownload(LocalModelCatalog model);
        void onDelete(LocalModelCatalog model);
        void onSelect(LocalModelCatalog model);
        /** Open this model's Hugging Face page in the browser (gated models only). */
        void onOpenModelPage(LocalModelCatalog model);
    }

    // ── Item type ────────────────────────────────────────────────────────────

    /** A single model row. `bodyExpanded` tracks this specific card's own expand state. */
    private static final class ModelRow {
        final LocalModelCatalog model;
        boolean bodyExpanded;

        ModelRow(LocalModelCatalog model, boolean bodyExpanded) {
            this.model = model;
            this.bodyExpanded = bodyExpanded;
        }
    }

    private final List<LocalModelCatalog> models;
    private final List<ModelRow> rows = new ArrayList<>();
    private final LocalModelManager modelManager;
    private final Callback callback;

    public OfflineModelAdapter(@NonNull List<LocalModelCatalog> models,
                                @NonNull LocalModelManager modelManager,
                                @NonNull Callback callback) {
        this.models = models;
        this.modelManager = modelManager;
        this.callback = callback;
        buildRows();
    }

    /** Flattens {@link #models} into {@link #rows} — no grouping, no titles. */
    private void buildRows() {
        rows.clear();
        for (LocalModelCatalog m : models) rows.add(new ModelRow(m, false));
    }

    /** Call after the underlying {@link #models} list changes shape (not just model state). */
    public void rebuildGroups() {
        buildRows();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_offline_model, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        bindModel(holder, rows.get(position));
    }

    // ── Model binding ────────────────────────────────────────────────────────

    private void bindModel(ViewHolder holder, ModelRow row) {
        LocalModelCatalog model = row.model;
        LocalModelState state = modelManager.getState(model);
        boolean isSelected = modelManager.getSelectedModel() == model;
        boolean isPaused = state == LocalModelState.DOWNLOADING && modelManager.isPaused(model);
        boolean isDownloading = state == LocalModelState.DOWNLOADING;
        boolean isError = state == LocalModelState.ERROR;

        String recommendedTag = model == LocalModelCatalog.getRecommendedDefault() ? " ★" : "";
        holder.tvName.setText(model.getDisplayName() + recommendedTag);

        holder.tvTier.setText(model.getTier().label + " · " + model.getApproxSizeLabel()
                + " · needs about " + model.getMinRamGb() + " GB RAM");

        // Badge: ACTIVE takes priority over GATED so the user's current model always stands out.
        if (isSelected) {
            holder.tvBadge.setVisibility(View.VISIBLE);
            holder.tvBadge.setText("✓ ACTIVE");
        } else if (model.isGated()) {
            holder.tvBadge.setVisibility(View.VISIBLE);
            holder.tvBadge.setText("🔒 GATED");
        } else {
            holder.tvBadge.setVisibility(View.GONE);
        }

        // Status line + icon tint, similar role to the provider card's Enabled/Disabled status.
        String statusText;
        int statusColor;
        switch (state) {
            case READY:
                statusText = isSelected ? "Active — used for offline generation" : "Downloaded — ready to use";
                statusColor = 0xFF4CAF50; // green
                break;
            case DOWNLOADING:
                statusText = "Downloading" + (isPaused ? " · Paused" : "…");
                statusColor = 0xFFFF8F00; // amber
                break;
            case ERROR:
                statusText = "Download failed — tap to retry";
                statusColor = 0xFFE53935; // red
                break;
            case NOT_DOWNLOADED:
            default:
                statusText = model.isGated() ? "Not downloaded · requires HF token" : "Not downloaded";
                statusColor = 0; // themed default below
                break;
        }
        holder.tvStatus.setText(statusText);
        if (statusColor != 0) {
            holder.tvStatus.setTextColor(statusColor);
        } else {
            holder.tvStatus.setTextColor(
                    holder.itemView.getContext()
                            .obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary})
                            .getColor(0, 0xFF888888));
        }
        holder.icon.setImageResource(isSelected
                ? R.drawable.ic_mtrl_check
                : R.drawable.ic_mtrl_downloading);

        // Per-card expand/collapse — the header row (icon/name/status) is always visible;
        // the body (capability note, progress, error, HF link, action button) toggles.
        holder.body.setVisibility(row.bodyExpanded ? View.VISIBLE : View.GONE);
        holder.arrow.setRotation(row.bodyExpanded ? 180f : 0f);
        holder.headerRow.setOnClickListener(v -> {
            row.bodyExpanded = !row.bodyExpanded;
            holder.body.setVisibility(row.bodyExpanded ? View.VISIBLE : View.GONE);
            holder.arrow.setRotation(row.bodyExpanded ? 180f : 0f);
        });

        holder.tvCapabilityNote.setText(model.getCapabilityNote());

        String error = modelManager.getLastError(model);
        if (isError && error != null) {
            holder.tvError.setVisibility(View.VISIBLE);
            holder.tvError.setText("Download failed: " + error);
        } else {
            holder.tvError.setVisibility(View.GONE);
        }

        if (model.isGated()) {
            holder.btnHfPage.setVisibility(View.VISIBLE);
            holder.btnHfPage.setOnClickListener(v -> callback.onOpenModelPage(model));
        } else {
            holder.btnHfPage.setVisibility(View.GONE);
            holder.btnHfPage.setOnClickListener(null);
        }

        if (isDownloading) {
            int percent = modelManager.getProgressPercent(model);
            holder.progressLayout.setVisibility(View.VISIBLE);
            holder.progressBar.setProgress(percent);
            holder.tvPercent.setText(percent + "%" + (isPaused ? " · Paused" : ""));

            holder.btnPauseResume.setVisibility(View.VISIBLE);
            holder.btnPauseResume.setImageResource(isPaused
                    ? R.drawable.ic_mtrl_play
                    : R.drawable.ic_mtrl_pause);
            holder.btnPauseResume.setContentDescription(isPaused ? "Resume download" : "Pause download");
            holder.btnPauseResume.setOnClickListener(v -> {
                if (isPaused) {
                    callback.onResumeDownload(model);
                } else {
                    callback.onPauseDownload(model);
                }
            });
        } else {
            holder.progressLayout.setVisibility(View.GONE);
            holder.btnPauseResume.setVisibility(View.GONE);
            holder.btnPauseResume.setOnClickListener(null);
        }

        switch (state) {
            case READY:
                holder.btnAction.setText(isSelected ? "Delete" : "Use");
                holder.btnAction.setOnClickListener(v -> {
                    if (!isSelected) {
                        callback.onSelect(model);
                    } else {
                        callback.onDelete(model);
                    }
                });
                break;
            case DOWNLOADING:
                holder.btnAction.setText("Cancel");
                holder.btnAction.setOnClickListener(v -> callback.onCancelDownload(model));
                break;
            case ERROR:
                holder.btnAction.setText("Retry");
                holder.btnAction.setOnClickListener(v -> callback.onDownload(model));
                break;
            case NOT_DOWNLOADED:
            default:
                holder.btnAction.setText("Download");
                holder.btnAction.setOnClickListener(v -> callback.onDownload(model));
                break;
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View headerRow;
        final ImageView icon;
        final TextView tvName;
        final TextView tvBadge;
        final TextView tvTier;
        final TextView tvStatus;
        final ImageView arrow;
        final View body;
        final TextView tvCapabilityNote;
        final TextView tvError;
        final TextView tvPercent;
        final MaterialButton btnAction;
        final View progressLayout;
        final LinearProgressIndicator progressBar;
        final android.widget.ImageButton btnPauseResume;
        final View btnHfPage;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            headerRow = itemView.findViewById(R.id.layout_model_header_row);
            icon = itemView.findViewById(R.id.iv_model_icon);
            tvName = itemView.findViewById(R.id.tv_model_name);
            tvBadge = itemView.findViewById(R.id.tv_model_badge);
            tvTier = itemView.findViewById(R.id.tv_model_tier);
            tvStatus = itemView.findViewById(R.id.tv_model_status);
            arrow = itemView.findViewById(R.id.iv_model_expand_arrow);
            body = itemView.findViewById(R.id.layout_model_body);
            tvCapabilityNote = itemView.findViewById(R.id.tv_model_capability_note);
            tvError = itemView.findViewById(R.id.tv_model_error);
            tvPercent = itemView.findViewById(R.id.tv_model_progress_percent);
            btnAction = itemView.findViewById(R.id.btn_model_action);
            progressLayout = itemView.findViewById(R.id.layout_model_progress);
            progressBar = itemView.findViewById(R.id.progress_model_download);
            btnPauseResume = itemView.findViewById(R.id.btn_model_pause_resume);
            btnHfPage = itemView.findViewById(R.id.btn_model_hf_page);
        }
    }
}
