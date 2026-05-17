package pro.sketchware.activities.importicon.adapters;


import android.content.Context;
import android.graphics.PorterDuff;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import android.graphics.Color;

import pro.sketchware.databinding.ImportIconListItemBinding;
import pro.sketchware.utility.SvgUtils;

public class IconAdapter extends ListAdapter<Pair<String, String>, IconAdapter.ViewHolder> {
    private static final DiffUtil.ItemCallback<Pair<String, String>> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Pair<String, String> oldItem, @NonNull Pair<String, String> newItem) {
            return oldItem.first.equals(newItem.first);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Pair<String, String> oldItem, @NonNull Pair<String, String> newItem) {
            return true;
        }
    };

    private final SvgUtils svgUtils;
    private final OnIconSelectedListener listener;
    private String selected_icon_type;
    private int selected_color;

    private final Set<Pair<String, String>> selectedItems = new HashSet<>();

    public IconAdapter(Context context, String selected_icon_type, int selected_color, OnIconSelectedListener listener) {
        super(DIFF_CALLBACK);
        svgUtils = new SvgUtils(context);
        this.selected_icon_type = selected_icon_type;
        this.selected_color = selected_color;
        this.listener = listener;
    }

    public void setSelectedIconType(String selected_icon_type) {
        this.selected_icon_type = selected_icon_type;
    }

    public void setSelectedColor(int selected_color) {
        this.selected_color = selected_color;
    }

    public void toggleSelection(int position) {
        Pair<String, String> item = getItem(position);
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
        }
        notifyItemChanged(position);
    }

    public void clearSelection() {
        selectedItems.clear();
        notifyDataSetChanged();
    }

    public Set<Pair<String, String>> getSelectedItems() {
        return selectedItems;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String filePath = getItem(position).second + File.separator + selected_icon_type + ".svg";
        svgUtils.loadImage(holder.itemBinding.img, filePath);
        holder.itemBinding.img.setColorFilter(selected_color, PorterDuff.Mode.SRC_IN);
        holder.itemBinding.title.setText(getItem(position).first);
        if (selectedItems.contains(getItem(position))) {
            holder.itemView.setBackgroundColor(Color.parseColor("#40000000")); // Semi-transparent grey
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImportIconListItemBinding binding = ImportIconListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    public interface OnIconSelectedListener {
        void onIconSelected(int position);
        void onIconLongSelected(int position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ImportIconListItemBinding itemBinding;

        public ViewHolder(ImportIconListItemBinding binding) {
            super(binding.getRoot());
            itemBinding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int position = getLayoutPosition();
                if (listener != null) {
                    listener.onIconSelected(position);
                }
            });
            binding.getRoot().setOnLongClickListener(v -> {
                int position = getLayoutPosition();
                if (listener != null) {
                    listener.onIconLongSelected(position);
                }
                return true;
            });
        }
    }
}

