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
            // ── Group 1: Free, no API ──────────────────────────────────────────
            // gpt-4o and gpt-4o-mini removed: AirForce AI returns "Invalid API Key".
            // llama-4-maverick and llama-4-scout both return "Invalid API Key" — removed.
            // claude-3-7-sonnet added (newer than claude-3-5-sonnet via this proxy).
            case CHUTES:           return Arrays.asList(
                    "deepseek-v3",
                    "claude-3-7-sonnet",
                    "claude-3-5-sonnet",
                    "gemini-2.0-flash",
                    "Qwen/Qwen3-235B-A22B",
                    "mistral-small-latest");

            // ── Group 2: Free with API ─────────────────────────────────────────
            // gemma-3-*-it models removed: all returned "Model Not Found".
            // gemini-2.5-flash is the best free model in 2026 (1M ctx, multimodal).
            // gemini-2.5-pro added: available on free tier with lower quota.
            case GOOGLE_AI_STUDIO: return Arrays.asList(
                    "gemini-2.5-flash",
                    "gemini-2.5-pro",
                    "gemini-2.0-flash",
                    "gemini-2.0-flash-lite");

            // Only Meta-Llama-3.3-70B-Instruct confirmed working.
            // The other three returned "Request Error" consistently.
            case SAMBANOVA:        return Arrays.asList(
                    "Meta-Llama-3.3-70B-Instruct");

            case CEREBRAS:         return Arrays.asList(
                    "llama-3.3-70b",
                    "llama-3.1-8b",
                    "qwen-3-32b");

            // Removed: gemma2-9b-it (Bad Request), deepseek-r1-distill-llama-70b (Bad Request),
            // llama-4-scout-17b-16e-preview (Model Not Found), mixtral-8x7b-32768 (Bad Request).
            // Added qwen3-32b (Qwen3 latest) and compound-beta (Groq tool-calling agent with web search).
            case GROQ:             return Arrays.asList(
                    "llama-3.3-70b-versatile",
                    "qwen-qwq-32b",
                    "qwen3-32b",
                    "compound-beta",
                    "llama-3.1-8b-instant");

            case HUGGINGFACE:      return Arrays.asList(
                    "Qwen/Qwen2.5-72B-Instruct",
                    "meta-llama/Llama-3.3-70B-Instruct",
                    "google/gemma-2-27b-it",
                    "mistralai/Mistral-7B-Instruct-v0.3");

            case MISTRAL:          return Arrays.asList(
                    "mistral-small-latest",
                    "open-mistral-nemo",
                    "codestral-latest",
                    "devstral-small-latest",
                    "mistral-large-latest");

            case COHERE:           return Arrays.asList(
                    "command-r-plus",
                    "command-r",
                    "command-light");

            case GITHUB_MODELS:    return Arrays.asList(
                    "openai/gpt-4o",
                    "openai/gpt-4o-mini",
                    "meta/llama-3.3-70b-instruct",
                    "meta/llama-4-scout",
                    "mistral-ai/mistral-large-2411",
                    "microsoft/phi-4");

            case SCALEWAY:         return Arrays.asList(
                    "llama-3.3-70b-instruct",
                    "llama-3.1-8b-instruct",
                    "mistral-nemo-instruct-2407",
                    "qwen2.5-72b-instruct");

            case CLOUDFLARE:       return Arrays.asList(
                    "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
                    "@cf/meta/llama-3.1-8b-instruct",
                    "@cf/google/gemma-3-12b-it",
                    "@cf/deepseek-ai/deepseek-r1-distill-llama-70b",
                    "@cf/qwen/qwen2.5-coder-32b-instruct");

            // ── Group 3: Paid ──────────────────────────────────────────────────
            case OPENAI:           return Arrays.asList(
                    "gpt-4o",
                    "gpt-4o-mini",
                    "o4-mini",
                    "o3",
                    "o3-mini");

            // claude-opus-4-8 / claude-sonnet-4-6 are the current Claude 4 family (2026).
            case ANTHROPIC:        return Arrays.asList(
                    "claude-opus-4-8",
                    "claude-sonnet-4-6",
                    "claude-haiku-4-5-20251001",
                    "claude-opus-4-7",
                    "claude-opus-4-5",
                    "claude-sonnet-4-5",
                    "claude-3-5-sonnet-20241022");

            // gemini-1.5 series retired. gemini-2.5-pro is the best paid Gemini model.
            case GEMINI:           return Arrays.asList(
                    "gemini-2.5-pro",
                    "gemini-2.5-flash",
                    "gemini-2.0-flash",
                    "gemini-2.0-flash-lite");

            case DEEPSEEK:         return Arrays.asList(
                    "deepseek-chat",
                    "deepseek-reasoner");

            case XAI_GROK:         return Arrays.asList(
                    "grok-3",
                    "grok-3-mini");

            // nemotron-ultra-253b, gemma-3-27b, phi-4, and nvidia/llama-3.1-nemotron-70b-instruct
            // all failed (Model Not Found / Request Error). Only meta/llama-3.3-70b-instruct confirmed.
            case NVIDIA:           return Arrays.asList(
                    "meta/llama-3.3-70b-instruct");

            // Free models use the :free suffix (OpenRouter zero-cost tier).
            // deepseek/deepseek-r1:free and qwen/qwen3-235b-a22b:free failed in prior diagnostics.
            // openrouter/free is a special auto-router that picks the best available free model
            // automatically — the most reliable option when specific models go down.
            // qwen/qwq-32b:free (reasoning model) and qwen/qwen3-coder:free (coding specialist)
            // added per May 2026 OpenRouter free tier listings.
            case OPENROUTER:       return Arrays.asList(
                    "openrouter/free",
                    "meta-llama/llama-3.3-70b-instruct:free",
                    "qwen/qwq-32b:free",
                    "qwen/qwen3-coder:free",
                    "google/gemma-2-9b-it:free",
                    "mistralai/mistral-7b-instruct:free",
                    "qwen/qwen-2.5-72b-instruct:free",
                    "deepseek/deepseek-r1-distill-llama-70b:free",
                    "openai/gpt-4o");

            case DEEPINFRA:        return Arrays.asList(
                    "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                    "google/gemma-3-27b-it",
                    "Qwen/Qwen3-235B-A22B",
                    "microsoft/phi-4");

            case TOGETHER:         return Arrays.asList(
                    "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                    "google/gemma-2-27b-it",
                    "Qwen/Qwen2.5-72B-Instruct-Turbo",
                    "deepseek-ai/DeepSeek-R1");

            case HYPERBOLIC:       return Arrays.asList(
                    "meta-llama/Llama-3.3-70B-Instruct",
                    "deepseek-ai/DeepSeek-R1",
                    "Qwen/Qwen2.5-72B-Instruct");

            case KLUSTER:          return Arrays.asList(
                    "klusterai/Meta-Llama-3.3-70B-Instruct-Turbo",
                    "klusterai/Meta-Llama-3.1-8B-Instruct-Turbo",
                    "Qwen/Qwen2.5-72B-Instruct");

            case OVH:              return Arrays.asList(
                    "Llama-3.3-70B-Instruct",
                    "Mistral-7B-Instruct-v0.3",
                    "Qwen2.5-72B-Instruct");

            case LAMBDA:           return Arrays.asList(
                    "llama3.3-70b-instruct-fp8",
                    "llama3.1-8b-instruct",
                    "qwen25-coder-32b-instruct");

            case FIREWORKS:        return Arrays.asList(
                    "accounts/fireworks/models/llama-v3p3-70b-instruct",
                    "accounts/fireworks/models/llama4-scout-instruct-basic",
                    "accounts/fireworks/models/deepseek-v3",
                    "accounts/fireworks/models/qwen2p5-72b-instruct");

            case NOVITA:           return Arrays.asList(
                    "meta-llama/llama-3.3-70b-instruct",
                    "meta-llama/llama-4-scout",
                    "deepseek/deepseek-v3",
                    "google/gemma-3-27b-it");

            case MORPH:            return Arrays.asList("morph-v3-fast", "morph-v3");

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
