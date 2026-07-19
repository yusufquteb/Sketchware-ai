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
import pro.sketchware.ai.orchestrator.AgentOrchestrator;
import pro.sketchware.ai.orchestrator.ExecutionPlan;
import pro.sketchware.ai.orchestrator.PlanStep;
import pro.sketchware.ai.security.PromptSanitizer;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.storage.ConversationManager;
import pro.sketchware.ai.storage.WorkspaceManager;
import pro.sketchware.ai.tools.ToolRegistry;

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
    @Nullable private AgentOrchestrator agentOrchestrator;
    @Nullable private ChatCoordinator.AiResponseCallback activeCallback;
    private final StringBuilder currentText = new StringBuilder();
    @Nullable private WeakReference<ChatCoordinator> coordinatorRef;

    /**
     * When true, {@link #onUserMessageReady} routes through {@link AgentOrchestrator}
     * (plan-up-front, then execute) instead of {@link AgentExecutor} (turn-by-turn
     * tool-calling loop). Defaults to false so existing behavior is unchanged unless
     * explicitly opted into — this class remains the single wiring point used by
     * ChatViewModel/ChatCoordinator either way, so no other file needs to change to
     * flip engines. See executeViaOrchestrator() for what gets reused from the
     * AgentExecutor path (persistence, system messages, Stage3 message bridging).
     */
    // Default flipped to true (was false during the earlier activation-wiring phase, pending
    // the final-response synthesis fix — see AgentOrchestrator#synthesizeFinalResponse).
    // This branch and executeViaAgentExecutor() below are mutually exclusive by construction
    // (see onUserMessageReady's if/else), so AgentExecutor is simply never instantiated for
    // new messages now — there is no scenario where both engines run at once.
    private boolean useOrchestratorEngine = true;

    public void setUseOrchestratorEngine(boolean enabled) {
        this.useOrchestratorEngine = enabled;
    }

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
    /**
     * Point (b) of the "resent previous message" diagnosis: history is still deliberately
     * shared across a provider switch (full conversation continuity is intended — see
     * onUserMessageReady's legacyHistory load), so this does NOT filter or scope history by
     * provider. What it does add is a visible marker the moment the provider actually
     * changes, so the user can see the boundary instead of a new provider's answer (which
     * may reflect the earlier provider's prior turn as context) looking like a stray repeat
     * of the old exchange. Fires only on a genuine change between two non-null providers —
     * not on the initial null → provider assignment during ChatViewModel.init(), and not on
     * a redundant re-set of the same provider.
     */
    public void setCurrentProvider(@Nullable AiProvider p) {
        if (this.currentProvider != null && p != null && this.currentProvider != p) {
            addSystemMessage("🔄 Switched to " + p.getDisplayName()
                    + " — this model can still see earlier turns in this conversation, including ones "
                    + this.currentProvider.getDisplayName() + " answered.");
        }
        this.currentProvider = p;
    }
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

        if (useOrchestratorEngine) {
            executeViaOrchestrator(userMessage.getText(), legacyHistory, finalProjectIds,
                    scope, scopedProjectId, callback);
            return;
        }

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
        if (agentOrchestrator != null) agentOrchestrator.cancel();
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

    // ─── Orchestrator engine path (plan-up-front instead of turn-by-turn) ────

    /**
     * Drives {@link AgentOrchestrator} instead of {@link AgentExecutor}, reusing
     * everything this class already does for persistence and UI feedback:
     *  - user message was already saved to ConversationManager above, unchanged.
     *  - onSynthesisChunk (point 7 synthesis step) forwards straight into
     *    callback.onTokenReceived, so ChatCoordinator's existing token-batching/
     *    typing-indicator logic applies with zero changes to ChatCoordinator itself.
     *  - point 7: the final answer and step notices are saved via the same
     *    toStage3()/ChatMessage.assistantMessage() bridge used by the
     *    AgentExecutor path below, not a second parallel format.
     *  - point 8: persisted via the same ConversationManager instance/conversationId.
     *
     * onPlanningChunk (the raw JSON plan text) is intentionally NOT forwarded to
     * callback.onTokenReceived/currentText anymore — that was the earlier known
     * limitation (a user watching it stream saw JSON, not prose). The user-visible
     * stream now comes only from onSynthesisChunk, the post-execution
     * natural-language answer (see AgentOrchestrator#synthesizeFinalResponse).
     */
    private void executeViaOrchestrator(String prompt, List<pro.sketchware.ai.models.ChatMessage> history,
                                         List<String> finalProjectIds, String scope,
                                         String scopedProjectId, ChatCoordinator.AiResponseCallback callback) {

        pro.sketchware.ai.tools.ToolRegistry registry =
                AgentExecutor.SCOPE_PROJECT.equals(scope) && scopedProjectId != null && !scopedProjectId.isEmpty()
                        ? ToolRegistry.createForProject(scopedProjectId)
                        : ToolRegistry.createGlobal();

        agentOrchestrator = new AgentOrchestrator(context, registry);

        agentOrchestrator.executeUserRequest(prompt, history, finalProjectIds, workspaceId,
                currentProvider, currentModelId, buildOrchestratorCallback(callback), null);
    }

    @NonNull
    private AgentOrchestrator.Callback buildOrchestratorCallback(
            @NonNull ChatCoordinator.AiResponseCallback callback) {

        return new AgentOrchestrator.Callback() {

            @Override
            public void onPlanningChunk(String textDelta) {
                // Raw plan JSON — deliberately not shown to the user (see javadoc above).
                // A lightweight status is already covered by onPlanReady() below.
            }

            @Override
            public void onSynthesisChunk(String textDelta) {
                if (textDelta == null) return;
                currentText.append(textDelta);
                callback.onTokenReceived(textDelta);
            }

            @Override
            public void onFinalResponse(String text) {
                // Authoritative final text from the synthesis call — overwrite rather than
                // append, in case a provider's client doesn't deliver true incremental
                // onChunk deltas and only calls onComplete with the full body.
                if (text != null && !text.isEmpty()) {
                    currentText.setLength(0);
                    currentText.append(text);
                }
            }

            @Override
            public void onPlanReady(ExecutionPlan plan) {
                addSystemMessage("📋 Plan ready — " + plan.getSteps().size() + " step(s)");
            }

            @Override
            public void onStepStarted(PlanStep step) {
                addSystemMessage("🔧 " + step.getToolName());
                if (toolProgressListener != null) {
                    mainHandler.post(() -> toolProgressListener.onToolStarted(
                            step.getToolName(), String.valueOf(step.hashCode())));
                }
            }

            @Override
            public void onStepCompleted(PlanStep step, ToolResult result) {
                if (toolProgressListener != null) {
                    mainHandler.post(() -> toolProgressListener.onToolCompleted(
                            String.valueOf(step.hashCode())));
                }
            }

            @Override
            public void onStepFailed(PlanStep step, String error) {
                addSystemMessage("⚠️ " + step.getToolName() + " failed: " + PromptSanitizer.redactForLog(error));
            }

            @Override
            public void onPlanComplete(ExecutionPlan plan) {
                String finalText = currentText.length() > 0
                        ? currentText.toString()
                        : "Plan finished (" + plan.getSteps().size() + " step(s)).";
                if (conversationId != null) {
                    conversationManager.saveMessage(conversationId,
                            pro.sketchware.ai.models.ChatMessage.assistantMessage(finalText, null));
                }
                callback.onStreamingComplete(finalText);
            }

            @Override
            public void onPlanningFailed(String error) {
                callback.onError(PromptSanitizer.redactForLog(error));
            }

            @Override
            public void onCancelled() {
                callback.onError("Cancelled");
            }

            @Override
            public void onCorrectionAttemptStarted(int attemptNumber, int maxAttempts) {
                addSystemMessage("🔁 Self-correction attempt " + attemptNumber + "/" + maxAttempts);
            }

            @Override
            public void onCorrected(int attemptsUsed) {
                addSystemMessage("✅ Build fixed after " + attemptsUsed + " attempt(s)");
            }

            @Override
            public void onExhausted(int attemptsUsed, String lastCompileLog) {
                addSystemMessage("❌ Self-correction gave up after " + attemptsUsed + " attempt(s)");
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
