package pro.sketchware.ai.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pro.sketchware.databinding.ItemProjectSelectableBinding;

public class ProjectSelectAdapter extends RecyclerView.Adapter<ProjectSelectAdapter.ViewHolder> {

    private final List<WorkspaceProjectsAdapter.ProjectInfo> projects = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private final Set<String> alreadyAddedIds = new HashSet<>();

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProjectSelectableBinding binding = ItemProjectSelectableBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WorkspaceProjectsAdapter.ProjectInfo project = projects.get(position);
        holder.bind(project);
    }

    @Override
    public int getItemCount() {
        return projects.size();
    }

    public void setProjects(@NonNull List<WorkspaceProjectsAdapter.ProjectInfo> newProjects) {
        List<WorkspaceProjectsAdapter.ProjectInfo> oldProjects = new ArrayList<>(projects);
        projects.clear();
        projects.addAll(newProjects);
        selectedIds.clear();
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldProjects.size(); }
            @Override public int getNewListSize() { return newProjects.size(); }
            @Override public boolean areItemsTheSame(int op, int np) {
                return oldProjects.get(op).getScId().equals(newProjects.get(np).getScId());
            }
            @Override public boolean areContentsTheSame(int op, int np) {
                return java.util.Objects.equals(oldProjects.get(op).getName(), newProjects.get(np).getName());
            }
        }).dispatchUpdatesTo(this);
    }

    @NonNull
    public Set<String> getSelectedProjectIds() {
        return new HashSet<>(selectedIds);
    }

    public void setAlreadyAdded(@NonNull Set<String> ids) {
        alreadyAddedIds.clear();
        alreadyAddedIds.addAll(ids);
        notifyItemRangeChanged(0, getItemCount());
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemProjectSelectableBinding binding;

        ViewHolder(@NonNull ItemProjectSelectableBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull WorkspaceProjectsAdapter.ProjectInfo project) {
            binding.projectName.setText(project.getName());
            binding.projectPackage.setText(
                    TextUtils.isEmpty(project.getPackageName())
                            ? ""
                            : project.getPackageName());

            binding.projectIcon.setImageDrawable(null);

            String scId = project.getScId();
            boolean isAlreadyAdded = alreadyAddedIds.contains(scId);
            boolean isSelected = selectedIds.contains(scId);

            MaterialCardView cardView = binding.getRoot();
            cardView.setChecked(isAlreadyAdded || isSelected);
            cardView.setEnabled(!isAlreadyAdded);
            cardView.setAlpha(isAlreadyAdded ? 0.5f : 1.0f);

            binding.getRoot().setOnClickListener(v -> {
                if (isAlreadyAdded) return;
                if (selectedIds.contains(scId)) {
                    selectedIds.remove(scId);
                } else {
                    selectedIds.add(scId);
                }
                notifyItemChanged(getAdapterPosition());
            });
        }
    }
}
