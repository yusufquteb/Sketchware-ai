package pro.sketchware.ai.api;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.storage.AiPreferences;

/**
 * Synchronous bridge used by Block Manager AI features (BlocksManager,
 * BlocksManagerDetailsActivity) that need a blocking sendMessage() call.
 *
 * <p>Wraps Pro's async {@link GroqApiClient} using a {@link CountDownLatch}
 * and reads credentials from {@link AiPreferences} — no separate ia_settings
 * SharedPreferences required.
 *
 * <p>Usage (on a background thread):
 * <pre>
 *     GroqApiClientHelper helper = GroqApiClientHelper.getInstance(context);
 *     if (!helper.isConfigured()) { ... show settings ... }
 *     String response = helper.sendMessage("Organise these blocks: ...");
 * </pre>
 */
public final class GroqApiClientHelper {

    private static final String TAG = "GroqApiClientHelper";
    /** Timeout for synchronous requests (seconds). */
    private static final int TIMEOUT_SECONDS = 60;

    private final AiPreferences prefs;
    private final Context context;

    private GroqApiClientHelper(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = AiPreferences.getInstance(this.context);
    }

    /** Returns a new instance bound to the given context's AiPreferences. */
    public static GroqApiClientHelper getInstance(Context context) {
        return new GroqApiClientHelper(context);
    }

    /** Returns true if a Groq API key is configured and non-empty. */
    public boolean isConfigured() {
        String key = prefs.getApiKey(AiProvider.GROQ);
        return key != null && !key.isEmpty();
    }

    /**
     * Sends {@code userMessage} to Groq synchronously and returns the full
     * response text. Must be called from a background thread.
     *
     * @throws IllegalStateException if called from the main thread.
     * @return Response text, or null on timeout/error.
     */
    public String sendMessage(String userMessage) {
        return sendMessage(userMessage, null);
    }

    /**
     * Sends {@code userMessage} with an optional system prompt synchronously.
     *
     * @param userMessage The user's request.
     * @param systemPrompt Optional system instructions (null = use default).
     * @return Response text, or null on timeout/error.
     */
    public String sendMessage(String userMessage, String systemPrompt) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            throw new IllegalStateException("sendMessage() must be called from a background thread.");
        }

        String apiKey = prefs.getApiKey(AiProvider.GROQ);
        if (apiKey == null || apiKey.isEmpty()) {
            Log.w(TAG, "Groq API key not configured");
            return null;
        }

        String modelId = prefs.getSelectedModel(AiProvider.GROQ);
        if (modelId == null || modelId.isEmpty()) {
            modelId = "llama-3.3-70b-versatile";
        }

        GroqApiClient client = new GroqApiClient(apiKey);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("user", userMessage));

        final String[] result = {null};
        final StringBuilder buffer = new StringBuilder();
        final CountDownLatch latch = new CountDownLatch(1);

        final String finalModelId = modelId;
        final String finalSystem = systemPrompt != null ? systemPrompt : getDefaultSystemPrompt();

        client.sendChatRequest(messages, finalModelId, finalSystem,
                new StreamingResponseHandler() {
                    @Override
                    public void onChunk(String textDelta) {
                        buffer.append(textDelta);
                    }

                    @Override
                    public void onToolCall(pro.sketchware.ai.models.ToolCall toolCall) {
                        // Not used for block manager AI features
                    }

                    @Override
                    public void onComplete(String fullResponse) {
                        result[0] = fullResponse.isEmpty() ? buffer.toString() : fullResponse;
                        latch.countDown();
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Groq request error: " + error);
                        latch.countDown();
                    }
                });

        try {
            boolean completed = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                Log.e(TAG, "Groq request timed out after " + TIMEOUT_SECONDS + "s");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        return result[0];
    }

    private static String getDefaultSystemPrompt() {
        return "You are an expert Android developer assistant helping with Sketchware Pro block management. "
                + "Be concise, precise, and respond only with what was asked.";
    }
}
