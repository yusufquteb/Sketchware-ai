package pro.sketchware.ai.adapters;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.AiProviderModels;
import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.ai.storage.AiPreferences;

/**
 * ViewPager2 adapter for the model selector sheet.
 * Each page is a RecyclerView listing one provider's models.
 * Connect to TabLayout via TabLayoutMediator.
 */
public class ModelProviderPagerAdapter extends RecyclerView.Adapter<ModelProviderPagerAdapter.PageHolder> {

    private final List<AiProvider> providers;
    private final AiPreferences preferences;
    private String selectedModelId;
    private final ModelSelectorAdapter.OnModelSelectedListener listener;

    public ModelProviderPagerAdapter(
            @NonNull List<AiProvider> providers,
            @NonNull AiPreferences preferences,
            String selectedModelId,
            @NonNull ModelSelectorAdapter.OnModelSelectedListener listener) {
        this.providers       = providers;
        this.preferences     = preferences;
        this.selectedModelId = selectedModelId;
        this.listener        = listener;
    }

    public void setSelectedModelId(String modelId) {
        this.selectedModelId = modelId;
        notifyItemRangeChanged(0, providers.size());
    }

    @NonNull @Override
    public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RecyclerView rv = new RecyclerView(parent.getContext());
        rv.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.MATCH_PARENT));
        rv.setLayoutManager(new LinearLayoutManager(parent.getContext()));
        rv.setPadding(0, 8, 0, 24);
        rv.setClipToPadding(false);
        return new PageHolder(rv);
    }

    @Override
    public void onBindViewHolder(@NonNull PageHolder holder, int position) {
        AiProvider p = providers.get(position);
        ModelSelectorAdapter adapter = new ModelSelectorAdapter(listener);
        adapter.setSelectedModelId(selectedModelId);
        adapter.setModels(modelsFor(p));
        holder.rv.setAdapter(adapter);
    }

    @Override
    public int getItemCount() { return providers.size(); }

    private List<ModelInfo> modelsFor(AiProvider p) {
        List<ModelInfo> cached = preferences.getCachedModels(p);
        if (cached != null && !cached.isEmpty()) return cached;
        List<String> ids = AiProviderModels.getStaticModels(p);
        List<ModelInfo> result = new ArrayList<>(ids.size());
        for (String id : ids) result.add(new ModelInfo(id, id, p, 0, null));
        return result;
    }

    static class PageHolder extends RecyclerView.ViewHolder {
        final RecyclerView rv;
        PageHolder(@NonNull RecyclerView rv) { super(rv); this.rv = rv; }
    }
}
