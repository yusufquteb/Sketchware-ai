package pro.sketchware.ai.chat.state;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import pro.sketchware.ai.chat.coordinator.AgentExecutorAiDelegate;
import pro.sketchware.ai.chat.coordinator.ChatCoordinator;
import pro.sketchware.ai.models.AiProvider;

/**
 * Survives configuration changes (rotation). Holds ChatCoordinator and
 * AgentExecutorAiDelegate so an in-flight AI streaming call is never
 * cancelled when the user rotates the screen.
 *
 * <p>Activity calls {@link #init} once per conversation, then retrieves
 * the coordinator and delegate on every recreation via the same ViewModel instance.
 */
public class ChatViewModel extends AndroidViewModel {

    private ChatCoordinator coordinator;
    private AgentExecutorAiDelegate aiDelegate;

    private final MutableLiveData<ChatSessionState> sessionState =
            new MutableLiveData<>(new ChatSessionState(false, null, null));

    public ChatViewModel(@NonNull Application application) {
        super(application);
    }

    /**
     * Creates coordinator + delegate for the given session.
     * Idempotent — safe to call on every Activity recreation; only initializes once.
     * If the model changed since last init, pass non-null provider/modelId to update.
     */
    public void init(@NonNull String conversationId,
                     @NonNull String workspaceId,
                     @Nullable String projectId,
                     @Nullable String pageContext,
                     @Nullable AiProvider provider,
                     @Nullable String modelId) {
        if (coordinator != null) {
            // Rotation: just refresh model if caller provides one.
            if (provider != null || modelId != null) {
                updateModel(provider, modelId);
            }
            return;
        }

        Context context = getApplication();
        coordinator = new ChatCoordinator(context);
        aiDelegate  = new AgentExecutorAiDelegate(context);

        aiDelegate.setConversationId(conversationId);
        aiDelegate.setWorkspaceId(workspaceId);
        aiDelegate.setScopedProjectId(projectId);
        aiDelegate.setPageContext(pageContext);
        aiDelegate.setCoordinator(coordinator);
        coordinator.setAiDelegate(aiDelegate);

        updateModel(provider, modelId);
    }

    /** Updates model in delegate and propagates to observed state. */
    public void updateModel(@Nullable AiProvider provider, @Nullable String modelId) {
        if (aiDelegate != null) {
            aiDelegate.setCurrentProvider(provider);
            aiDelegate.setCurrentModelId(modelId);
        }
        ChatSessionState current = sessionState.getValue();
        if (current != null) {
            sessionState.setValue(current.withModel(provider, modelId));
        }
    }

    /** Called by the Activity's CoordinatorListener to track running state. */
    public void setAiRunning(boolean running) {
        ChatSessionState current = sessionState.getValue();
        if (current != null) {
            sessionState.setValue(current.withAiRunning(running));
        }
    }

    @NonNull
    public LiveData<ChatSessionState> getSessionState() {
        return sessionState;
    }

    @Nullable
    public ChatCoordinator getCoordinator() {
        return coordinator;
    }

    @Nullable
    public AgentExecutorAiDelegate getAiDelegate() {
        return aiDelegate;
    }

    /** Called by Android when the Activity is permanently finished (not on rotation). */
    @Override
    protected void onCleared() {
        super.onCleared();
        if (coordinator != null) {
            coordinator.destroy();
            coordinator = null;
        }
        if (aiDelegate != null) {
            aiDelegate.shutdown();
            aiDelegate = null;
        }
    }
}
