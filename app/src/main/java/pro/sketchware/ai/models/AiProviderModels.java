package pro.sketchware.ai.models;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Curated, verified model ID lists for every AiProvider.
 *
 * Update policy:
 *  - Only add a model after confirming it responds without error (not just that it appears in
 *    a provider's documentation — providers often list models that aren't yet live).
 *  - Mark removed models with an inline comment explaining why they were removed.
 *  - Keep the list ordered: best/most capable first.
 *
 * Coding benchmark notes (2026, relevant for Sketchware Java/Android use):
 *  - Claude Opus 4.8   — #1 SWE-bench (80.8%), best for complex multi-file code
 *  - Claude Sonnet 4.6 — #2 SWE-bench (79.6%), best value paid model
 *  - DeepSeek V4 Flash — fastest free option with strong code output (1M ctx)
 *  - Qwen3 Coder       — best specialised free coding model (1M ctx)
 *  - GPT-OSS 20B       — OpenAI open-source, matches o3-mini on coding, free
 *  - Gemma 4 31B       — Google open model, tool-calling, 256K ctx, free
 *  - DeepSeek R1       — reasoning + code, free on OpenRouter
 */
public final class AiProviderModels {

    private AiProviderModels() {}

    public static List<String> getStaticModels(AiProvider provider) {
        if (provider == null) return Collections.emptyList();
        switch (provider) {

            // ══════════════════════════════════════════════════════════════════
            // GROUP 1 — Free, NO API key needed
            // ══════════════════════════════════════════════════════════════════

            // AirForce AI (api.airforce) — community proxy, no sign-up.
            // Works without any API key; best starting point for new users.
            // Removed: gpt-4o, gpt-4o-mini → "Invalid API Key" from this proxy.
            // Removed: llama-4-maverick, llama-4-scout → "Invalid API Key".
            // Claude access: claude-3-7-sonnet and claude-3-5-sonnet work via proxy.
            // DeepSeek access: deepseek-v3 works via proxy (free DeepSeek!).
            // Gemini access: gemini-2.0-flash works via proxy (free Gemini!).
            case CHUTES:
                return Arrays.asList(
                        "deepseek-v3",            // DeepSeek V3 — excellent Java/Android code
                        "claude-3-7-sonnet",      // Claude via proxy — free, strong at code
                        "claude-3-5-sonnet",      // Claude fallback
                        "gemini-2.0-flash",       // Gemini via proxy — free
                        "Qwen/Qwen3-235B-A22B",   // Largest free open model
                        "mistral-small-latest");

            // ══════════════════════════════════════════════════════════════════
            // GROUP 2 — Free WITH API key (generous free tiers)
            // ══════════════════════════════════════════════════════════════════

            // Google AI Studio — best free API in 2026.
            // Free tier (as of April 2026): gemini-2.5-flash = 1,500 req/day, 1M ctx.
            // gemini-2.5-pro = only 50 req/day on free tier → NOT practical, removed.
            // gemma-3-*-it removed: all returned "Model Not Found" via Studio API.
            // Gemma 4 access: gemma-4-0-preview-04-17 available via Gemini API.
            case GOOGLE_AI_STUDIO:
                return Arrays.asList(
                        "gemini-2.5-flash",               // Best free: 1,500 req/day, 1M ctx
                        "gemini-2.0-flash",               // Fallback: 500 req/day
                        "gemini-2.0-flash-lite",          // Lightweight: 1,500 req/day
                        "gemma-4-0-preview-04-17");       // Gemma 4 via Studio API

            // SambaNova — confirmed: Meta-Llama-3.3-70B-Instruct only.
            // Other models (Gemma 3, Llama 4) returned "Request Error".
            case SAMBANOVA:
                return Arrays.asList(
                        "Meta-Llama-3.3-70B-Instruct");

            // Cerebras — ultra-fast LPU inference, free tier.
            // qwen-3-32b confirmed working (strong at code).
            case CEREBRAS:
                return Arrays.asList(
                        "qwen-3-32b",      // Best for coding on Cerebras
                        "llama-3.3-70b",
                        "llama-3.1-8b");

            // Groq — blazing fast LPU inference, free tier.
            // qwen3-32b: Qwen3 latest generation, confirmed on Groq.
            // compound-beta: Groq Compound Agent — has built-in web search + tool execution.
            // Removed: gemma2-9b-it, deepseek-r1-distill-llama-70b → Bad Request.
            // Removed: mixtral-8x7b-32768 → Bad Request.
            // Removed: llama-4-scout-17b-16e-preview → Model Not Found.
            case GROQ:
                return Arrays.asList(
                        "qwen3-32b",               // Qwen3 latest — strong Java/Android code
                        "llama-3.3-70b-versatile", // Best general quality on Groq free
                        "qwen-qwq-32b",            // Reasoning model
                        "compound-beta",           // Agent with web search (agentic tasks)
                        "llama-3.1-8b-instant");   // Fastest, high rate limits

            // HuggingFace Inference API — free $0.10/month credit for models ≤10 GB.
            case HUGGINGFACE:
                return Arrays.asList(
                        "Qwen/Qwen2.5-72B-Instruct",
                        "meta-llama/Llama-3.3-70B-Instruct",
                        "google/gemma-2-27b-it",
                        "mistralai/Mistral-7B-Instruct-v0.3");

            // Mistral — free experimental plan includes Mistral Small and Codestral.
            // devstral-small-latest: specialised for coding/agentic tasks.
            case MISTRAL:
                return Arrays.asList(
                        "devstral-small-latest",   // Best for code, free experimental
                        "codestral-latest",         // Code specialist, free experimental
                        "mistral-small-latest",
                        "open-mistral-nemo",
                        "mistral-large-latest");

            // Cohere — 20 req/min, 1,000 req/month free tier.
            case COHERE:
                return Arrays.asList(
                        "command-r-plus",
                        "command-r",
                        "command-light");

            // GitHub Models — free with any GitHub account.
            // phi-4 and microsoft/phi-4-mini confirmed; phi-4-reasoning added (2026).
            case GITHUB_MODELS:
                return Arrays.asList(
                        "openai/gpt-4o",
                        "openai/gpt-4o-mini",
                        "meta/llama-3.3-70b-instruct",
                        "meta/llama-4-scout",
                        "mistral-ai/mistral-large-2411",
                        "microsoft/phi-4",
                        "microsoft/phi-4-mini-reasoning");

            // Scaleway — 1M free token trial credit, European provider.
            case SCALEWAY:
                return Arrays.asList(
                        "llama-3.3-70b-instruct",
                        "llama-3.1-8b-instruct",
                        "qwen2.5-72b-instruct",
                        "mistral-nemo-instruct-2407");

            // Cloudflare Workers AI — 10,000 neurons/day free.
            // UPDATED: gemma-4-26b-a4b-it added (official Cloudflare launch, April 2026).
            //   Gemma 4: MoE 26B/4B active, 256K ctx, tool-calling, vision, multilingual.
            //   Model page: developers.cloudflare.com/workers-ai/models/gemma-4-26b-a4b-it/
            // gemma-3-12b-it replaced by gemma-4-26b-a4b-it (same family, newer generation).
            case CLOUDFLARE:
                return Arrays.asList(
                        "@cf/google/gemma-4-26b-a4b-it",             // Gemma 4 — 256K ctx, tool-calling ✅
                        "@cf/meta/llama-3.3-70b-instruct-fp8-fast",  // Llama 3.3 70B — fast fp8
                        "@cf/deepseek-ai/deepseek-r1-distill-llama-70b", // DeepSeek R1 reasoning
                        "@cf/qwen/qwen2.5-coder-32b-instruct",       // Qwen Coder — code specialist
                        "@cf/meta/llama-3.1-8b-instruct");           // Lightweight fallback

            // ══════════════════════════════════════════════════════════════════
            // GROUP 3 — Paid providers
            // ══════════════════════════════════════════════════════════════════

            // OpenAI — o4-mini and o3 are the current reasoning models (2026).
            // o1-mini removed (deprecated by OpenAI).
            case OPENAI:
                return Arrays.asList(
                        "gpt-4o",
                        "o4-mini",     // Best value reasoning model
                        "o3",          // Top reasoning, expensive
                        "gpt-4o-mini",
                        "o3-mini");

            // Anthropic — Claude 4 family. Best overall for Java/Android code in 2026.
            // SWE-bench: claude-opus-4-8 → ~80.8%, claude-sonnet-4-6 → 79.6%.
            case ANTHROPIC:
                return Arrays.asList(
                        "claude-opus-4-8",          // #1 coding benchmark 2026
                        "claude-sonnet-4-6",         // Best value, strong at Java/Android
                        "claude-haiku-4-5-20251001", // Fastest, cheapest
                        "claude-opus-4-7",
                        "claude-opus-4-5",
                        "claude-sonnet-4-5",
                        "claude-3-5-sonnet-20241022");

            // Gemini paid API — gemini-2.5-pro leads 13/16 major benchmarks (Feb 2026).
            // 60% cheaper than Claude Opus with comparable coding performance.
            // gemini-1.5 series retired (all return "Model Not Found").
            case GEMINI:
                return Arrays.asList(
                        "gemini-2.5-pro",      // Tops coding benchmarks, 1M ctx
                        "gemini-2.5-flash",    // Best value paid Gemini
                        "gemini-2.0-flash",
                        "gemini-2.0-flash-lite");

            // DeepSeek — 5M free tokens on signup (no credit card).
            // deepseek-chat = DeepSeek V3; deepseek-reasoner = DeepSeek R1.
            // Outstanding value: V3 rivals GPT-4o at $0.27/M input tokens.
            case DEEPSEEK:
                return Arrays.asList(
                        "deepseek-chat",      // DeepSeek V3 — best value code model
                        "deepseek-reasoner"); // DeepSeek R1 — reasoning + coding

            // xAI Grok — real-time web access built in.
            case XAI_GROK:
                return Arrays.asList(
                        "grok-3",
                        "grok-3-mini");

            // NVIDIA NIM — enterprise GPU inference.
            // Most models failed (Model Not Found / Request Error).
            // Only meta/llama-3.3-70b-instruct confirmed working.
            case NVIDIA:
                return Arrays.asList(
                        "meta/llama-3.3-70b-instruct");

            // OpenRouter — aggregates 100+ models, including many free (:free suffix).
            //
            // FREE MODELS (confirmed May 2026, openrouter.ai/collections/free-models):
            //  • openrouter/free        — auto-router: always picks best available free model ✅
            //  • google/gemma-4-31b-it:free  — Gemma 4, 256K ctx, tool-calling ✅ openrouter.ai/google/gemma-4-31b-it:free
            //  • deepseek/deepseek-v4-flash:free — fast reasoning, 1M ctx ✅ openrouter.ai/deepseek/deepseek-v4-flash
            //  • deepseek/deepseek-r1:free  — reasoning model ✅ openrouter.ai/deepseek/deepseek-r1:free
            //  • openai/gpt-oss-20b:free    — OpenAI open-source, matches o3-mini ✅ openrouter.ai/openai/gpt-oss-20b:free
            //  • qwen/qwen3-coder:free      — best free coding specialist, 1M ctx
            //  • qwen/qwq-32b:free          — QwQ reasoning model
            //
            // Removed (previously failing): deepseek/deepseek-r1:free (re-added, now stable),
            //   qwen/qwen3-235b-a22b:free (still unreliable on free tier).
            // gemma-2-9b-it:free kept as lightweight fallback.
            case OPENROUTER:
                return Arrays.asList(
                        "openrouter/free",                          // Auto-picks best free model — most resilient ✅
                        "google/gemma-4-31b-it:free",               // Gemma 4 — 256K ctx, tool-calling ✅
                        "deepseek/deepseek-v4-flash:free",          // DeepSeek V4 — 1M ctx, fast reasoning ✅
                        "deepseek/deepseek-r1:free",                // DeepSeek R1 — reasoning ✅
                        "openai/gpt-oss-20b:free",                  // GPT-OSS 20B — matches o3-mini ✅
                        "qwen/qwen3-coder:free",                    // Best free coding model
                        "qwen/qwq-32b:free",                        // QwQ reasoning
                        "meta-llama/llama-3.3-70b-instruct:free",   // Reliable general model
                        "mistralai/mistral-7b-instruct:free",       // Lightweight fallback
                        "google/gemma-2-9b-it:free",                // Smallest free fallback
                        "openai/gpt-4o");                           // Paid flagship

            // DeepInfra — affordable GPU inference.
            // gemma-4-27b-it: Gemma 4 27B (dense version) available on DeepInfra.
            // gemma-3-27b-it kept as fallback.
            case DEEPINFRA:
                return Arrays.asList(
                        "google/gemma-4-27b-it",              // Gemma 4 on DeepInfra
                        "Qwen/Qwen3-235B-A22B",               // Largest Qwen3 open model
                        "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                        "google/gemma-3-27b-it",              // Gemma 3 fallback
                        "microsoft/phi-4");

            // Together AI — strong open-source model selection.
            // DeepSeek R1 and Qwen 2.5 72B confirmed working.
            case TOGETHER:
                return Arrays.asList(
                        "deepseek-ai/DeepSeek-R1",            // Best reasoning on Together
                        "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                        "Qwen/Qwen2.5-72B-Instruct-Turbo",
                        "google/gemma-2-27b-it");

            // Hyperbolic — $10 free credit on signup.
            case HYPERBOLIC:
                return Arrays.asList(
                        "deepseek-ai/DeepSeek-R1",
                        "meta-llama/Llama-3.3-70B-Instruct",
                        "Qwen/Qwen2.5-72B-Instruct");

            // Kluster AI — batch + realtime inference.
            case KLUSTER:
                return Arrays.asList(
                        "klusterai/Meta-Llama-3.3-70B-Instruct-Turbo",
                        "klusterai/Meta-Llama-3.1-8B-Instruct-Turbo",
                        "Qwen/Qwen2.5-72B-Instruct");

            // OVH AI — European provider, 12 req/min.
            case OVH:
                return Arrays.asList(
                        "Llama-3.3-70B-Instruct",
                        "Qwen2.5-72B-Instruct",
                        "Mistral-7B-Instruct-v0.3");

            // Lambda Labs — affordable GPU cloud.
            case LAMBDA:
                return Arrays.asList(
                        "llama3.3-70b-instruct-fp8",
                        "qwen25-coder-32b-instruct",
                        "llama3.1-8b-instruct");

            // Fireworks AI — fast inference for large models.
            // deepseek-v3 and llama 4 confirmed available.
            case FIREWORKS:
                return Arrays.asList(
                        "accounts/fireworks/models/deepseek-v3",
                        "accounts/fireworks/models/llama-v3p3-70b-instruct",
                        "accounts/fireworks/models/llama4-scout-instruct-basic",
                        "accounts/fireworks/models/qwen2p5-72b-instruct");

            // Novita AI — affordable inference.
            // gemma-3-27b-it replaced by gemma-4-27b-it (Gemma 4 available on Novita).
            case NOVITA:
                return Arrays.asList(
                        "meta-llama/llama-3.3-70b-instruct",
                        "deepseek/deepseek-v3",
                        "google/gemma-4-27b-it",   // Gemma 4 on Novita
                        "meta-llama/llama-4-scout");

            // Morph LLM — specialised for precise code editing (surgical diffs).
            case MORPH:
                return Arrays.asList(
                        "morph-v3-fast",
                        "morph-v3");

            default:
                return Collections.emptyList();
        }
    }

    public static String getDefaultModel(AiProvider provider) {
        List<String> models = getStaticModels(provider);
        return models.isEmpty() ? "" : models.get(0);
    }

    public static boolean isModelValidForProvider(AiProvider provider, String modelId) {
        if (provider == null || modelId == null || modelId.isEmpty()) return false;
        return getStaticModels(provider).contains(modelId);
    }
}
