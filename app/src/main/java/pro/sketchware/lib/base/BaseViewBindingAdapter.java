package pro.sketchware.lib.base;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import java.util.ArrayList;
import java.util.Objects;

public abstract class BaseViewBindingAdapter<T> extends RecyclerView.Adapter<BaseViewBindingAdapter.ViewHolder> {

    private ArrayList<T> items = new ArrayList<>();

    @NonNull
    public abstract ViewBinding getViewBinding(LayoutInflater inflater, ViewGroup parent);

    public abstract void onBindView(@NonNull ViewBinding binding, int position);

    @NonNull
    @Override
    public final ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(getViewBinding(LayoutInflater.from(parent.getContext()), parent));
    }

    @Override
    public final void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        onBindView(holder.binding, position);
    }

    @Override
    public final int getItemCount() {
        return items.size();
    }

    public void setItems(@NonNull ArrayList<T> newItems) {
        ArrayList<T> oldItems = this.items;
        this.items = newItems;
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldItems.size(); }
            @Override public int getNewListSize() { return newItems.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                return Objects.equals(oldItems.get(oldPos), newItems.get(newPos));
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                return Objects.equals(oldItems.get(oldPos), newItems.get(newPos));
            }
        }).dispatchUpdatesTo(this);
    }

    public T getItem(int position) {
        return items.get(position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewBinding binding;

        public ViewHolder(@NonNull ViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
