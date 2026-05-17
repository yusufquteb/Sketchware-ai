package pro.sketchware.activities.main.fragments.agent;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import pro.sketchware.activities.main.fragments.agent.adapters.WorkspacesAdapter;
import pro.sketchware.ai.activities.AiSettingsActivity;
import pro.sketchware.ai.activities.WorkspaceActivity;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.Workspace;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.storage.WorkspaceManager;
import pro.sketchware.databinding.DialogCreateWorkspaceBinding;
import pro.sketchware.databinding.FragmentAgentBinding;

public class AgentFragment extends Fragment {

    private FragmentAgentBinding binding;
    private WorkspacesAdapter adapter;
    private WorkspaceManager workspaceManager;
    private AiPreferences aiPreferences;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Transitions are handled by the host Activity's FragmentTransaction
        // (android.R.anim.fade_in / fade_out). Setting them here for show/hide
        // transactions has no effect and only adds overhead.
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAgentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        workspaceManager = new WorkspaceManager(requireContext());
        aiPreferences = AiPreferences.getInstance(requireContext());

        adapter = new WorkspacesAdapter(workspace -> {
            Intent intent = new Intent(requireContext(), WorkspaceActivity.class);
            intent.putExtra("workspace_id", workspace.getId());
            startActivity(intent);
        });

        binding.workspacesList.setAdapter(adapter);

        binding.iconSettings.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AiSettingsActivity.class)));

        // "+" button in title bar — quick shortcut to create a workspace
        // mirrors the FAB so the action is reachable without scrolling down
        binding.iconAddWorkspace.setOnClickListener(v -> showCreateWorkspaceDialog());

        binding.fabCreateWorkspace.setOnClickListener(v -> showCreateWorkspaceDialog());
        binding.btnCreateWorkspaceEmpty.setOnClickListener(v -> showCreateWorkspaceDialog());

        binding.btnSetupApiKey.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AiSettingsActivity.class)));

        binding.nestedScroll.setOnScrollChangeListener(
                (androidx.core.widget.NestedScrollView.OnScrollChangeListener)
                        (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (scrollY > oldScrollY) {
                        binding.fabCreateWorkspace.shrink();
                    } else if (scrollY < oldScrollY) {
                        binding.fabCreateWorkspace.extend();
                    }
                });

        checkApiKeyWarning();
        refreshWorkspaces();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            checkApiKeyWarning();
            refreshWorkspaces();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void refreshWorkspaces() {
        if (binding == null) return;

        List<Workspace> workspaces = workspaceManager.getAllWorkspaces();
        adapter.setWorkspaces(workspaces);
        updateEmptyState(workspaces.size());
    }

    private void updateEmptyState(int count) {
        if (binding == null) return;

        if (count == 0) {
            binding.emptyState.setVisibility(View.VISIBLE);
            binding.workspacesList.setVisibility(View.GONE);
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.workspacesList.setVisibility(View.VISIBLE);
        }
    }

    private void checkApiKeyWarning() {
        if (binding == null) return;

        boolean hasAnyKey = false;
        for (AiProvider provider : AiProvider.values()) {
            if (aiPreferences.hasApiKey(provider)) {
                hasAnyKey = true;
                break;
            }
        }
        binding.cardApiKeyWarning.setVisibility(hasAnyKey ? View.GONE : View.VISIBLE);
    }

    private void showCreateWorkspaceDialog() {
        DialogCreateWorkspaceBinding dialogBinding =
                DialogCreateWorkspaceBinding.inflate(LayoutInflater.from(requireContext()));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Create Workspace")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = dialogBinding.inputName.getText() != null
                            ? dialogBinding.inputName.getText().toString().trim() : "";
                    String description = dialogBinding.inputDescription.getText() != null
                            ? dialogBinding.inputDescription.getText().toString().trim() : "";

                    if (name.isEmpty()) {
                        return;
                    }

                    Workspace workspace = new Workspace(name, description);
                    workspaceManager.saveWorkspace(workspace);
                    refreshWorkspaces();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
