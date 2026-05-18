// nikit overhaul — Task 1 — 2026-05
package pro.sketchware.ai.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.AiProviderModels;
import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.databinding.ItemModelBinding;

public class ModelSelectorAdapter extends RecyclerView.Adapter<ModelSelectorAdapter.ViewHolder> {

    public interface OnModelSelectedListener {
        void onModelSelected(ModelInfo model);
    }

    public interface OnModelLongClickListener {
        boolean onModelLongClick(ModelInfo model);
    }

    private final List<ModelInfo> models = new ArrayList<>();
    private final OnModelSelectedListener listener;
    @Nullable private String selectedModelId;
    @Nullable private OnModelLongClickListener longClickListener;

    public ModelSelectorAdapter(@NonNull OnModelSelectedListener listener) {
        this.listener = listener;
    }

    public void setOnModelLongClickListener(@Nullable OnModelLongClickListener l) {
        this.longClickListener = l;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemModelBinding binding = ItemModelBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(models.get(position));
    }

    @Override
    public int getItemCount() { return models.size(); }

    public void setModels(@NonNull List<ModelInfo> newModels) {
        List<ModelInfo> oldModels = new ArrayList<>(models);
        models.clear();
        models.addAll(newModels);
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldModels.size(); }
            @Override public int getNewListSize() { return newModels.size(); }
            @Override public boolean areItemsTheSame(int op, int np) {
                return Objects.equals(oldModels.get(op).getId(), newModels.get(np).getId());
            }
            @Override public boolean areContentsTheSame(int op, int np) {
                ModelInfo o = oldModels.get(op), n = newModels.get(np);
                return Objects.equals(o.getId(), n.getId()) && Objects.equals(o.getName(), n.getName());
            }
        }).dispatchUpdatesTo(this);
    }

    /**
     * Task 1: Rebuilds the model list from {@link AiProviderModels#getStaticModels(AiProvider)}.
     * Call when the user changes the active provider.
     * If the currently selected model is invalid for the new provider, resets to the default.
     *
     * @return the model ID that is now selected, or {@code null} if the list is empty
     */
    @Nullable
    public String updateModelsForProvider(@NonNull AiProvider provider) {
        List<String> staticIds = AiProviderModels.getStaticModels(provider);
        List<ModelInfo> newModels = new ArrayList<>();
        for (String id : staticIds) {
            newModels.add(new ModelInfo(id, id, provider, 0L, null));
        }

        if (!AiProviderModels.isModelValidForProvider(provider, selectedModelId)) {
            String def = AiProviderModels.getDefaultModel(provider);
            selectedModelId = def.isEmpty() ? null : def;
        }

        setModels(newModels);
        return selectedModelId;
    }

    public void setSelectedModelId(@Nullable String modelId) {
        String prev = this.selectedModelId;
        this.selectedModelId = modelId;
        if (!Objects.equals(prev, modelId)) notifyItemRangeChanged(0, getItemCount());
    }

    @Nullable
    public String getSelectedModelId() { return selectedModelId; }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemModelBinding binding;

        ViewHolder(@NonNull ItemModelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ModelInfo model) {
            String displayName = model.getName();
            if (TextUtils.isEmpty(displayName)) displayName = model.getId();
            binding.modelName.setText(displayName != null ? displayName : "");
            binding.modelId.setText(model.getId() != null ? model.getId() : "");

            boolean isSelected = model.getId() != null
                    && model.getId().equals(selectedModelId);
            binding.getRoot().setChecked(isSelected);

            binding.getRoot().setOnClickListener(v -> {
                String prev = selectedModelId;
                selectedModelId = model.getId();
                if (!Objects.equals(prev, selectedModelId)) notifyItemRangeChanged(0, getItemCount());
                listener.onModelSelected(model);
            });

            binding.getRoot().setOnLongClickListener(v -> {
                if (longClickListener != null) return longClickListener.onModelLongClick(model);
                return false;
            });
        }
    }
}
