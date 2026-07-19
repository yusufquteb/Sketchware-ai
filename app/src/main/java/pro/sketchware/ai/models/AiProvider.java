package pro.sketchware.ai.models;

public enum AiProvider {

    // ════════════════════════════════════════════════════════
    // GROUP 1 — Free, no API key needed
    // ════════════════════════════════════════════════════════

    /** @deprecated Hidden from UI — kept for binary compatibility with Llm7ApiClient */
    @Deprecated
    LLM7("LLM7.io",
            "https://api.llm7.io",
            "/v1/models", "/v1/chat/completions", false, false,
            "LLM7.io — legacy provider, hidden from UI."),

    /** @deprecated Hidden from UI — kept for binary compatibility with ChutesApiClient */
    @Deprecated
    CHUTES("AirForce AI",
            "https://api.airforce",
            "/v1/models", "/v1/chat/completions", false, false,
            "AirForce AI — legacy provider, hidden from UI."),

        POLLINATIONS("Pollinations AI",
            "https://text.pollinations.ai",
            "/models", "/openai", false, false,
            "Pollinations AI — completely free, no sign-up or API key needed. " +
            "Routes to GPT-4o, Claude, DeepSeek V3, Mistral, Qwen, and more. " +
            "Default provider — works instantly with no setup. Get a free key at enter.pollinations.ai for higher limits."),

    // ════════════════════════════════════════════════════════
    // GROUP 2 — Free with API key (genuine ongoing free tiers)
    // ════════════════════════════════════════════════════════

    GOOGLE_AI_STUDIO("Google AI Studio",
            "https://generativelanguage.googleapis.com",
            "/v1beta/openai/models", "/v1beta/openai/chat/completions", true, false,
            "Google AI Studio — best free API in 2026. Gemini 2.5 Flash: 1,500 req/day, 1M context. " +
            "Also includes Gemini 2.0 Flash and Gemma 4. Free API key at aistudio.google.com."),

    SAMBANOVA("SambaNova",
            "https://api.sambanova.ai",
            "/v1/models", "/v1/chat/completions", true, false,
            "SambaNova Cloud — free fast inference for Meta-Llama-3.3-70B. Free API key at cloud.sambanova.ai."),

    CEREBRAS("Cerebras",
            "https://api.cerebras.ai",
            "/v1/models", "/v1/chat/completions", true, false,
            "Cerebras — ultra-fast inference on custom hardware. Free tier with Llama 3.3 70B and Llama 3.1 8B. API key at cloud.cerebras.ai."),

    GROQ("Groq",
            "https://api.groq.com",
            "/openai/v1/models", "/openai/v1/chat/completions", true, true,
            "Groq — blazing fast inference on LPU hardware. Free tier varies by model: " +
            "Llama 3.3 70B ~1,000 req/day (12K tokens/min); Llama 3.1 8B ~14,400 req/day. " +
            "Use 8B for high-frequency tasks, 70B for best quality."),

    HUGGINGFACE("HuggingFace",
            "https://api-inference.huggingface.co",
            "/v1/models", "/v1/chat/completions", true, false,
            "HuggingFace — free inference for Gemma 2/3, Llama, Mistral, and Qwen models. Free API key at huggingface.co."),

    MISTRAL("Mistral",
            "https://api.mistral.ai",
            "/v1/models", "/v1/chat/completions", true, false,
            "Mistral — La Plateforme. Free experimental plan: Devstral Small (coding), Codestral, Mistral Small, NeMo. " +
            "Devstral is specialised for agentic coding tasks. API key at console.mistral.ai."),

    SCALEWAY("Scaleway",
            "https://api.scaleway.ai",
            "/v1/models", "/v1/chat/completions", true, false,
            "Scaleway Generative APIs — European cloud with 1M free tokens/month. Supports Llama, Mistral, and Qwen models."),

    // ════════════════════════════════════════════════════════
    // GROUP 3 — Paid providers
    // ════════════════════════════════════════════════════════

    OPENAI("OpenAI",
            "https://api.openai.com",
            "/v1/models", "/v1/chat/completions", true, false,
            "OpenAI GPT-4o & o-series — industry-leading code generation. Best-in-class for Java/Android development."),

    ANTHROPIC("Anthropic Claude",
            "https://api.anthropic.com",
            "/v1/models", "/v1/messages", true, false,
            "Anthropic Claude — excellent at following complex instructions. Claude Sonnet 4.6 and Opus 4.7 excel at multi-step Android coding tasks."),

    GEMINI("Gemini",
            "https://generativelanguage.googleapis.com",
            "/v1beta/models", "/v1beta/chat/completions", true, false,
            "Google Gemini — powerful multimodal AI. Best for complex Sketchware projects with large context windows."),

    /**
     * @deprecated Hidden from UI — kept for binary compatibility with users who have DEEPSEEK
     * saved as their preferred provider or API key. Removed from selection because
     * deepseek-reasoner (R1) does not reliably support tool calling, causing the agent to
     * hallucinate instead of using tools. DeepSeek's models remain reachable through
     * OpenRouter/Together/Groq/etc., which route tool calls correctly for their hosted
     * deepseek-v3/r1 models (see ModelCapabilities).
     */
    @Deprecated
    DEEPSEEK("DeepSeek",
            "https://api.deepseek.com",
            "/v1/models", "/v1/chat/completions", true, false,
            "DeepSeek — outstanding code performance. DeepSeek-V3 rivals GPT-4 at a fraction of the cost."),

    XAI_GROK("xAI Grok",
            "https://api.x.ai",
            "/v1/models", "/v1/chat/completions", true, false,
            "xAI Grok — real-time knowledge. Grok-3 is excellent for code and reasoning tasks."),

    NVIDIA("NVIDIA",
            "https://integrate.api.nvidia.com",
            "/v1/models", "/v1/chat/completions", true, false,
            "NVIDIA NIM — enterprise-grade AI models optimized for code. Supports Llama, Mistral, and more."),

    OPENROUTER("OpenRouter",
            "https://openrouter.ai",
            "/api/v1/models", "/api/v1/chat/completions", true, false,
            "OpenRouter — unified API gateway to 100+ AI models including GPT-4, Claude, and Gemini."),

    DEEPINFRA("DeepInfra",
            "https://api.deepinfra.com",
            "/v1/openai/models", "/v1/openai/chat/completions", true, false,
            "DeepInfra — affordable inference for Gemma 2/3, Llama, Qwen, and DeepSeek models."),

    TOGETHER("Together AI",
            "https://api.together.xyz",
            "/v1/models", "/v1/chat/completions", true, false,
            "Together AI — wide open-source model selection. $100 in trial credits on signup (no credit card). " +
            "Pay-per-token after credits expire. Best for testing DeepSeek R1 and Qwen 2.5 72B."),

    HYPERBOLIC("Hyperbolic",
            "https://api.hyperbolic.xyz",
            "/v1/models", "/v1/chat/completions", true, false,
            "Hyperbolic — fast Llama, DeepSeek, and Qwen inference. $10 free credit on signup."),

    KLUSTER("Kluster AI",
            "https://api.kluster.ai",
            "/v1/models", "/v1/chat/completions", true, false,
            "Kluster AI — affordable batch and realtime inference for Llama, Mistral, and Qwen models."),

    // OVHcloud — REMOVED (no longer offered to users).

    LAMBDA("Lambda Labs",
            "https://api.lambdalabs.com",
            "/v1/models", "/v1/chat/completions", true, false,
            "Lambda Labs — affordable GPU cloud with Llama 3.3 70B, Hermes, Qwen and more. API key at lambdalabs.com."),

    FIREWORKS("Fireworks AI",
            "https://api.fireworks.ai",
            "/inference/v1/models", "/inference/v1/chat/completions", true, false,
            "Fireworks AI — fast inference for Llama 4, DeepSeek, Qwen, and Mixtral models."),

    NOVITA("Novita AI",
            "https://api.novita.ai",
            "/v3/openai/models", "/v3/openai/chat/completions", true, false,
            "Novita AI — affordable inference. Supports Llama 4, DeepSeek R1, Gemma 3, and Qwen models."),

    MORPH("Morph LLM",
            "https://api.morphllm.com",
            "/v1/models", "/v1/chat/completions", true, false,
            "Morph LLM — specialized AI for precise code editing and XML layout refinement. " +
            "morph-v3-fast excels at applying surgical code edits with minimal changes. " +
            "API key: morphllm.com/dashboard/api-keys"),

    // ════════════════════════════════════════════════════════
    // GROUP 4 — On-device, offline (no network, no API key)
    // ════════════════════════════════════════════════════════

    /**
     * Runs entirely on-device via LiteRT-LM (com.google.ai.edge.litertlm), no internet
     * connection or API key required. baseUrl/modelsEndpoint/chatEndpoint are unused for
     * this provider (kept as empty strings only because the constructor requires non-null
     * values) — all actual requests go through
     * {@link pro.sketchware.ai.offline.LocalModelProvider} directly to the local engine,
     * never over HTTP.
     */
    LOCAL_LLM("Offline AI (On-Device)",
            "", "", "", false, true,
            "يعمل بالكامل على جهازك بدون إنترنت وبدون مفتاح API. اختر من ٥ موديلات " +
            "(Qwen3 0.6B إلى Gemma 3n E2B) حسب قوة جهازك. يحتاج تنزيل ملف الموديل مرة واحدة " +
            "من إعدادات الذكاء الاصطناعي قبل أول استخدام.");

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String  displayName;
    private final String  baseUrl;
    private final String  modelsEndpoint;
    private final String  chatEndpoint;
    private final boolean apiKeyRequired;
    private final boolean unlimited;
    private final String  description;

    AiProvider(String displayName, String baseUrl, String modelsEndpoint,
               String chatEndpoint, boolean apiKeyRequired, boolean unlimited, String description) {
        this.displayName    = displayName;
        this.baseUrl        = baseUrl;
        this.modelsEndpoint = modelsEndpoint;
        this.chatEndpoint   = chatEndpoint;
        this.apiKeyRequired = apiKeyRequired;
        this.unlimited      = unlimited;
        this.description    = description;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String  getDisplayName()    { return displayName; }
    public String  getBaseUrl()        { return baseUrl; }
    public String  getModelsEndpoint() { return modelsEndpoint; }
    public String  getChatEndpoint()   { return chatEndpoint; }
    public boolean requiresApiKey()    { return apiKeyRequired; }
    public boolean isUnlimited()       { return unlimited; }
    public String  getDescription()    { return description; }

    /** Short rate-limit summary shown under the provider card in Settings. */
    @SuppressWarnings("deprecation") // intentionally handles its own legacy LLM7/CHUTES cases
    public String getLimitsText() {
        switch (this) {
            case LLM7:
                return "Legacy provider — hidden from UI";
            case CHUTES:
                return "Legacy provider — hidden from UI";
            case POLLINATIONS:
                return "~1 req/min anonymous (no key) — get free key at enter.pollinations.ai for higher limits";
            case GOOGLE_AI_STUDIO:
                return "Gemini 2.5 Flash: 10 RPM • 1,500 req/day • 1M ctx (free)\n"
                     + "Gemini 2.0 Flash: 15 RPM • 1,500 req/day • 250K TPM\n"
                     + "Gemma 4: available on free tier via API";
            case SAMBANOVA:
                return "Free tier — rate limited. API key at cloud.sambanova.ai";
            case CEREBRAS:
                return "30 RPM • 60K TPM • 14,400 req/day • 1M tokens/day";
            case GROQ:
                return "Qwen3 32B: 1,000 req/day • 6K tokens/min\n"
                     + "Llama 3.3 70B: 1,000 req/day • 12K TPM\n"
                     + "Llama 3.1 8B: 14,400 req/day • high TPM";
            case HUGGINGFACE:
                return "Free $0.10/month inference credit (small models ≤ 10 GB)";
            case MISTRAL:
                return "Free experimental: Devstral, Codestral, Mistral Small, NeMo\n"
                     + "Codestral: 30 RPM • 2,000 req/day\n"
                     + "Mistral Small: 1 req/sec • 1B tokens/month";
            case SCALEWAY:
                return "1M tokens one-time free trial credit (pay after)";
            case LOCAL_LLM:
                return "No limits, no network — runs on your device's own hardware. " +
                       "Speed and quality depend entirely on your phone (see model picker).";
            default:
                return "";
        }
    }

    // ── Provider Group ────────────────────────────────────────────────────────

    /** UI category used to separate providers into labelled sections. */
    public enum ProviderGroup {
        FREE_NO_API("Free — No API Key Needed"),
        FREE_WITH_API("Free — API Key Required"),
        PAID("Paid Providers"),
        OFFLINE("Offline — On-Device");

        private final String title;
        ProviderGroup(String t) { this.title = t; }
        public String getTitle() { return title; }
    }

    public ProviderGroup getGroup() {
        if (this == LOCAL_LLM) return ProviderGroup.OFFLINE;
        if (!apiKeyRequired) return ProviderGroup.FREE_NO_API;
        switch (this) {
            case GROQ:
            case GOOGLE_AI_STUDIO:
            case SAMBANOVA:
            case CEREBRAS:
            case HUGGINGFACE:
            case MISTRAL:
            case SCALEWAY:
            case NVIDIA:
            case OPENROUTER:
                return ProviderGroup.FREE_WITH_API;
            default:
                return ProviderGroup.PAID;
        }
    }

    // ── Labels ────────────────────────────────────────────────────────────────

    public String getSelectorLabel() {
        if (this == LOCAL_LLM) return displayName + " 📴";
        if (unlimited)       return displayName + " ∞";
        if (!apiKeyRequired) return displayName + " 🆓";
        return displayName;
    }

    public static AiProvider fromName(String name) {
        if (name == null) return null;
        for (AiProvider p : values()) {
            if (p.name().equalsIgnoreCase(name) || p.displayName.equalsIgnoreCase(name))
                return p;
        }
        return null;
    }
}
