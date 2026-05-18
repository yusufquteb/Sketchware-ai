package pro.sketchware.activities.resourceseditor.components.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.stream.Collectors;

import pro.sketchware.activities.resourceseditor.ResourcesEditorActivity;
import pro.sketchware.activities.resourceseditor.components.models.ColorModel;
import pro.sketchware.activities.resourceseditor.components.utils.ColorsEditorManager;
import pro.sketchware.databinding.PalletCustomviewBinding;
import pro.sketchware.utility.PropertiesUtil;

public class ColorsAdapter extends RecyclerView.Adapter<ColorsAdapter.ViewHolder> {

    private final ArrayList<ColorModel> originalData;
    private final HashMap<Integer, String> notesMap;
    private final ResourcesEditorActivity activity;
    private final ColorsEditorManager colorsEditorManager;
    private ArrayList<ColorModel> filteredData;

    public ColorsAdapter(ColorsEditorManager colorsEditorManager, ArrayList<ColorModel> filteredData, ResourcesEditorActivity activity, HashMap<Integer, String> notesMap) {
        this.colorsEditorManager = colorsEditorManager;
        originalData = new ArrayList<>(filteredData);
        this.filteredData = filteredData;
        this.activity = activity;
        this.notesMap = notesMap;
    }

    @NonNull
    @Override
    public ColorsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PalletCustomviewBinding itemBinding = PalletCustomviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(itemBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ColorModel colorModel = filteredData.get(position);
        String colorName = colorModel.getColorName();
        String colorValue = colorModel.getColorValue();
        if (notesMap.containsKey(position)) {
            holder.itemBinding.tvTitle.setText(notesMap.get(position));
            holder.itemBinding.tvTitle.setVisibility(View.VISIBLE);
        } else {
            holder.itemBinding.tvTitle.setVisibility(View.GONE);
        }

        holder.itemBinding.title.setText(colorName);
        holder.itemBinding.sub.setText(colorValue);

        holder.itemBinding.color.setBackgroundColor(PropertiesUtil.parseColor(colorsEditorManager.getColorValue(activity.getApplicationContext(), colorValue, 4, activity.variant.contains("night"))));

        holder.itemBinding.backgroundCard.setOnClickListener(v -> activity.colorsEditor.showColorEditDialog(colorModel, position));
    }

    @Override
    public int getItemCount() {
        return filteredData.size();
    }

    public void filter(String newText) {
        List<ColorModel> oldData = filteredData;
        if (newText == null || newText.isEmpty()) {
            filteredData = new ArrayList<>(originalData);
        } else {
            String filterText = newText.toLowerCase().trim();
            filteredData = originalData.stream()
                    .filter(item -> item.getColorName().toLowerCase().contains(filterText) ||
                            item.getColorValue().toLowerCase().contains(filterText))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        List<ColorModel> newData = filteredData;
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldData.size(); }
            @Override public int getNewListSize() { return newData.size(); }
            @Override public boolean areItemsTheSame(int op, int np) {
                return java.util.Objects.equals(oldData.get(op).getColorName(), newData.get(np).getColorName());
            }
            @Override public boolean areContentsTheSame(int op, int np) {
                return java.util.Objects.equals(oldData.get(op).getColorValue(), newData.get(np).getColorValue());
            }
        }).dispatchUpdatesTo(this);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public PalletCustomviewBinding itemBinding;

        public ViewHolder(PalletCustomviewBinding itemBinding) {
            super(itemBinding.getRoot());
            this.itemBinding = itemBinding;
        }
    }
}
