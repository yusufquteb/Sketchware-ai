// nikit overhaul — Task 1 — 2026-05
// AiProvider.java is intentionally untouched.
// All static model lists live here so AiProvider stays clean.
package pro.sketchware.ai.models;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Companion to {@link AiProvider} that holds curated, verified model ID lists.
 *
 * <p>Kept separate so {@code AiProvider.java} can be edited independently.
 */
public final class AiProviderModels {

    private AiProviderModels() {}

    // ── Static model lists (Task 1) ───────────────────────────────────────────

    /**
     * Returns a curated list of verified model IDs for the given provider.
     * These are pre-vetted IDs that are known to work — no network call needed.
     */
    public static List<String> getStaticModels(AiProvider provider) {
        if (provider == null) return Collections.emptyList();
        switch (provider) {
            case GROQ:             return Arrays.asList("llama-3.3-70b-versatile","llama-3.1-8b-instant","compound-beta","compound-beta-mini","gemma2-9b-it","mixtral-8x7b-32768");
            case OPENAI:           return Arrays.asList("gpt-4o","gpt-4o-mini","gpt-4-turbo","o1","o1-mini","o3-mini");
            case ANTHROPIC:        return Arrays.asList("claude-opus-4-5","claude-sonnet-4-5","claude-haiku-4-5","claude-3-5-sonnet-20241022","claude-3-5-haiku-20241022");
            case GEMINI:           return Arrays.asList("gemini-2.0-flash","gemini-2.0-flash-lite","gemini-1.5-pro","gemini-1.5-flash");
            case DEEPSEEK:         return Arrays.asList("deepseek-chat","deepseek-reasoner");
            case XAI_GROK:         return Arrays.asList("grok-3","grok-3-mini","grok-2-1212");
            case NVIDIA:           return Arrays.asList("meta/llama-3.3-70b-instruct","nvidia/llama-3.1-nemotron-ultra-253b-v1","qwen/qwen3-235b-a22b","mistralai/mistral-large-2-instruct","google/gemma-3-27b-it","microsoft/phi-4");
            case OPENROUTER:       return Arrays.asList("openai/gpt-4o","anthropic/claude-3.5-sonnet","google/gemini-2.0-flash-exp:free","meta-llama/llama-3.3-70b-instruct:free","deepseek/deepseek-chat-v3-0324:free");
            case DEEPINFRA:        return Arrays.asList("meta-llama/Llama-3.3-70B-Instruct-Turbo","google/gemma-3-27b-it","Qwen/Qwen3-235B-A22B","deepseek-ai/DeepSeek-R1-Turbo","microsoft/phi-4");
            case TOGETHER:         return Arrays.asList("meta-llama/Llama-3.3-70B-Instruct-Turbo","google/gemma-2-27b-it","Qwen/Qwen2.5-72B-Instruct-Turbo","deepseek-ai/DeepSeek-R1");
            case HUGGINGFACE:      return Arrays.asList("Qwen/Qwen2.5-72B-Instruct","meta-llama/Llama-3.3-70B-Instruct","google/gemma-2-27b-it","mistralai/Mixtral-8x7B-Instruct-v0.1");
            case CEREBRAS:         return Arrays.asList("llama-3.3-70b","llama-3.1-8b","qwen-3-32b");
            case GOOGLE_AI_STUDIO: return Arrays.asList("gemini-2.0-flash","gemini-2.0-flash-lite","gemma-3-27b-it","gemma-3-12b-it");
            case SAMBANOVA:        return Arrays.asList("Meta-Llama-3.3-70B-Instruct","Llama-4-Scout-17B-16E-Instruct","DeepSeek-R1-Distill-Llama-70B","Qwen2.5-72B-Instruct");
            case MORPH:            return Arrays.asList("morph-v3-fast", "morph-v3");

            // ── New providers added in v7 ─────────────────────────────────────
            case KLUSTER:          return Arrays.asList(
                    "klusterai/Meta-Llama-3.3-70B-Instruct-Turbo",
                    "klusterai/Meta-Llama-3.1-8B-Instruct-Turbo",
                    "klusterai/Meta-Llama-3.1-405B-Instruct-Turbo",
                    "mistralai/Mixtral-8x7B-Instruct-v0.1",
                    "Qwen/Qwen2.5-72B-Instruct");

            case CLOUDFLARE:       return Arrays.asList(
                    "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
                    "@cf/meta/llama-3.1-8b-instruct",
                    "@cf/meta/llama-3.1-70b-instruct",
                    "@cf/google/gemma-3-12b-it",
                    "@cf/mistral/mistral-7b-instruct-v0.1",
                    "@cf/deepseek-ai/deepseek-r1-distill-llama-70b",
                    "@cf/qwen/qwen2.5-coder-32b-instruct");

            case GITHUB_MODELS:    return Arrays.asList(
                    "openai/gpt-4o",
                    "openai/gpt-4o-mini",
                    "openai/o3-mini",
                    "openai/o1",
                    "meta/llama-3.3-70b-instruct",
                    "meta/llama-4-scout",
                    "mistral-ai/mistral-large-2411",
                    "microsoft/phi-4",
                    "deepseek/deepseek-v3");

            case FIREWORKS:        return Arrays.asList(
                    "accounts/fireworks/models/llama-v3p3-70b-instruct",
                    "accounts/fireworks/models/llama-v3p1-8b-instruct",
                    "accounts/fireworks/models/llama4-scout-instruct-basic",
                    "accounts/fireworks/models/llama4-maverick-instruct-basic",
                    "accounts/fireworks/models/deepseek-v3",
                    "accounts/fireworks/models/deepseek-r1",
                    "accounts/fireworks/models/qwen2p5-72b-instruct",
                    "accounts/fireworks/models/mixtral-8x7b-instruct");

            case NOVITA:           return Arrays.asList(
                    "meta-llama/llama-3.3-70b-instruct",
                    "meta-llama/llama-3.1-8b-instruct",
                    "meta-llama/llama-4-scout",
                    "meta-llama/llama-4-maverick",
                    "deepseek/deepseek-v3",
                    "deepseek/deepseek-r1",
                    "google/gemma-3-27b-it",
                    "qwen/qwen2.5-72b-instruct",
                    "mistralai/mistral-7b-instruct");

            case CHUTES:           return Arrays.asList(
                    "gpt-4o",
                    "gpt-4o-mini",
                    "claude-3-5-sonnet",
                    "gemini-1-5-flash",
                    "meta-llama/llama-4-maverick",
                    "meta-llama/llama-4-scout",
                    "deepseek-v3",
                    "deepseek-r1",
                    "Qwen/Qwen3-235B-A22B",
                    "mistral-7b-instruct");

            default:               return Collections.emptyList();
        }
    }

    /**
     * Returns the first model in the static list, or empty string if none.
     */
    public static String getDefaultModel(AiProvider provider) {
        List<String> models = getStaticModels(provider);
        return models.isEmpty() ? "" : models.get(0);
    }

    /**
     * Returns true if {@code modelId} is a known valid model for this provider.
     */
    public static boolean isModelValidForProvider(AiProvider provider, String modelId) {
        if (provider == null || modelId == null || modelId.isEmpty()) return false;
        return getStaticModels(provider).contains(modelId);
    }
}
