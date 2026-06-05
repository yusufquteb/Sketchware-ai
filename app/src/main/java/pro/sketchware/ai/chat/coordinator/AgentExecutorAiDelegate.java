package pro.sketchware.ai.chat.coordinator;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.engine.AgentExecutor;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.models.Workspace;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.storage.ConversationManager;
import pro.sketchware.ai.storage.WorkspaceManager;

/**
 * Bridges {@link ChatCoordinator} with {@link AgentExecutor}.
 *
 * <p>Implements {@link ChatCoordinator.AiDelegate} so the coordinator calls it when the user
 * sends a message. Internally drives an {@link AgentExecutor} and routes all callbacks back
 * to the coordinator's {@link ChatCoordinator.AiResponseCallback}.
 *
 * <p>Also handles persistence: saves every message to {@link ConversationManager} so history
 * survives across sessions.
 */
public class AgentExecutorAiDelegate implements ChatCoordinator.AiDelegate {

    // ─── UI callback interfaces ────────────────────────────────────────────────

    public interface PulseListener {
        void onPulseRequired(String plan, Runnable onContinue, Runnable onCancel);
    }

    public interface ThinkingListener {
        void onThinking(String status);
    }

    public interface ToolProgressListener {
        void onToolStarted(String toolName, String toolCallId);
        void onToolProgress(String toolCallId, String status, int progress, boolean indeterminate);
        void onToolCompleted(String toolCallId);
    }

    public interface FailoverListener {
        void onFailover(String fromProvider, String toProvider, String toModel);
    }

    // ─── Dependencies ──────────────────────────────────────────────────────────

    private final Context context;
    private final ConversationManager conversationManager;
    private final WorkspaceManager workspaceManager;
    private final AiPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ─── Session config ────────────────────────────────────────────────────────

    @Nullable private String conversationId;
    @Nullable private String workspaceId;
    @Nullable private String scopedProjectId;
    @Nullable private String pageContext;
    @Nullable private AiProvider currentProvider;
    @Nullable private String currentModelId;

    // ─── Runtime state ─────────────────────────────────────────────────────────

    @Nullable private AgentExecutor agentExecutor;
    @Nullable private ChatCoordinator.AiResponseCallback activeCallback;
    private final StringBuilder currentText = new StringBuilder();
    @Nullable private WeakReference<ChatCoordinator> coordinatorRef;

    // ─── Optional UI listeners ─────────────────────────────────────────────────

    @Nullable private PulseListener pulseListener;
    @Nullable private ThinkingListener thinkingListener;
    @Nullable private ToolProgressListener toolProgressListener;
    @Nullable private FailoverListener failoverListener;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public AgentExecutorAiDelegate(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.conversationManager = new ConversationManager(context);
        this.workspaceManager    = new WorkspaceManager(context);
        this.preferences         = AiPreferences.getInstance(context);
    }

    // ─── Configuration setters ────────────────────────────────────────────────

    public void setConversationId(@Nullable String id)       { this.conversationId  = id; }
    public void setWorkspaceId(@Nullable String id)          { this.workspaceId     = id; }
    public void setScopedProjectId(@Nullable String id)      { this.scopedProjectId = id; }
    public void setPageContext(@Nullable String ctx)          { this.pageContext     = ctx; }
    public void setCurrentProvider(@Nullable AiProvider p)   { this.currentProvider = p; }
    public void setCurrentModelId(@Nullable String modelId)  { this.currentModelId  = modelId; }

    public void setCoordinator(@NonNull ChatCoordinator coordinator) {
        this.coordinatorRef = new WeakReference<>(coordinator);
    }

    public void setPulseListener(@Nullable PulseListener l)           { this.pulseListener        = l; }
    public void setThinkingListener(@Nullable ThinkingListener l)     { this.thinkingListener     = l; }
    public void setToolProgressListener(@Nullable ToolProgressListener l) { this.toolProgressListener = l; }
    public void setFailoverListener(@Nullable FailoverListener l)     { this.failoverListener     = l; }

    // ─── AiDelegate ──────────────────────────────────────────────────────────

    @Override
    public void onUserMessageReady(
            @NonNull pro.sketchware.ai.chat.model.ChatMessage userMessage,
            @NonNull List<pro.sketchware.ai.chat.model.ChatMessage> coordinatorHistory,
            @NonNull ChatCoordinator.AiResponseCallback callback) {

        this.activeCallback = callback;
        this.currentText.setLength(0);

        // Persist the user message
        if (conversationId != null && userMessage.getText() != null) {
            pro.sketchware.ai.models.ChatMessage legacyUser =
                    new pro.sketchware.ai.models.ChatMessage(
                            conversationId, userMessage.getText());
            conversationManager.saveMessage(conversationId, legacyUser);
        }

        // Signal streaming started (shows typing indicator / placeholder bubble)
        callback.onStreamingStarted();

        // Validate model
        if (currentModelId == null || currentModelId.isEmpty()) {
            callback.onError("No model selected. Tap the model name to choose one.");
            return;
        }
        if (currentProvider == null) {
            callback.onError("No AI provider selected. Please configure one in Settings.");
            return;
        }

        // Full history from ConversationManager (authoritative, Legacy format)
        List<pro.sketchware.ai.models.ChatMessage> legacyHistory =
                conversationId != null
                        ? conversationManager.getMessages(conversationId)
                        : new ArrayList<>();

        // Determine project scope
        List<String> projectIds = new ArrayList<>();
        String scope;
        if (scopedProjectId != null && !scopedProjectId.isEmpty()) {
            projectIds.add(scopedProjectId);
            scope = AgentExecutor.SCOPE_PROJECT;
        } else {
            Workspace workspace = workspaceId != null
                    ? workspaceManager.getWorkspace(workspaceId) : null;
            if (workspace != null) projectIds = workspace.getProjectIds();
            scope = AgentExecutor.SCOPE_GLOBAL;
        }

        final List<String> finalProjectIds = new ArrayList<>(projectIds);
        String systemPrompt = preferences.getSystemPrompt();

        agentExecutor = new AgentExecutor(
                context, finalProjectIds, workspaceId, scope, scopedProjectId);

        agentExecutor.setPulseCallback((plan, onContinue, onCancel) -> {
            if (pulseListener != null) {
                mainHandler.post(() -> pulseListener.onPulseRequired(plan, onContinue, onCancel));
            } else {
                onContinue.run();
            }
        });

        agentExecutor.execute(legacyHistory, currentModelId, currentProvider,
                systemPrompt, finalProjectIds, workspaceId, pageContext,
                buildAgentCallback(callback));
    }

    @Override
    public void onCancelRequested() {
        if (agentExecutor != null) agentExecutor.cancel();
    }

    // ─── Public helpers ───────────────────────────────────────────────────────

    /**
     * Loads conversation history from ConversationManager and converts it to Stage 3 format
     * so the coordinator can display it on startup.
     */
    @NonNull
    public List<pro.sketchware.ai.chat.model.ChatMessage> loadHistory(
            @Nullable String convId) {
        if (convId == null) return new ArrayList<>();
        List<pro.sketchware.ai.models.ChatMessage> legacy =
                conversationManager.getMessages(convId);
        List<pro.sketchware.ai.chat.model.ChatMessage> result = new ArrayList<>();
        for (pro.sketchware.ai.models.ChatMessage msg : legacy) {
            pro.sketchware.ai.chat.model.ChatMessage s3 = toStage3(msg);
            if (s3 != null) result.add(s3);
        }
        return result;
    }

    /** Cancels any in-flight request and shuts down the executor. */
    public void shutdown() {
        if (agentExecutor != null) {
            agentExecutor.shutdown();
            agentExecutor = null;
        }
        activeCallback = null;
    }

    // ─── Internal: AgentCallback builder ─────────────────────────────────────

    @NonNull
    private AgentExecutor.AgentCallback buildAgentCallback(
            @NonNull ChatCoordinator.AiResponseCallback callback) {

        return new AgentExecutor.AgentCallback() {

            @Override
            public void onStreamingChunk(String chunk) {
                if (chunk == null) return;
                currentText.append(chunk);
                callback.onTokenReceived(chunk);
            }

            @Override
            public void onAssistantMessage(pro.sketchware.ai.models.ChatMessage msg) {
                if (conversationId != null && msg != null) {
                    conversationManager.saveMessage(conversationId, msg);
                }
            }

            @Override
            public void onToolCallStarted(ToolCall toolCall) {
                if (toolCall == null) return;
                addSystemMessage("🔧 " + toolCall.getName());
                if (toolProgressListener != null) {
                    mainHandler.post(() -> toolProgressListener.onToolStarted(
                            toolCall.getName(), toolCall.getId()));
                }
            }

            @Override
            public void onToolCallProgress(String toolCallId, String status,
                    int progress, boolean indeterminate) {
                if (toolProgressListener != null) {
                    mainHandler.post(() -> toolProgressListener.onToolProgress(
                            toolCallId, status, progress, indeterminate));
                }
            }

            @Override
            public void onToolCallCompleted(ToolCall toolCall, ToolResult result) {
                if (toolProgressListener != null && toolCall != null) {
                    mainHandler.post(() -> toolProgressListener.onToolCompleted(
                            toolCall.getId()));
                }
            }

            @Override
            public void onToolMessage(pro.sketchware.ai.models.ChatMessage toolMessage) {
                if (conversationId != null && toolMessage != null) {
                    conversationManager.saveMessage(conversationId, toolMessage);
                }
            }

            @Override
            public void onResponseComplete(pro.sketchware.ai.models.ChatMessage finalMsg) {
                String finalText = (finalMsg != null && finalMsg.getContent() != null)
                        ? finalMsg.getContent()
                        : currentText.toString();
                callback.onStreamingComplete(finalText);
            }

            @Override
            public void onCancelled() {
                callback.onError("Cancelled");
            }

            @Override
            public void onError(String error) {
                callback.onError(error != null ? error : "Unknown error");
            }

            @Override
            public void onThinking(String status) {
                if (thinkingListener != null && status != null) {
                    mainHandler.post(() -> thinkingListener.onThinking(status));
                }
            }

            @Override
            public void onFailover(String fromProvider, String toProvider, String toModel) {
                addSystemMessage("⚡ Switched to " + toModel);
                if (failoverListener != null) {
                    mainHandler.post(() -> failoverListener.onFailover(
                            fromProvider, toProvider, toModel));
                }
            }
        };
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void addSystemMessage(@NonNull String text) {
        ChatCoordinator coordinator = coordinatorRef != null ? coordinatorRef.get() : null;
        if (coordinator != null) coordinator.addSystemMessage(text);
    }

    @Nullable
    private pro.sketchware.ai.chat.model.ChatMessage toStage3(
            @NonNull pro.sketchware.ai.models.ChatMessage legacy) {
        String text = legacy.getContent() != null ? legacy.getContent() : "";
        switch (legacy.getRole()) {
            case "user":
                return pro.sketchware.ai.chat.model.ChatMessage.user(text);
            case "assistant":
                return pro.sketchware.ai.chat.model.ChatMessage.ai(text);
            case "system":
                return pro.sketchware.ai.chat.model.ChatMessage.system(text);
            case "tool":
                return pro.sketchware.ai.chat.model.ChatMessage.tool(
                        legacy.getToolName() != null ? legacy.getToolName() : "tool",
                        legacy.getToolCallId() != null ? legacy.getToolCallId() : "",
                        text);
            default:
                return pro.sketchware.ai.chat.model.ChatMessage.ai(text);
        }
    }
}
