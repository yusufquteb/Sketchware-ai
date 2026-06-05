package pro.sketchware.ai.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import pro.sketchware.ai.models.AiProvider;

/**
 * Per-model capability overrides on top of {@link ProviderCapabilities}.
 *
 * <p>Some providers host heterogeneous model families where individual models have
 * different capabilities than the provider default. For example, OpenRouter hosts both
 * reasoning models (which support tool calling) and base language models (which do not).
 * This class provides model-level overrides so routing and enforcement decisions are
 * accurate at the individual model level.
 *
 * <p>Usage:
 * <pre>
 *   ProviderCapabilities base = ProviderCapabilities.of(AiProvider.OPENROUTER);
 *   ProviderCapabilities effective = ModelCapabilities.resolve(AiProvider.OPENROUTER, modelId, base);
 *   if (effective.supportsTools) { ... }
 * </pre>
 */
public final class ModelCapabilities {

    private ModelCapabilities() {}

    /**
     * Returns effective capabilities for a specific model, merging provider defaults
     * with any known model-level overrides.
     *
     * <p>When no override exists for {@code modelId}, {@code providerDefault} is returned as-is.
     */
    @NonNull
    public static ProviderCapabilities resolve(
            @NonNull AiProvider provider,
            @Nullable String modelId,
            @NonNull ProviderCapabilities providerDefault) {

        if (modelId == null || modelId.isEmpty()) return providerDefault;
        String m = modelId.toLowerCase();

        switch (provider) {

            // ── OpenRouter: heterogeneous model zoo ───────────────────────────
            case OPENROUTER:
                return resolveOpenRouter(m, providerDefault);

            // ── Groq: not all hosted models support tool calling ──────────────
            case GROQ:
                return resolveGroq(m, providerDefault);

            // ── HuggingFace: base models rarely support tool calling ──────────
            case HUGGINGFACE:
                return resolveHuggingFace(m, providerDefault);

            // ── LLM7: proxy — capabilities depend on the underlying model ─────
            case LLM7:
                return resolveLlm7(m, providerDefault);

            // ── Pollinations: proxy — most models are plain chat ──────────────
            case POLLINATIONS:
                return resolvePollinations(m, providerDefault);

            // ── DeepInfra: hosting varied open-weight models ──────────────────
            case DEEPINFRA:
                return resolveDeepInfra(m, providerDefault);

            // All other providers: provider-level capabilities are accurate
            default:
                return providerDefault;
        }
    }

    // ── Provider-specific resolvers ───────────────────────────────────────────

    private static ProviderCapabilities resolveOpenRouter(
            String m, ProviderCapabilities base) {
        // Reasoning/tool models confirmed to support tool calling
        boolean hasTools = m.contains("claude") || m.contains("gpt-4")
                || m.contains("gemini") || m.contains("deepseek-v3")
                || m.contains("qwen") || m.contains("mistral")
                || m.contains("llama-3") || m.contains("tool");

        // Vision models
        boolean hasVision = m.contains("vision") || m.contains("claude-3")
                || m.contains("gpt-4o") || m.contains("gemini")
                || m.contains("llava") || m.contains("pixtral");

        // Base/completion models that don't support structured calling
        boolean noTools = m.contains("base") || m.contains("instruct-lite")
                || m.contains("completion");
        if (noTools) hasTools = false;

        if (hasTools == base.supportsTools && hasVision == base.supportsVision) {
            return base;
        }
        return new ProviderCapabilities.Builder()
                .from(base)
                .tools(hasTools)
                .vision(hasVision)
                .build();
    }

    private static ProviderCapabilities resolveGroq(
            String m, ProviderCapabilities base) {
        // Groq confirmed tool-calling models (as of mid-2026)
        boolean hasTools = m.contains("llama-3") || m.contains("mixtral")
                || m.contains("gemma") || m.contains("tool");
        // Whisper and guard models on Groq: no tool calling
        boolean noTools = m.contains("whisper") || m.contains("guard");
        if (noTools) hasTools = false;

        if (hasTools == base.supportsTools) return base;
        return new ProviderCapabilities.Builder().from(base).tools(hasTools).build();
    }

    private static ProviderCapabilities resolveHuggingFace(
            String m, ProviderCapabilities base) {
        // HuggingFace Inference API: only chat-template-capable instruct models support tools
        boolean hasTools = m.contains("instruct") || m.contains("chat") || m.contains("tool");
        if (hasTools == base.supportsTools) return base;
        return new ProviderCapabilities.Builder().from(base).tools(hasTools).build();
    }

    private static ProviderCapabilities resolveLlm7(
            String m, ProviderCapabilities base) {
        // LLM7 proxies: capabilities follow the underlying model family
        boolean hasTools = m.contains("qwen") || m.contains("gpt") || m.contains("gemini")
                || m.contains("deepseek") || m.contains("mistral") || m.contains("llama");
        if (hasTools == base.supportsTools) return base;
        return new ProviderCapabilities.Builder().from(base).tools(hasTools).build();
    }

    private static ProviderCapabilities resolvePollinations(
            String m, ProviderCapabilities base) {
        // Pollinations proxy: "openai", "mistral" routed models support tools
        boolean hasTools = m.equals("openai") || m.equals("mistral")
                || m.contains("gpt") || m.contains("claude");
        if (hasTools == base.supportsTools) return base;
        return new ProviderCapabilities.Builder().from(base).tools(hasTools).build();
    }

    private static ProviderCapabilities resolveDeepInfra(
            String m, ProviderCapabilities base) {
        boolean hasTools = m.contains("instruct") || m.contains("llama-3")
                || m.contains("qwen") || m.contains("mistral") || m.contains("tool");
        boolean noTools = m.contains("base") || m.contains("completion");
        if (noTools) hasTools = false;
        if (hasTools == base.supportsTools) return base;
        return new ProviderCapabilities.Builder().from(base).tools(hasTools).build();
    }
}
