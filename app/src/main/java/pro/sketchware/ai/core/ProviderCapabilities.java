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
    /** Human-readable free-tier limits shown under the provider card (e.g. "30 RPM · 14,400 RPD"). Empty for paid providers. */
    public final String  freeTierSummary;

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
        this.freeTierSummary       = b.freeTierSummary;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Returns the known capabilities for the given provider.
     * Falls back to conservative defaults for unknown providers.
     */
    @NonNull
    @SuppressWarnings("deprecation") // intentionally still handles the legacy CHUTES case
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
                return new Builder()
                        .streaming(true).tools(true).vision(true)
                        .jsonMode(true).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(1_000_000).maxOutput(8_192).freeRpm(-1).build();

            case GOOGLE_AI_STUDIO:
                return new Builder()
                        .streaming(true).tools(true).vision(true)
                        .jsonMode(true).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(1_000_000).maxOutput(8_192).freeRpm(15)
                        .freeTierSummary("Flash: 15 RPM · 500 RPD · 250K TPM · Gemma: 30 RPM · 14.4K RPD").build();

            case GROQ:
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(true).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(131_072).maxOutput(8_192).freeRpm(30)
                        .freeTierSummary("30 RPM · 70B: 1K RPD · 8B: 14.4K RPD · 12K–30K TPM").build();

            case DEEPSEEK:
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(true).reasoning(true).systemPrompts(true).temperature(true)
                        .maxContext(64_000).maxOutput(8_192).freeRpm(-1).build();

            case CEREBRAS:
                // Phase 5.2: tools(true) — CerebrasApiClient already builds requests through
                // NvidiaApiClient.buildOpenAiRequestBody() (native OpenAI "tools"/"tool_choice"
                // format) and reads responses through parseOpenAiSseStream() (which already
                // accumulates streamed "tool_calls" deltas into ToolCall objects) — the same
                // shared code path every OpenAI-compatible provider in this app uses. tools(false)
                // here had no code-level reason behind it; it was an unverified conservative
                // default. Flipping it is a config change, not new code, but the actual hosted
                // Cerebras models' tool-calling reliability still needs field verification —
                // see CHANGES.md Phase 5.2.
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(8_192).maxOutput(8_192).freeRpm(30)
                        .freeTierSummary("30 RPM · 14,400 RPD · 1M TPD").build();

            case SAMBANOVA:
                // Phase 5.2: tools(true) — see the CEREBRAS case above for the shared reasoning
                // (same buildOpenAiRequestBody / parseOpenAiSseStream path, same "unverified
                // default, not a tested rejection" situation). Needs field verification.
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(32_768).maxOutput(4_096).freeRpm(30)
                        .freeTierSummary("$5 trial credits · 3-month validity").build();

            case CHUTES:
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(128_000).maxOutput(8_192).freeRpm(60)
                        .freeTierSummary("Free · no key needed · community proxied").build();

            case MISTRAL:
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(true).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(32_768).maxOutput(8_192).freeRpm(20)
                        .freeTierSummary("1 RPS · Codestral: 2K RPD · 500K TPM").build();

            case SCALEWAY:
                // Phase 5.2: tools(true) — see the CEREBRAS case above for the shared reasoning.
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(32_768).maxOutput(4_096).freeRpm(-1)
                        .freeTierSummary("1M free tokens/month").build();

            case NVIDIA:
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(true).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(128_000).maxOutput(8_192).freeRpm(40)
                        .freeTierSummary("40 RPM free · phone verification required").build();

            case OPENROUTER:
                return new Builder()
                        .streaming(true).tools(true).vision(true)
                        .jsonMode(true).reasoning(true).systemPrompts(true).temperature(true)
                        .maxContext(200_000).maxOutput(8_192).freeRpm(20)
                        .freeTierSummary("20 RPM · 50 RPD (free models)").build();

            case HUGGINGFACE:
                // Phase 5.2: tools(true) — see the CEREBRAS case above for the shared reasoning.
                // Worth extra caution here specifically: HuggingFace's Inference API fronts many
                // different underlying model families, some of which may not honor "tools" at
                // all — field verification matters more here than for the single-backend
                // providers above.
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(32_768).maxOutput(4_096).freeRpm(-1)
                        .freeTierSummary("$0.10 free credits/month").build();

            case HYPERBOLIC:
                // Phase 5.2: tools(true) — see the CEREBRAS case above for the shared reasoning.
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(32_768).maxOutput(4_096).freeRpm(-1)
                        .freeTierSummary("$1 free credit on signup").build();

            case XAI_GROK:
                return new Builder()
                        .streaming(true).tools(true).vision(true)
                        .jsonMode(true).reasoning(true).systemPrompts(true).temperature(true)
                        .maxContext(131_072).maxOutput(8_192).freeRpm(-1).build();

            case MORPH:
                // Phase 5.2: tools(true) — see the CEREBRAS case above for the shared reasoning.
                // Note temperature(false) here is unrelated and left untouched — Morph is a code
                // patch-application specialist model where temperature is deliberately not
                // exposed; that's a separate, already-verified decision.
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(false)
                        .maxContext(32_768).maxOutput(4_096).freeRpm(-1).build();

            case LOCAL_LLM:
                // Small on-device model, no vision. llama.cpp migration (this session): context
                // window is now a load-time parameter (n_ctx = 8192, fixed for every model — see
                // LlamaCppEngineBridge.CONTEXT_SIZE_TOKENS) instead of a value baked into the
                // exported model file at export time, which is what LiteRT-LM's ekv4096 exports
                // required (see LocalModelCatalog for that history).
                //
                // Context/Token MVP: tools(true) — re-enabled. Phase 5.4's tools(false) was a
                // reaction to sending the full 106-tool catalog (8,000-11,000+ tokens) on every
                // request, which alone blew past the old 4096-token KV cache. AgentExecutor
                // sends only the 7-tool essential subset (ToolRegistry.getEssentialTools()) to
                // LocalModelProvider's own tool-block builder, but note: AgentExecutor's general
                // toolRegistry.getToolsForContextBudget(caps.maxContextTokens) tiering (TINY/
                // MEDIUM/LARGE) is shared across every provider, and 8192 now exceeds
                // ToolRegistry.TINY_CONTEXT_THRESHOLD_TOKENS (4096) — this local model will
                // auto-promote out of the TINY tier to MEDIUM (~20 tools) unless that's
                // deliberately capped elsewhere. Per the approved llama.cpp migration plan this
                // promotion is NOT meant to happen silently in this change — verify the actual
                // tool-tier this resolves to and whether ~20 tools' worth of prompt still fits
                // the 8192-token budget before relying on it; if not, this provider may need an
                // explicit override rather than relying on the shared tiering thresholds.
                // LocalModelProvider's existing pre-flight TokenBudgetChecker check still rejects
                // any prompt that ends up too large in practice — see that class's
                // sendChatRequest() — but that's a safety net, not a substitute for verifying the
                // tiering choice deliberately.
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(8_192).maxOutput(2_048).freeRpm(-1)
                        .freeTierSummary("On-device — no rate limits, speed depends on your hardware").build();

            case NOVITA:
                // Phase 5.2: tools(true) — see the CEREBRAS case above for the shared reasoning.
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(32_768).maxOutput(4_096).freeRpm(-1)
                        .freeTierSummary("$0.50 trial credit · 1-year validity").build();

            case FIREWORKS:
                return new Builder()
                        .streaming(true).tools(true).vision(false)
                        .jsonMode(true).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(32_768).maxOutput(4_096).freeRpm(-1)
                        .freeTierSummary("$1 free trial credit").build();

            default:
                return new Builder()
                        .streaming(true).tools(false).vision(false)
                        .jsonMode(false).reasoning(false).systemPrompts(true).temperature(true)
                        .maxContext(32_768).maxOutput(4_096).freeRpm(-1).build();
        }
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /** Public builder — allows {@link pro.sketchware.ai.core.ModelCapabilities} to create overrides. */
    public static final class Builder {
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
        String  freeTierSummary       = "";

        public Builder() {}

        /** Copy-constructor: initialises all fields from an existing instance. */
        public Builder from(@NonNull ProviderCapabilities src) {
            this.supportsStreaming     = src.supportsStreaming;
            this.supportsTools         = src.supportsTools;
            this.supportsVision        = src.supportsVision;
            this.supportsJsonMode      = src.supportsJsonMode;
            this.supportsReasoning     = src.supportsReasoning;
            this.supportsSystemPrompts = src.supportsSystemPrompts;
            this.supportsTemperature   = src.supportsTemperature;
            this.maxContextTokens      = src.maxContextTokens;
            this.maxOutputTokens       = src.maxOutputTokens;
            this.freeRpmLimit          = src.freeRpmLimit;
            this.freeTierSummary       = src.freeTierSummary;
            return this;
        }

        public Builder streaming(boolean v)      { supportsStreaming = v;     return this; }
        public Builder tools(boolean v)          { supportsTools = v;         return this; }
        public Builder vision(boolean v)         { supportsVision = v;        return this; }
        public Builder jsonMode(boolean v)       { supportsJsonMode = v;      return this; }
        public Builder reasoning(boolean v)      { supportsReasoning = v;     return this; }
        public Builder systemPrompts(boolean v)  { supportsSystemPrompts = v; return this; }
        public Builder temperature(boolean v)    { supportsTemperature = v;   return this; }
        public Builder maxContext(int v)         { maxContextTokens = v;      return this; }
        public Builder maxOutput(int v)          { maxOutputTokens = v;       return this; }
        public Builder freeRpm(int v)            { freeRpmLimit = v;          return this; }
        public Builder freeTierSummary(String v) { freeTierSummary = v != null ? v : ""; return this; }

        public ProviderCapabilities build() { return new ProviderCapabilities(this); }
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
