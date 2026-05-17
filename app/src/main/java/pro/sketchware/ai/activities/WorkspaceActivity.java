package pro.sketchware.ai.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import a.a.a.lC;
import pro.sketchware.R;
import pro.sketchware.ai.adapters.ProjectSelectAdapter;
import pro.sketchware.ai.adapters.WorkspaceProjectsAdapter;
import pro.sketchware.ai.fragments.WorkspaceConversationsFragment;
import pro.sketchware.ai.fragments.WorkspaceProjectsFragment;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.Conversation;
import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.ai.models.Workspace;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.storage.ConversationManager;
import pro.sketchware.ai.storage.WorkspaceManager;
import pro.sketchware.databinding.ActivityWorkspaceBinding;
import pro.sketchware.databinding.DialogAddProjectToWorkspaceBinding;
import pro.sketchware.databinding.DialogCreateWorkspaceBinding;

public class WorkspaceActivity extends AppCompatActivity {

    public static final String EXTRA_WORKSPACE_ID = "workspace_id";

    private ActivityWorkspaceBinding binding;
    private WorkspaceManager workspaceManager;
    private ConversationManager conversationManager;
    private AiPreferences aiPreferences;
    private String workspaceId;
    private Workspace workspace;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityWorkspaceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        workspaceManager = new WorkspaceManager(this);
        conversationManager = new ConversationManager(this);
        aiPreferences = AiPreferences.getInstance(this);

        workspaceId = getIntent().getStringExtra(EXTRA_WORKSPACE_ID);
        if (workspaceId == null) {
            finish();
            return;
        }

        workspace = workspaceManager.getWorkspace(workspaceId);
        if (workspace == null) {
            Toast.makeText(this, "Workspace not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupViewPager();
        setupFab();
        applyInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        workspace = workspaceManager.getWorkspace(workspaceId);
        if (workspace != null) {
            binding.collapsingToolbar.setTitle(workspace.getName());
        }
    }

    private void setupToolbar() {
        binding.collapsingToolbar.setTitle(workspace.getName());
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit) {
                showEditWorkspaceDialog();
                return true;
            } else if (id == R.id.action_delete) {
                showDeleteConfirmation();
                return true;
            }
            return false;
        });
    }

    private void setupViewPager() {
        binding.viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                Bundle args = new Bundle();
                args.putString(EXTRA_WORKSPACE_ID, workspaceId);
                Fragment fragment;
                if (position == 0) {
                    fragment = new WorkspaceConversationsFragment();
                } else {
                    fragment = new WorkspaceProjectsFragment();
                }
                fragment.setArguments(args);
                return fragment;
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> tab.setText(position == 0 ? "Conversations" : "Projects")
        ).attach();
    }

    private void setupFab() {
        binding.fabAction.setOnClickListener(v -> {
            int currentTab = binding.viewPager.getCurrentItem();
            if (currentTab == 0) {
                createNewConversation();
            } else {
                showAddProjectDialog();
            }
        });

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                binding.fabAction.setImageResource(R.drawable.ic_mtrl_add);
                binding.fabAction.setContentDescription(
                        position == 0 ? "New Chat" : "Add Project");
            }
        });
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAction, (v, insets) -> {
            Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.bottomMargin = dpToPx(16) + navBars.bottom;
            params.rightMargin = dpToPx(16);
            v.setLayoutParams(params);
            return insets;
        });
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void createNewConversation() {
        AiProvider provider = aiPreferences.getSelectedProvider();
        String modelId = aiPreferences.getSelectedModel(provider);
        if (modelId == null) {
            List<ModelInfo> models = aiPreferences.getCachedModels(provider);
            if (!models.isEmpty()) {
                modelId = models.get(0).getId();
            }
        }

        Conversation conversation = new Conversation(
                workspaceId,
                "New Chat",
                modelId != null ? modelId : "",
                provider.name()
        );
        conversationManager.saveConversation(conversation);

        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversation.getId());
        intent.putExtra(ChatActivity.EXTRA_WORKSPACE_ID, workspaceId);
        startActivity(intent);
    }

    private void showAddProjectDialog() {
        workspace = workspaceManager.getWorkspace(workspaceId);
        if (workspace == null) return;

        DialogAddProjectToWorkspaceBinding dialogBinding =
                DialogAddProjectToWorkspaceBinding.inflate(LayoutInflater.from(this));

        List<HashMap<String, Object>> allProjects = lC.a();
        List<WorkspaceProjectsAdapter.ProjectInfo> projectInfoList = new ArrayList<>();
        Set<String> alreadyAdded = new HashSet<>(workspace.getProjectIds());

        for (HashMap<String, Object> project : allProjects) {
            Object scIdObj = project.get("sc_id");
            if (scIdObj == null) continue;
            String scId = scIdObj.toString();
            Object nameObj = project.get("my_app_name");
            String name = nameObj != null ? nameObj.toString() : scId;
            Object pkgObj = project.get("my_sc_pkg_name");
            String pkg = pkgObj != null ? pkgObj.toString() : "";
            projectInfoList.add(new WorkspaceProjectsAdapter.ProjectInfo(scId, name, pkg));
        }

        if (projectInfoList.isEmpty()) {
            Toast.makeText(this, "No projects available", Toast.LENGTH_SHORT).show();
            return;
        }

        ProjectSelectAdapter selectAdapter = new ProjectSelectAdapter();
        selectAdapter.setProjects(projectInfoList);
        selectAdapter.setAlreadyAdded(alreadyAdded);
        dialogBinding.projectsList.setAdapter(selectAdapter);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Add Projects")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Add", (dialog, which) -> {
                    Set<String> selectedIds = selectAdapter.getSelectedProjectIds();
                    if (!selectedIds.isEmpty()) {
                        for (String scId : selectedIds) {
                            workspace.addProject(scId);
                        }
                        workspaceManager.updateWorkspace(workspace);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditWorkspaceDialog() {
        workspace = workspaceManager.getWorkspace(workspaceId);
        if (workspace == null) return;

        DialogCreateWorkspaceBinding dialogBinding =
                DialogCreateWorkspaceBinding.inflate(LayoutInflater.from(this));
        dialogBinding.inputName.setText(workspace.getName());
        dialogBinding.inputDescription.setText(workspace.getDescription());

        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit Workspace")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = dialogBinding.inputName.getText() != null
                            ? dialogBinding.inputName.getText().toString().trim() : "";
                    String description = dialogBinding.inputDescription.getText() != null
                            ? dialogBinding.inputDescription.getText().toString().trim() : "";

                    if (name.isEmpty()) return;

                    workspace.setName(name);
                    workspace.setDescription(description);
                    workspaceManager.updateWorkspace(workspace);
                    binding.collapsingToolbar.setTitle(name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Workspace")
                .setMessage("This will permanently delete this workspace and all its conversations. This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    workspaceManager.deleteWorkspace(workspaceId);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
