package pro.sketchware.ai.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.lC;
import pro.sketchware.ai.adapters.WorkspaceProjectsAdapter;
import pro.sketchware.ai.models.Workspace;
import pro.sketchware.ai.storage.WorkspaceManager;
import pro.sketchware.databinding.FragmentWorkspaceProjectsBinding;

public class WorkspaceProjectsFragment extends Fragment
        implements WorkspaceProjectsAdapter.OnProjectActionListener {

    private FragmentWorkspaceProjectsBinding binding;
    private WorkspaceManager workspaceManager;
    private WorkspaceProjectsAdapter adapter;
    private String workspaceId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWorkspaceProjectsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            workspaceId = args.getString("workspace_id");
        }
        if (workspaceId == null) return;

        workspaceManager = new WorkspaceManager(requireContext());

        adapter = new WorkspaceProjectsAdapter(this);
        binding.projectsList.setAdapter(adapter);
        refreshProjects();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && workspaceId != null) {
            refreshProjects();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void refreshProjects() {
        if (binding == null || workspaceId == null) return;

        Workspace workspace = workspaceManager.getWorkspace(workspaceId);
        if (workspace == null) return;

        List<String> projectIds = workspace.getProjectIds();
        List<HashMap<String, Object>> allProjects = lC.a();
        List<WorkspaceProjectsAdapter.ProjectInfo> items = new ArrayList<>();

        for (String scId : projectIds) {
            String appName = null;
            String packageName = null;

            for (HashMap<String, Object> projectData : allProjects) {
                Object pId = projectData.get("sc_id");
                if (pId != null && pId.toString().equals(scId)) {
                    Object nameObj = projectData.get("my_app_name");
                    appName = nameObj != null ? nameObj.toString() : null;
                    Object pkgObj = projectData.get("my_sc_pkg_name");
                    packageName = pkgObj != null ? pkgObj.toString() : null;
                    break;
                }
            }

            if (appName == null) {
                appName = "Project " + scId;
            }

            items.add(new WorkspaceProjectsAdapter.ProjectInfo(scId, appName, packageName));
        }

        adapter.setProjects(items);

        if (items.isEmpty()) {
            binding.emptyState.setVisibility(View.VISIBLE);
            binding.projectsList.setVisibility(View.GONE);
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.projectsList.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRemoveProject(String scId) {
        Workspace workspace = workspaceManager.getWorkspace(workspaceId);
        if (workspace != null) {
            workspace.removeProject(scId);
            workspaceManager.updateWorkspace(workspace);
            refreshProjects();
        }
    }
}
