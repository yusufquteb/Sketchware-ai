package com.besome.sketch.editor.logic;

import static com.besome.sketch.editor.logic.PaletteSelector.paletteSelectorRecord;
import static com.google.android.material.color.MaterialColors.harmonizeWithPrimary;
import static com.google.android.material.color.MaterialColors.isColorLight;
import static pro.sketchware.utility.ThemeUtils.getColor;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import androidx.recyclerview.widget.DiffUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import a.a.a.Vs;
import a.a.a.wB;
import pro.sketchware.R;
import pro.sketchware.databinding.PaletteSelectorItemBinding;

public class PaletteSelectorAdapter extends RecyclerView.Adapter<PaletteSelectorAdapter.PaletteSelectorViewHolder> {

    private final PaletteSelector paletteSelector;
    private final List<paletteSelectorRecord> paletteList = new ArrayList<>();
    private final Context context;
    private final Vs onBlockCategorySelectListener;
    private int selectedPosition = -1;

    public PaletteSelectorAdapter(PaletteSelector paletteSelector, Vs onBlockCategorySelectListener) {
        this.paletteSelector = paletteSelector;
        context = paletteSelector.getContext();
        this.onBlockCategorySelectListener = onBlockCategorySelectListener;
    }

    public void setPalettes(List<paletteSelectorRecord> list) {
        List<paletteSelectorRecord> newList = new ArrayList<>();
        for (paletteSelectorRecord palette : list) {
            if (paletteSelector.matchesSearch(palette.text())) {
                newList.add(palette);
            }
        }
        if (!newList.isEmpty() && selectedPosition < 0) {
            selectedPosition = 0;
        }
        List<paletteSelectorRecord> oldList = new ArrayList<>(paletteList);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldList.size(); }
            @Override public int getNewListSize() { return newList.size(); }
            @Override public boolean areItemsTheSame(int op, int np) {
                return oldList.get(op).index() == newList.get(np).index();
            }
            @Override public boolean areContentsTheSame(int op, int np) {
                paletteSelectorRecord o = oldList.get(op), n = newList.get(np);
                return Objects.equals(o.text(), n.text()) && o.color() == n.color();
            }
        });
        paletteList.clear();
        paletteList.addAll(newList);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public PaletteSelectorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PaletteSelectorItemBinding binding = PaletteSelectorItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new PaletteSelectorViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PaletteSelectorViewHolder holder, int position) {
        paletteSelectorRecord item = paletteList.get(position);
        int id = item.index();
        String title = item.text();
        int color = harmonizeWithPrimary(context, item.color());

        holder.binding.tvCategory.setText(title);
        holder.binding.bg.setBackgroundColor(color);
        holder.binding.tvCategory.setTextColor(
                position == selectedPosition ?
                        isColorLight(color) ? getColor(context, R.attr.colorOnSurface) : getColor(context, R.attr.colorOnSurfaceInverse)
                        : getColor(context, R.attr.colorOnSurface));
        holder.binding.bg.getLayoutParams().width = position == selectedPosition ? ViewGroup.LayoutParams.MATCH_PARENT : (int) wB.a(context, 4f);

        holder.itemView.setOnClickListener(v -> {
            int prev = selectedPosition;
            selectedPosition = holder.getAbsoluteAdapterPosition();
            if (prev >= 0) notifyItemChanged(prev);
            if (selectedPosition >= 0) notifyItemChanged(selectedPosition);
            if (onBlockCategorySelectListener != null) {
                onBlockCategorySelectListener.a(id, color);
            }
        });
    }

    @Override
    public int getItemCount() {
        return paletteList.size();
    }

    public void selectPaletteById(int tag) {
        for (int i = 0; i < paletteList.size(); i++) {
            int paletteId = paletteList.get(i).index();
            if (paletteId == tag) {
                int prev = selectedPosition;
                selectedPosition = i;
                if (prev >= 0) notifyItemChanged(prev);
                notifyItemChanged(selectedPosition);
                if (onBlockCategorySelectListener != null) {
                    onBlockCategorySelectListener.a(paletteId, paletteList.get(i).color());
                }
                break;
            }
        }
    }

    public void selectPosition(int pos) {
        if (pos >= 0 && pos < paletteList.size()) {
            int prev = selectedPosition;
            selectedPosition = pos;
            if (prev >= 0) notifyItemChanged(prev);
            notifyItemChanged(selectedPosition);
            if (onBlockCategorySelectListener != null) {
                onBlockCategorySelectListener.a(paletteList.get(pos).index(), paletteList.get(pos).color());
            }
        }
    }

    public static class PaletteSelectorViewHolder extends RecyclerView.ViewHolder {
        final PaletteSelectorItemBinding binding;

        public PaletteSelectorViewHolder(PaletteSelectorItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
