package pro.sketchware.activities.main.fragments.agent.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.models.Workspace;
import pro.sketchware.databinding.ItemWorkspaceBinding;

public class WorkspacesAdapter extends RecyclerView.Adapter<WorkspacesAdapter.ViewHolder> {

    public interface OnWorkspaceClickListener {
        void onWorkspaceClick(Workspace workspace);
    }

    private List<Workspace> workspaces = new ArrayList<>();
    private final OnWorkspaceClickListener listener;

    public WorkspacesAdapter(@NonNull OnWorkspaceClickListener listener) {
        this.listener = listener;
    }

    public void setWorkspaces(@NonNull List<Workspace> workspaces) {
        this.workspaces = new ArrayList<>(workspaces);
        notifyDataSetChanged();
    }

    @NonNull
    public List<Workspace> getWorkspaces() {
        return workspaces;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWorkspaceBinding binding = ItemWorkspaceBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Workspace workspace = workspaces.get(position);
        holder.bind(workspace);
    }

    @Override
    public int getItemCount() {
        return workspaces.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemWorkspaceBinding binding;

        ViewHolder(@NonNull ItemWorkspaceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Workspace workspace) {
            binding.workspaceName.setText(workspace.getName());

            String description = workspace.getDescription();
            if (description != null && !description.isEmpty()) {
                binding.workspaceDescription.setText(description);
                binding.workspaceDescription.setVisibility(View.VISIBLE);
            } else {
                binding.workspaceDescription.setVisibility(View.GONE);
            }

            int projectCount = workspace.getProjectIds().size();
            if (projectCount == 0) {
                binding.workspaceProjectCount.setText("No projects");
            } else if (projectCount == 1) {
                binding.workspaceProjectCount.setText("1 project");
            } else {
                binding.workspaceProjectCount.setText(projectCount + " projects");
            }

            binding.getRoot().setOnClickListener(v -> listener.onWorkspaceClick(workspace));
        }
    }
}
