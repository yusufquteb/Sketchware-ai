package pro.sketchware.ai.integration;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import a.a.a.lC;
import pro.sketchware.ai.chat.ui.ChatActivity;
import pro.sketchware.ai.models.Conversation;
import pro.sketchware.ai.models.Workspace;
import pro.sketchware.ai.storage.ConversationManager;
import pro.sketchware.ai.storage.WorkspaceManager;

public final class AiProjectIntegrationHelper {
    private static final String WORKSPACE_PREFIX = "project-";

    private AiProjectIntegrationHelper() {
    }

    @NonNull
    public static Workspace ensureProjectWorkspace(@NonNull Context context, @NonNull String scId, @Nullable String projectName) {
        WorkspaceManager workspaceManager = new WorkspaceManager(context);
        String workspaceId = WORKSPACE_PREFIX + scId;
        Workspace workspace = workspaceManager.getWorkspace(workspaceId);
        String resolvedProjectName = resolveProjectName(scId, projectName);
        String workspaceName = resolvedProjectName + " AI";
        String description = "Dedicated AI workspace for project " + resolvedProjectName + " (" + scId + ").";
        if (workspace == null) {
            workspace = new Workspace(workspaceName, description);
            workspace.setId(workspaceId);
            workspace.addProject(scId);
            workspaceManager.saveWorkspace(workspace);
        } else {
            boolean changed = false;
            if (!workspace.hasProject(scId)) {
                workspace.addProject(scId);
                changed = true;
            }
            if (!workspaceName.equals(workspace.getName())) {
                workspace.setName(workspaceName);
                changed = true;
            }
            if (!description.equals(workspace.getDescription())) {
                workspace.setDescription(description);
                changed = true;
            }
            if (changed) {
                workspaceManager.updateWorkspace(workspace);
            }
        }
        return workspace;
    }

    @NonNull
    public static Conversation createConversation(@NonNull Context context, @NonNull Workspace workspace, @Nullable String title) {
        ConversationManager conversationManager = new ConversationManager(context);
        Conversation conversation = new Conversation(workspace.getId(), title == null || title.trim().isEmpty() ? "New Chat" : title.trim(), null, null);
        conversationManager.saveConversation(conversation);
        return conversation;
    }

    public static void openProjectChat(@NonNull Activity activity, @NonNull String scId, @Nullable String projectName,
                                       @NonNull String title, @Nullable String initialPrompt) {
        Workspace workspace = ensureProjectWorkspace(activity, scId, projectName);
        Conversation conversation = createConversation(activity, workspace, title);
        Intent intent = new Intent(activity, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_WORKSPACE_ID, workspace.getId());
        intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversation.getId());
        // ✅ Fix: pass EXTRA_PROJECT_ID so ChatActivity uses SCOPE_PROJECT instead of SCOPE_GLOBAL
        intent.putExtra(ChatActivity.EXTRA_PROJECT_ID, scId);
        if (initialPrompt != null && !initialPrompt.trim().isEmpty()) {
            intent.putExtra(ChatActivity.EXTRA_INITIAL_PROMPT, initialPrompt.trim());
        }
        activity.startActivity(intent);
    }

    /**
     * Opens the project chat with a specific page context (e.g. "errors", "blocks").
     * The pageContext is appended to the initial prompt so the model knows where it was launched from.
     */
    public static void openProjectChatWithContext(@NonNull Activity activity, @NonNull String scId,
                                                   @Nullable String projectName, @NonNull String title,
                                                   @Nullable String initialPrompt, @Nullable String pageContext) {
        Workspace workspace = ensureProjectWorkspace(activity, scId, projectName);
        Conversation conversation = createConversation(activity, workspace, title);
        Intent intent = new Intent(activity, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_WORKSPACE_ID, workspace.getId());
        intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversation.getId());
        intent.putExtra(ChatActivity.EXTRA_PROJECT_ID, scId);
        if (pageContext != null && !pageContext.trim().isEmpty()) {
            intent.putExtra(ChatActivity.EXTRA_PAGE_CONTEXT, pageContext.trim());
        }
        if (initialPrompt != null && !initialPrompt.trim().isEmpty()) {
            intent.putExtra(ChatActivity.EXTRA_INITIAL_PROMPT, initialPrompt.trim());
        }
        activity.startActivity(intent);
    }

    @NonNull
    public static String resolveProjectName(@NonNull String scId, @Nullable String fallback) {
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback.trim();
        }
        try {
            java.util.HashMap<String, Object> project = lC.b(scId);
            if (project != null) {
                Object workspaceName = project.get("my_ws_name");
                if (workspaceName != null && !String.valueOf(workspaceName).trim().isEmpty()) {
                    return String.valueOf(workspaceName).trim();
                }
                Object appName = project.get("my_app_name");
                if (appName != null && !String.valueOf(appName).trim().isEmpty()) {
                    return String.valueOf(appName).trim();
                }
            }
        } catch (Throwable ignored) {
        }
        return "Project " + scId;
    }
}
