package pro.sketchware.ai.models;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Curated, verified model ID lists for every AiProvider.
 *
 * Ordering rules (applied within each provider):
 *  1. Coding-specialised models first (devstral, qwen-coder, deepseek-v3, etc.)
 *  2. Higher capability / larger context window before smaller
 *  3. Free models before paid models (within same provider)
 *  4. Reasoning models after general-purpose (they use more tokens)
 *
 * Update policy:
 *  - Only add a model after confirming it responds without error.
 *  - Mark removed models with an inline comment explaining why.
 *
 * Coding benchmark notes (2026, Java/Android relevance):
 *  - Claude Opus 4.8    — #1 SWE-bench 80.8%
 *  - Claude Sonnet 4.6  — #2 SWE-bench 79.6%
 *  - Gemini 2.5 Pro     — leads 13/16 major benchmarks
 *  - DeepSeek V3/V4     — best open-weight code model, very affordable
 *  - Qwen3 Coder        — best specialised open coding model (1M ctx)
 *  - Devstral Small     — agentic coding specialist (Mistral, free)
 *  - GPT-4o             — reliable all-round, strong at instruction following
 */
public final class AiProviderModels {

    private AiProviderModels() {}

    public static List<String> getStaticModels(AiProvider provider) {
        if (provider == null) return Collections.emptyList();
        switch (provider) {

            // ══════════════════════════════════════════════════════════════════
            // GROUP 1 — Free, NO API key needed
            // ══════════════════════════════════════════════════════════════════

            // LLM7.io — free, no API key, 30 RPM anonymous (best no-key rate limit).
            // Models confirmed active June 2026.
            case LLM7:
                return Arrays.asList(
                        "qwen2.5-coder-32b-instruct",       // Coding specialist, 32K ctx ✅
                        "deepseek-r1-0528",                 // Latest DeepSeek R1 reasoning ✅
                        "gpt-4o-mini-2024-07-18",           // GPT-4o Mini via proxy ✅
                        "gemini-2.5-flash-lite",            // Gemini Flash Lite, multimodal ✅
                        "gpt-o4-mini-2025-04-16",           // o4-mini reasoning via proxy ✅
                        "mistral-small-3.1-24b-instruct-2503", // Mistral Small 3.1 ✅
                        "gpt-4.1-nano-2025-04-14");         // Lightweight nano model ✅

            // Pollinations AI — completely free, no sign-up.
            // OpenAI-compatible endpoint at /openai.
            // Models are routed by name: "openai" → GPT-4o proxy, etc.
            case POLLINATIONS:
                return Arrays.asList(
                        "openai",        // GPT-4o equivalent via Pollinations proxy — strongest
                        "mistral",       // Mistral Large equivalent — strong code
                        "qwen-coder",    // Qwen2.5-Coder — coding specialist
                        "claude",        // Claude proxy — strong at instructions
                        "searchgpt");    // GPT-4o + live web search

            // AirForce AI (api.airforce) — community proxy, no sign-up.
            // Removed: gpt-4o, gpt-4o-mini, llama-4-maverick, llama-4-scout
            //          → all return "Invalid API Key" from this proxy.
            // Confirmed working (May 2026): deepseek-v3, claude-3-7-sonnet,
            //   gemini-2.0-flash, Qwen/Qwen3-235B-A22B, claude-3-5-sonnet.
            case CHUTES:
                return Arrays.asList(
                        "deepseek-v3",           // Best free code model via proxy
                        "claude-3-7-sonnet",     // Claude 3.7 via proxy — top code quality
                        "claude-3-5-sonnet",     // Claude 3.5 fallback
                        "gemini-2.0-flash",      // Gemini via proxy — 1M ctx
                        "Qwen/Qwen3-235B-A22B",  // Largest free open model, 235B MoE
                        "mistral-small-latest"); // Lightweight fallback

            // ══════════════════════════════════════════════════════════════════
            // GROUP 2 — Free WITH API key (genuine ongoing free tiers)
            // ══════════════════════════════════════════════════════════════════

            // Google AI Studio — best free API in 2026.
            // Free tier (June 2026): Gemini 2.5 Flash = 1,500 req/day, 1M ctx, 10 RPM.
            // gemini-2.5-pro = only 50 req/day on free tier → not practical, omitted.
            // Gemma 3 models removed: all returned "Model Not Found" via Studio API.
            // gemma-4-0-preview-04-17: Gemma 4 via Gemini API (experimental).
            case GOOGLE_AI_STUDIO:
                return Arrays.asList(
                        "gemini-2.5-flash",           // Best free: 1,500 req/day, 1M ctx
                        "gemini-2.0-flash",           // Solid fallback: 1,500 req/day
                        "gemini-2.0-flash-lite",      // Lightweight: 1,500 req/day
                        "gemma-4-0-preview-04-17");   // Gemma 4 (experimental)

            // SambaNova — free tier with rate limits.
            // Only Meta-Llama-3.3-70B-Instruct confirmed working (others → errors).
            case SAMBANOVA:
                return Arrays.asList(
                        "Meta-Llama-3.3-70B-Instruct");

            // Cerebras — ultra-fast LPU inference, free tier.
            // qwen-3-32b: best for coding on Cerebras, fast inference.
            case CEREBRAS:
                return Arrays.asList(
                        "qwen-3-32b",     // Best coding — Qwen3 on fast Cerebras hardware
                        "llama-3.3-70b",  // Strong general, 128K ctx
                        "llama-3.1-8b");  // Fastest, highest rate limits

            // Groq — blazing fast LPU inference, generous free tier.
            // qwen3-32b: Qwen3 32B — strong Java/Android code, 1M ctx.
            // compound-beta: Groq Compound Agent with built-in web search.
            // Removed: gemma2-9b-it, deepseek-r1-distill-llama-70b → Bad Request.
            // Removed: mixtral-8x7b-32768 → Bad Request.
            // Removed: llama-4-scout-17b-16e-preview → Model Not Found.
            case GROQ:
                return Arrays.asList(
                        "qwen3-32b",               // Best coding on Groq, 1M ctx, fast
                        "llama-3.3-70b-versatile", // Best quality general model on Groq
                        "compound-beta",           // Agentic: web search + tool execution
                        "qwen-qwq-32b",            // Reasoning model — complex problems
                        "llama-3.1-8b-instant");   // Fastest, highest rate limit fallback

            // HuggingFace Inference API — free $0.10/month credit.
            // Prefer smaller models (< 10 GB) to stay within free credit.
            // Mistral 7B confirmed; larger models (70B+) consume credit faster.
            case HUGGINGFACE:
                return Arrays.asList(
                        "mistralai/Mistral-7B-Instruct-v0.3", // Reliable, small, free credit-friendly
                        "google/gemma-2-27b-it",              // Strong general, larger
                        "Qwen/Qwen2.5-72B-Instruct",          // Best quality (uses more credit)
                        "meta-llama/Llama-3.3-70B-Instruct"); // Large — uses credit quickly

            // Mistral — free experimental plan.
            // devstral-small-latest: NEW (2026) — specialised for agentic coding, free.
            // codestral-latest: code specialist, free on experimental plan.
            // mistral-large-latest REMOVED — paid model (not on free tier).
            case MISTRAL:
                return Arrays.asList(
                        "devstral-small-latest",  // #1 coding: agentic code tasks, free, 32K ctx
                        "codestral-latest",        // Code specialist: completions + FIM, free, 32K
                        "mistral-small-latest",   // Best general free model, 128K ctx
                        "open-mistral-nemo");     // Lightest free model, 128K ctx

            // Cohere — 20 req/min, 1,000 req/month free.
            // command-r-plus: best quality, RAG-optimised.
            case COHERE:
                return Arrays.asList(
                        "command-r-plus",  // Best quality, 128K ctx
                        "command-r",       // Good balance
                        "command-light");  // Fastest, cheapest

            // GitHub Models — free with any GitHub account.
            // Llama 4 Scout and GPT-4o are the strongest available for free.
            // phi-4-mini-reasoning: small but surprisingly capable at code reasoning.
            case GITHUB_MODELS:
                return Arrays.asList(
                        "meta/llama-4-scout",                  // Most capable free model on GitHub
                        "openai/gpt-4o",                       // GPT-4o free via GitHub — strong
                        "meta/llama-3.3-70b-instruct",         // Reliable, 128K ctx
                        "mistral-ai/mistral-large-2411",       // Mistral Large, free via GitHub
                        "openai/gpt-4o-mini",                  // Fast, lightweight
                        "microsoft/phi-4",                     // Strong reasoning for its size
                        "microsoft/phi-4-mini-reasoning");     // Smallest, fast code reasoning

            // Scaleway — 1M token one-time trial credit (then pay-per-token).
            // Qwen 2.5 72B first: best coding model available on Scaleway.
            case SCALEWAY:
                return Arrays.asList(
                        "qwen2.5-72b-instruct",       // Best coding, 128K ctx
                        "llama-3.3-70b-instruct",     // Strong general, 128K ctx
                        "llama-3.1-8b-instruct",      // Fast lightweight fallback
                        "mistral-nemo-instruct-2407"); // Small Mistral fallback

            // Cloudflare Workers AI — 10,000 neurons/day free.
            // UPDATED April 2026: gemma-4-26b-a4b-it (Gemma 4 MoE, 256K ctx, tool-calling).
            // qwen2.5-coder-32b first after Gemma 4 as coding specialist.
            case CLOUDFLARE:
                return Arrays.asList(
                        "@cf/google/gemma-4-26b-a4b-it",              // Gemma 4: 256K ctx, tool-calling, vision ✅
                        "@cf/qwen/qwen2.5-coder-32b-instruct",        // Coding specialist, 32K ctx ✅
                        "@cf/deepseek-ai/deepseek-r1-distill-llama-70b", // Reasoning + code ✅
                        "@cf/meta/llama-3.3-70b-instruct-fp8-fast",   // Fast fp8, 128K ctx ✅
                        "@cf/meta/llama-3.1-8b-instruct");            // Lightweight fallback ✅

            // ══════════════════════════════════════════════════════════════════
            // GROUP 3 — Paid providers
            // ══════════════════════════════════════════════════════════════════

            // OpenAI — paid. GPT-4o: best instruction following. o4-mini: reasoning.
            // o1-mini removed (deprecated by OpenAI).
            case OPENAI:
                return Arrays.asList(
                        "gpt-4o",       // Best all-round, strong at Java/Android
                        "o4-mini",      // Best value reasoning model
                        "gpt-4o-mini",  // Fast, cheap, good for simple tasks
                        "o3",           // Top reasoning, expensive
                        "o3-mini");     // Older reasoning, cheaper than o3

            // Anthropic — best overall for complex Java/Android code in 2026.
            // SWE-bench: claude-opus-4-8 80.8% (#1), claude-sonnet-4-6 79.6% (#2).
            case ANTHROPIC:
                return Arrays.asList(
                        "claude-opus-4-8",           // #1 SWE-bench 80.8% — best for code
                        "claude-sonnet-4-6",          // #2 79.6% — best value paid Claude
                        "claude-sonnet-4-5",          // Previous generation, still strong
                        "claude-haiku-4-5-20251001",  // Fastest, cheapest Claude
                        "claude-opus-4-7",
                        "claude-opus-4-5",
                        "claude-3-5-sonnet-20241022");

            // Gemini paid API — gemini-2.5-pro leads 13/16 major benchmarks (2026).
            // 1M context window; 60% cheaper than Claude Opus with comparable code quality.
            // gemini-1.5 series removed — all return "Model Not Found".
            case GEMINI:
                return Arrays.asList(
                        "gemini-2.5-pro",      // #1 in most benchmarks, 1M ctx, coding champion
                        "gemini-2.5-flash",    // Best value: fast + strong, 1M ctx
                        "gemini-2.0-flash",    // Reliable, 1M ctx
                        "gemini-2.0-flash-lite"); // Lightweight option

            // DeepSeek — deepseek-chat = V3 (outstanding code for the price).
            // 5M free tokens on signup; very low ongoing price after.
            case DEEPSEEK:
                return Arrays.asList(
                        "deepseek-chat",      // DeepSeek V3 — best value code model ($0.27/M)
                        "deepseek-reasoner"); // DeepSeek R1 — reasoning + coding

            // xAI Grok — real-time web knowledge built in.
            case XAI_GROK:
                return Arrays.asList(
                        "grok-3",       // Most capable, real-time knowledge
                        "grok-3-mini"); // Faster, cheaper

            // NVIDIA NIM — enterprise GPU inference.
            // Only meta/llama-3.3-70b-instruct confirmed working (others → errors).
            case NVIDIA:
                return Arrays.asList(
                        "meta/llama-3.3-70b-instruct");

            // OpenRouter — aggregates 200+ models; :free suffix = zero cost.
            // openrouter/free: auto-router — always picks the best available free model.
            // Ordering: free router first, then best free coding models, smallest free last.
            // openai/gpt-4o REMOVED — paid, no :free suffix.
            case OPENROUTER:
                return Arrays.asList(
                        "openrouter/free",                         // Auto-picks best free model ✅
                        "google/gemma-4-31b-it:free",              // Gemma 4: 256K ctx, tool-calling ✅
                        "deepseek/deepseek-v4-flash:free",         // DeepSeek V4: 1M ctx, fast code ✅
                        "qwen/qwen3-coder:free",                   // Best free coding: 1M ctx, coding specialist ✅
                        "deepseek/deepseek-r1:free",               // Reasoning + code ✅
                        "openai/gpt-oss-20b:free",                 // OpenAI open-source, matches o3-mini ✅
                        "qwen/qwq-32b:free",                       // QwQ reasoning model ✅
                        "meta-llama/llama-3.3-70b-instruct:free",  // Reliable general fallback ✅
                        "mistralai/mistral-7b-instruct:free",      // Lightweight free fallback ✅
                        "google/gemma-2-9b-it:free");              // Smallest free fallback ✅

            // DeepInfra — affordable pay-per-token inference.
            // Qwen3 235B first: largest open coding model available.
            case DEEPINFRA:
                return Arrays.asList(
                        "Qwen/Qwen3-235B-A22B",                    // Largest open model, best code
                        "google/gemma-4-27b-it",                   // Gemma 4 dense, tool-calling
                        "meta-llama/Llama-3.3-70B-Instruct-Turbo", // Fast 70B
                        "microsoft/phi-4",                         // Strong reasoning for size
                        "google/gemma-3-27b-it");                  // Gemma 3 fallback

            // Together AI — strong open-source model selection, trial credits on signup.
            // DeepSeek-R1 first: best reasoning+code on Together.
            case TOGETHER:
                return Arrays.asList(
                        "deepseek-ai/DeepSeek-R1",                  // Best reasoning+code on Together
                        "Qwen/Qwen2.5-72B-Instruct-Turbo",          // Strong coding, 128K ctx
                        "meta-llama/Llama-3.3-70B-Instruct-Turbo",  // Reliable general
                        "google/gemma-2-27b-it");                   // Fallback

            // Hyperbolic — $10 free credit on signup, then pay-per-token.
            case HYPERBOLIC:
                return Arrays.asList(
                        "deepseek-ai/DeepSeek-R1",            // Best reasoning
                        "Qwen/Qwen2.5-72B-Instruct",          // Strong coding
                        "meta-llama/Llama-3.3-70B-Instruct"); // Reliable fallback

            // Kluster AI — batch + realtime inference.
            case KLUSTER:
                return Arrays.asList(
                        "Qwen/Qwen2.5-72B-Instruct",                       // Best coding
                        "klusterai/Meta-Llama-3.3-70B-Instruct-Turbo",     // Strong general
                        "klusterai/Meta-Llama-3.1-8B-Instruct-Turbo");     // Fast lightweight

            // OVHcloud AI Endpoints — free anonymous tier (2 RPM), no key needed.
            // Qwen3-Coder-30B has 262K context window — excellent for large Java files.
            // Models confirmed June 2026: https://endpoints.ai.cloud.ovh.net/catalog
            case OVH:
                return Arrays.asList(
                        "Qwen3-Coder-30B-A3B-Instruct",       // Coding specialist, 262K ctx ✅
                        "DeepSeek-R1-Distill-Llama-70B",      // Reasoning + code, 131K ctx ✅
                        "Meta-Llama-3.3-70B-Instruct",        // Strong general, 131K ctx ✅
                        "Qwen2.5-VL-72B-Instruct",            // Vision + text, 72B ✅
                        "Mistral-Nemo-Instruct-2407");         // Lightweight fallback ✅

            // Lambda Labs — affordable GPU cloud.
            // qwen25-coder-32b first: coding specialist.
            case LAMBDA:
                return Arrays.asList(
                        "qwen25-coder-32b-instruct",   // Coding specialist, best for Java/Android
                        "llama3.3-70b-instruct-fp8",   // Strong general, fp8 fast
                        "llama3.1-8b-instruct");        // Lightweight fallback

            // Fireworks AI — fast inference for large models.
            // DeepSeek V3 first: best code quality on Fireworks.
            case FIREWORKS:
                return Arrays.asList(
                        "accounts/fireworks/models/deepseek-v3",               // Best code quality
                        "accounts/fireworks/models/qwen2p5-72b-instruct",       // Strong coding
                        "accounts/fireworks/models/llama4-scout-instruct-basic", // Llama 4
                        "accounts/fireworks/models/llama-v3p3-70b-instruct");   // Reliable fallback

            // Novita AI — affordable inference.
            // DeepSeek V3 first: best code model.
            case NOVITA:
                return Arrays.asList(
                        "deepseek/deepseek-v3",               // Best code model on Novita
                        "google/gemma-4-27b-it",              // Gemma 4 on Novita
                        "meta-llama/llama-3.3-70b-instruct",  // Strong general
                        "meta-llama/llama-4-scout");           // Llama 4 Scout

            // Morph LLM — specialised for precise code editing (surgical diffs).
            // morph-v3-fast first: same quality as v3 but 2x faster.
            case MORPH:
                return Arrays.asList(
                        "morph-v3-fast", // Faster code editing (recommended)
                        "morph-v3");     // Slower but slightly more thorough

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
