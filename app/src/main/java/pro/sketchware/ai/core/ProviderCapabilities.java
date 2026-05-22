package pro.sketchware.ai.core;

import androidx.annotation.NonNull;

import pro.sketchware.ai.models.AiProvider;

/**
 * Per-provider capability metadata.
 *
 * <p>Allows the system to dynamically adapt behavior based on what a provider
 * actually supports, rather than assuming all providers are equivalent.
 *
 * <p>Usage:
 * <pre>
 * ProviderCapabilities caps = ProviderCapabilities.of(AiProvider.GROQ);
 * if (caps.supportsTools) { ... }
 * </pre>
 */
public final class ProviderCapabilities {

    public final boolean supportsStreaming;
    public final boolean supportsTools;
    public final boolean supportsVision;
    public final boolean supportsJsonMode;
    public final boolean supportsReasoning;
    public final boolean supportsSystemPrompts;
    public final boolean supportsTemperature;
    /** Maximum context window in tokens. 0 = unknown. */
    public final int     maxContextTokens;
    /** Maximum output tokens. 0 = unknown. */
    public final int     maxOutputTokens;
    /** Approximate requests per minute on the free tier. -1 = unlimited/unknown. */
    public final int     freeRpmLimit;

    private ProviderCapabilities(Builder b) {
        this.supportsStreaming     = b.supportsStreaming;
        this.supportsTools         = b.supportsTools;
        this.supportsVision        = b.supportsVision;
        this.supportsJsonMode      = b.supportsJsonMode;
        this.supportsReasoning     = b.supportsReasoning;
        this.supportsSystemPrompts = b.supportsSystemPrompts;
        this.supportsTemperature   = b.supportsTemperature;
        this.maxContextTokens      = b.maxContextTokens;
        this.maxOutputTokens       = b.maxOutputTokens;
        this.freeRpmLimit          = b.freeRpmLimit;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Returns the known capabilities for the given provider.
     * Falls back to conservative defaults for unknown providers.
     */
    @NonNull
    public static ProviderCapabilities of(@NonNull AiProvider provider) {
        switch (provider) {
            case OPENAI:
                return new Builder()
                        .streaming(true).tools(true).vision(true)
                        .jsonMode(true).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(128_000).maxOutput(16_384).freeRpm(-1).build();

            case ANTHROPIC:
                return new Builder()
                        .streaming(true).tools(true).vision(true)
                        .jsonMode(false).reasoning(true).systemPrompts(true).temperature(true)
                        .maxContext(200_000).maxOutput(8_192).freeRpm(-1).build();

            case GEMINI:
            case GOOGLE_AI_STUDIO:
                return new Builder()
                        .streaming(true).tools(true).vision(true)
                        .jsonMode(true).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(1_000_000).maxOutput(8_192).freeRpm(15).build();

            case GROQ:
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(true).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(131_072).maxOutput(8_192).freeRpm(30).build();

            case DEEPSEEK:
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(true).reasoning(true).systemPrompts(true).temperature(true)
                        .maxContext(64_000).maxOutput(8_192).freeRpm(-1).build();

            case CEREBRAS:
                return new Builder()
                        .streaming(true).tools(false).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(8_192).maxOutput(8_192).freeRpm(30).build();

            case SAMBANOVA:
                return new Builder()
                        .streaming(true).tools(false).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(32_768).maxOutput(4_096).freeRpm(30).build();

            case CHUTES:
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(128_000).maxOutput(8_192).freeRpm(60).build();

            case MISTRAL:
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(true).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(32_768).maxOutput(8_192).freeRpm(20).build();

            case COHERE:
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(128_000).maxOutput(4_096).freeRpm(20).build();

            case XAI_GROK:
                return new Builder()
                        .streaming(true).tools(true).vision(true)
                        .jsonMode(true).reasoning(true).systemPrompts(true).temperature(true)
                        .maxContext(131_072).maxOutput(8_192).freeRpm(-1).build();

            case OPENROUTER:
                return new Builder()
                        .streaming(true).tools(true).vision(true)
                        .jsonMode(true).reasoning(true).systemPrompts(true).temperature(true)
                        .maxContext(200_000).maxOutput(8_192).freeRpm(-1).build();

            case MORPH:
                return new Builder()
                        .streaming(true).tools(false).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(false)
                        .maxContext(32_768).maxOutput(4_096).freeRpm(-1).build();

            default:
                return new Builder()
                        .streaming(true).tools(false).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(32_768).maxOutput(4_096).freeRpm(-1).build();
        }
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private static final class Builder {
        boolean supportsStreaming     = true;
        boolean supportsTools         = false;
        boolean supportsVision        = false;
        boolean supportsJsonMode      = false;
        boolean supportsReasoning     = false;
        boolean supportsSystemPrompts = true;
        boolean supportsTemperature   = true;
        int     maxContextTokens      = 32_768;
        int     maxOutputTokens       = 4_096;
        int     freeRpmLimit          = -1;

        Builder streaming(boolean v)     { supportsStreaming = v;     return this; }
        Builder tools(boolean v)         { supportsTools = v;         return this; }
        Builder vision(boolean v)        { supportsVision = v;        return this; }
        Builder jsonMode(boolean v)      { supportsJsonMode = v;      return this; }
        Builder reasoning(boolean v)     { supportsReasoning = v;     return this; }
        Builder systemPrompts(boolean v) { supportsSystemPrompts = v; return this; }
        Builder temperature(boolean v)   { supportsTemperature = v;   return this; }
        Builder maxContext(int v)        { maxContextTokens = v;      return this; }
        Builder maxOutput(int v)         { maxOutputTokens = v;       return this; }
        Builder freeRpm(int v)           { freeRpmLimit = v;          return this; }

        ProviderCapabilities build() { return new ProviderCapabilities(this); }
    }

    @Override
    @NonNull
    public String toString() {
        return "ProviderCapabilities{"
                + "streaming=" + supportsStreaming
                + ", tools=" + supportsTools
                + ", vision=" + supportsVision
                + ", json=" + supportsJsonMode
                + ", ctx=" + maxContextTokens + "t"
                + "}";
    }
}
