package pro.sketchware.ai.chat.state;

import androidx.annotation.Nullable;

import pro.sketchware.ai.models.AiProvider;

/** Immutable snapshot of the current chat session state. */
public final class ChatSessionState {

    public final boolean isAiRunning;
    @Nullable public final String currentModelId;
    @Nullable public final AiProvider currentProvider;

    public ChatSessionState(boolean isAiRunning,
                            @Nullable AiProvider currentProvider,
                            @Nullable String currentModelId) {
        this.isAiRunning     = isAiRunning;
        this.currentProvider = currentProvider;
        this.currentModelId  = currentModelId;
    }

    public ChatSessionState withAiRunning(boolean running) {
        return new ChatSessionState(running, currentProvider, currentModelId);
    }

    public ChatSessionState withModel(@Nullable AiProvider provider, @Nullable String modelId) {
        return new ChatSessionState(isAiRunning, provider, modelId);
    }
}
