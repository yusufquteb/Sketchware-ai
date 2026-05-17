package pro.sketchware.ai.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import pro.sketchware.ai.models.Workspace;
import pro.sketchware.utility.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorkspaceManager {

    private final File storageDir;
    private final ConversationManager conversationManager;

    public WorkspaceManager(@NonNull Context context) {
        storageDir = new File(context.getFilesDir(), "ai_agent/workspaces");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        conversationManager = new ConversationManager(context);
    }

    public void saveWorkspace(@NonNull Workspace workspace) {
        File file = new File(storageDir, workspace.getId() + ".json");
        FileUtil.writeFile(file.getAbsolutePath(), workspace.toJson());
    }

    @Nullable
    public Workspace getWorkspace(@NonNull String id) {
        File file = new File(storageDir, id + ".json");
        if (!file.exists()) {
            return null;
        }
        return Workspace.fromJson(FileUtil.readFile(file.getAbsolutePath()));
    }

    @NonNull
    public List<Workspace> getAllWorkspaces() {
        List<Workspace> workspaces = new ArrayList<>();
        File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return workspaces;
        }
        for (File file : files) {
            Workspace workspace = Workspace.fromJson(FileUtil.readFile(file.getAbsolutePath()));
            if (workspace != null) {
                workspaces.add(workspace);
            }
        }
        Collections.sort(workspaces, (a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()));
        return workspaces;
    }

    public void deleteWorkspace(@NonNull String id) {
        File file = new File(storageDir, id + ".json");
        if (file.exists()) {
            file.delete();
        }
        conversationManager.deleteAllConversationsForWorkspace(id);
    }

    public void updateWorkspace(@NonNull Workspace workspace) {
        workspace.setUpdatedAt(System.currentTimeMillis());
        saveWorkspace(workspace);
    }
}
