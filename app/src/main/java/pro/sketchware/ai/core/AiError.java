package pro.sketchware.ai.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Unified AI error taxonomy.
 *
 * <p>Replaces scattered error strings with a typed, actionable error model.
 * Every error surface in the system should use this class.
 */
public final class AiError {

    public enum Category {
        /** No/bad internet connection or DNS failure. */
        NETWORK,
        /** 401 / invalid API key. */
        AUTH,
        /** 429 / too many requests / quota exceeded. */
        RATE_LIMIT,
        /** Request timed out waiting for first token or stream completion. */
        TIMEOUT,
        /** Provider returned 5xx or is temporarily unavailable. */
        PROVIDER_DOWN,
        /** The selected model does not exist or is not accessible. */
        MODEL_NOT_FOUND,
        /** Malformed JSON in the AI response (parse failure). */
        INVALID_RESPONSE,
        /** A tool threw an exception or returned unexpected output. */
        TOOL_FAILURE,
        /** Device is running low on memory. */
        MEMORY_PRESSURE,
        /** All providers in the fallback chain have been exhausted. */
        ALL_PROVIDERS_FAILED,
        /** User explicitly cancelled the request. */
        CANCELLED,
        /** Internal bug or unexpected condition. */
        INTERNAL;

        /** Returns true if this category is worth retrying automatically. */
        public boolean isRetryable() {
            return this == NETWORK || this == TIMEOUT || this == PROVIDER_DOWN;
        }

        /** Returns true if this requires user action (e.g. settings change). */
        public boolean requiresUserAction() {
            return this == AUTH || this == RATE_LIMIT || this == MODEL_NOT_FOUND;
        }
    }

    @NonNull  public final Category category;
    @NonNull  public final String   userMessage;
    @Nullable public final String   technicalDetail;
    @Nullable public final String   providerName;
    @Nullable public final String   actionHint;

    private AiError(
            @NonNull  Category category,
            @NonNull  String   userMessage,
            @Nullable String   technicalDetail,
            @Nullable String   providerName,
            @Nullable String   actionHint
    ) {
        this.category        = category;
        this.userMessage     = userMessage;
        this.technicalDetail = technicalDetail;
        this.providerName    = providerName;
        this.actionHint      = actionHint;
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    public static AiError network(@Nullable String detail) {
        return new AiError(Category.NETWORK, "Network error. Check your connection.",
                detail, null, "Check your internet connection and try again.");
    }

    public static AiError auth(@Nullable String provider) {
        return new AiError(Category.AUTH, "Invalid API key.",
                null, provider, "Go to AI Settings and update your API key for "
                        + (provider != null ? provider : "this provider") + ".");
    }

    public static AiError rateLimit(@Nullable String provider) {
        return new AiError(Category.RATE_LIMIT, "Rate limit reached.",
                null, provider, "Switch to Groq ∞ or Cerebras (free) — tap the model chip.");
    }

    public static AiError timeout(@Nullable String provider, long timeoutMs) {
        return new AiError(Category.TIMEOUT,
                "Request timed out after " + (timeoutMs / 1000) + "s.",
                null, provider, "Try a faster provider like Groq or Cerebras.");
    }

    public static AiError providerDown(@Nullable String provider, @Nullable String detail) {
        return new AiError(Category.PROVIDER_DOWN, "Provider temporarily unavailable.",
                detail, provider, "Try again in a moment or switch providers.");
    }

    public static AiError modelNotFound(@Nullable String modelId, @Nullable String provider) {
        return new AiError(Category.MODEL_NOT_FOUND,
                "Model not found: " + (modelId != null ? modelId : "unknown"),
                null, provider, "Refresh models in AI Settings or choose a different model.");
    }

    public static AiError invalidResponse(@Nullable String detail) {
        return new AiError(Category.INVALID_RESPONSE, "AI returned an unexpected response.",
                detail, null, "Try again. If the problem persists, switch models.");
    }

    public static AiError toolFailure(@NonNull String toolName, @Nullable String detail) {
        return new AiError(Category.TOOL_FAILURE, "Tool \"" + toolName + "\" failed.",
                detail, null, null);
    }

    public static AiError memoryPressure() {
        return new AiError(Category.MEMORY_PRESSURE, "Low memory — AI paused.",
                null, null, "Close other apps and try again.");
    }

    public static AiError allProvidersFailed(@Nullable String lastDetail) {
        return new AiError(Category.ALL_PROVIDERS_FAILED,
                "All configured providers failed.",
                lastDetail, null,
                "Add more providers in AI Settings or check your network.");
    }

    public static AiError cancelled() {
        return new AiError(Category.CANCELLED, "Request cancelled.", null, null, null);
    }

    public static AiError internal(@Nullable String detail) {
        return new AiError(Category.INTERNAL, "Internal error.",
                detail, null, "Please try again.");
    }

    // ── Classification from raw error strings ────────────────────────────────

    /**
     * Classifies a raw error string into an {@link AiError}.
     * Used when errors arrive as strings from legacy API clients.
     */
    @NonNull
    public static AiError fromRawError(@Nullable String raw, @Nullable String providerName) {
        if (raw == null || raw.isEmpty()) return internal(null);

        String lower = raw.toLowerCase();

        if (lower.contains("cancel")) {
            return cancelled();
        }
        if (lower.contains("401") || lower.contains("invalid api key")
                || lower.contains("unauthorized") || lower.contains("invalid_api_key")) {
            return auth(providerName);
        }
        if (lower.contains("429") || lower.contains("rate limit") || lower.contains("rate_limit")
                || lower.contains("quota") || lower.contains("too many requests")) {
            return rateLimit(providerName);
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return timeout(providerName, 0);
        }
        if (lower.contains("503") || lower.contains("service unavailable")
                || lower.contains("overloaded")) {
            return providerDown(providerName, raw);
        }
        if (lower.contains("404") || lower.contains("model not found")
                || lower.contains("no such model")) {
            return modelNotFound(null, providerName);
        }
        if (lower.contains("connect") || lower.contains("socket")
                || lower.contains("network") || lower.contains("i/o")
                || lower.contains("unreachable") || lower.contains("dns")
                || lower.contains("ssl") || lower.contains("eof")) {
            return network(raw);
        }
        if (lower.contains("parse") || lower.contains("json") || lower.contains("malformed")) {
            return invalidResponse(raw);
        }
        return internal(raw);
    }

    // ── Display helpers ───────────────────────────────────────────────────────

    /**
     * Returns a chat-displayable message combining the user message and action hint.
     * Includes the ⚠️ prefix to match the existing chat error style.
     */
    @NonNull
    public String toChatMessage() {
        StringBuilder sb = new StringBuilder("⚠️ ").append(userMessage);
        if (actionHint != null && !actionHint.isEmpty()) {
            sb.append("\n\n💡 ").append(actionHint);
        }
        return sb.toString();
    }

    @NonNull
    @Override
    public String toString() {
        return "AiError{" + category + ", " + userMessage
                + (providerName != null ? ", provider=" + providerName : "") + "}";
    }
}
