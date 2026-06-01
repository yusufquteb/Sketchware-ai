package pro.sketchware.ai.models;

public enum AiProvider {

    // ════════════════════════════════════════════════════════
    // GROUP 1 — Free, no API key needed
    // ════════════════════════════════════════════════════════

    // LLM7.io — 30 req/min anonymous (no key), 120 RPM with free token.
    // Best rate-limit of any no-key provider. OpenAI-compatible. UK-based, actively maintained.
    LLM7("LLM7.io",
            "https://api.llm7.io",
            "/v1/models", "/v1/chat/completions", false, false,
            "LLM7.io — completely free, no sign-up or API key required. " +
            "Best rate-limit of all no-key providers: 30 req/min anonymous. " +
            "OpenAI-compatible. Includes GPT-4o-mini, DeepSeek R1, Qwen Coder, Gemini Flash Lite."),

    POLLINATIONS("Pollinations AI",
            "https://text.pollinations.ai",
            "/models", "/openai", false, false,
            "Pollinations AI — completely free, no sign-up or API key needed. " +
            "OpenAI-compatible endpoint routing to GPT-4o, Claude Opus 4.8, DeepSeek V3, Mistral, Qwen. " +
            "Note: anonymous use is rate-limited to ~1 req/min. Get a free key at enter.pollinations.ai for higher limits."),

    CHUTES("AirForce AI",
            "https://api.airforce",
            "/v1/models", "/v1/chat/completions", false, false,
            "AirForce AI — completely free community API, no API key required. " +
            "Provides access to Claude, DeepSeek V3, Gemini Flash, and Qwen3 235B via proxy. " +
            "Zero setup — perfect default for first-time users."),

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

    COHERE("Cohere",
            "https://api.cohere.com",
            "/v1/models", "/v2/chat", true, false,
            "Cohere — Command R+ and Command R models. Free tier: 20 req/min, 1,000 req/month. API key at dashboard.cohere.com."),

    GITHUB_MODELS("GitHub Models",
            "https://models.inference.ai.azure.com",
            "/v1/models", "/v1/chat/completions", true, false,
            "GitHub Models — access GPT-4o, o3, Llama, Mistral, Phi via GitHub token. Free with any GitHub account."),

    SCALEWAY("Scaleway",
            "https://api.scaleway.ai",
            "/v1/models", "/v1/chat/completions", true, false,
            "Scaleway Generative APIs — European cloud with 1M free tokens/month. Supports Llama, Mistral, and Qwen models."),

    CLOUDFLARE("Cloudflare Workers AI",
            "https://api.cloudflare.com",
            "/client/v4/accounts/{account_id}/ai/models/search", "/client/v4/accounts/{account_id}/ai/run/{model}", true, false,
            "Cloudflare Workers AI — 10,000 neurons/day free tier. Includes Gemma 4 (256K ctx, tool-calling), " +
            "Llama 3.3 70B, DeepSeek R1, and Qwen Coder 32B. Account ID required alongside API key."),

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

    // OVHcloud — free anonymous tier: 2 RPM, no account, no sign-up.
    // Use empty API key or "anonymous" — documented permanent free tier from a major EU cloud provider.
    // For higher limits (400 RPM), create a free OVH account.
    OVH("OVHcloud AI",
            "https://oai.endpoints.kepler.ai.cloud.ovh.net",
            "/v1/models", "/v1/chat/completions", false, false,
            "OVHcloud AI Endpoints — permanent free anonymous tier, no sign-up required. " +
            "2 req/min anonymous; up to 400 RPM with free OVH account. " +
            "EU-hosted (GDPR). 40+ open models: Llama 3.3, DeepSeek R1, Qwen3 Coder 262K ctx."),

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
            "API key: morphllm.com/dashboard/api-keys");

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
    public String getLimitsText() {
        switch (this) {
            case LLM7:
                return "30 RPM anonymous (no key) • 120 RPM with free token from token.llm7.io";
            case POLLINATIONS:
                return "~1 req/min anonymous (no key) — get free key at enter.pollinations.ai for higher limits";
            case CHUTES:
                return "Completely free — no API key — no published rate limits";
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
            case COHERE:
                return "20 RPM • 1,000 req/month";
            case GITHUB_MODELS:
                return "Free with any GitHub account — low limits on free tier\n"
                     + "Higher limits with GitHub Copilot subscription";
            case SCALEWAY:
                return "1M tokens one-time free trial credit (pay after)";
            case CLOUDFLARE:
                return "10,000 neurons/day (≈ 1,000–2,000 requests/day)\n"
                     + "Requires account_id alongside API key";
            case OVH:
                return "2 RPM anonymous (no key) — 400 RPM with free OVH account";
            default:
                return "";
        }
    }

    // ── Provider Group ────────────────────────────────────────────────────────

    /** UI category used to separate providers into labelled sections. */
    public enum ProviderGroup {
        FREE_NO_API("Free — No API Key Needed"),
        FREE_WITH_API("Free — API Key Required"),
        PAID("Paid Providers");

        private final String title;
        ProviderGroup(String t) { this.title = t; }
        public String getTitle() { return title; }
    }

    public ProviderGroup getGroup() {
        if (!apiKeyRequired) return ProviderGroup.FREE_NO_API;
        switch (this) {
            case GROQ:
            case GOOGLE_AI_STUDIO:
            case SAMBANOVA:
            case CEREBRAS:
            case HUGGINGFACE:
            case MISTRAL:
            case COHERE:
            case GITHUB_MODELS:
            case SCALEWAY:
            case CLOUDFLARE:
                return ProviderGroup.FREE_WITH_API;
            default:
                return ProviderGroup.PAID;
        }
    }

    // ── Labels ────────────────────────────────────────────────────────────────

    public String getSelectorLabel() {
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
