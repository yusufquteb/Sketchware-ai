package pro.sketchware.ai.api;

import pro.sketchware.ai.models.ToolCall;

/**
 * Callback interface for handling streaming responses from AI API providers.
 * Implementations receive incremental text chunks, tool call invocations,
 * the completed full response, and error notifications.
 */
public interface StreamingResponseHandler {

    /**
     * Called when a new text chunk is received from the stream.
     *
     * @param textDelta the incremental text fragment
     */
    void onChunk(String textDelta);

    /**
     * Called when the model invokes a tool/function call.
     *
     * @param toolCall the parsed tool call with function name and arguments
     */
    void onToolCall(ToolCall toolCall);

    /**
     * Called when the stream has finished successfully.
     *
     * @param fullResponse the complete concatenated response text
     */
    void onComplete(String fullResponse);

    /**
     * Called when an error occurs during streaming.
     *
     * @param error a human-readable error description
     */
    void onError(String error);
}
