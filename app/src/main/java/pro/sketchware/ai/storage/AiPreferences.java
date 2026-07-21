package pro.sketchware.ai.storage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import pro.sketchware.ai.core.CircuitBreaker;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.AiProviderModels;
import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.ai.security.SecureKeyStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AiPreferences {

    private static final String PREFS_NAME = "ai_preferences";
    private static final String KEY_API_KEY_PREFIX = "api_key_";
    private static final String KEY_CACHED_MODELS_PREFIX = "cached_models_";
    private static final String KEY_SELECTED_MODEL_PREFIX = "selected_model_";
    private static final String KEY_SELECTED_PROVIDER = "selected_provider";
    private static final String KEY_SYSTEM_PROMPT = "system_prompt";
    private static final String KEY_TEMPERATURE = "ai_temperature";
    private static final String KEY_MAX_TOKENS = "ai_max_tokens";
    private static final String KEY_AUTO_FIX_ON_ERROR = "ai_auto_fix_on_error";
    /** Added Phase 3 (ai.engine.budget): max estimated INPUT tokens allowed in one outgoing payload. */
    private static final String KEY_MAX_PAYLOAD_TOKENS = "ai_max_payload_tokens";

    /** Prefix for provider enabled toggle — must match AiSettingsActivity.PREF_ENABLED */
    public static final String KEY_PROVIDER_ENABLED = "provider_enabled_";

    /** Morph (MORF) code-edit AI — used to refine AI-generated layouts */
    public static final String KEY_MORPH_API_KEY    = "morph_api_key";
    public static final String KEY_MORPH_ENABLED    = "morph_enabled";
    public static final String KEY_MORPH_FOR_LAYOUT = "morph_for_layout";

    /** Optional: dedicated provider for layout generation (Groq recommended — fast). */
    public static final String KEY_LAYOUT_AI_PROVIDER = "layout_ai_provider";

    /** Added Phase 5.4: Hugging Face personal access token, needed to download gated
     *  on-device models (currently the Gemma-family entries in LocalModelCatalog — see its
     *  isGated()). Stored in the same encrypted secureStore as provider API keys, not plain
     *  SharedPreferences, since it's a credential with the same sensitivity. */
    private static final String KEY_HUGGING_FACE_TOKEN = "hugging_face_token";

    /** Profile-specific model and provider settings */
    private static final String KEY_PROFILE_MODEL_PREFIX = "profile_model_";
    private static final String KEY_PROFILE_PROVIDER_PREFIX = "profile_provider_";

    /** Default models per provider */
    // llama-4-maverick and llama-4-scout both return "Invalid API Key" — use deepseek-v3
    public static final String DEFAULT_POLLINATIONS_MODEL     = "openai";           // GPT-4o equivalent, no key needed
    public static final String DEFAULT_DEEPINFRA_MODEL        = "Qwen/Qwen3-235B-A22B";
    // compound-beta-mini removed: it uses multi-call internally and exhausts tokens/min quickly.
    public static final String DEFAULT_GROQ_MODEL             = "qwen3-32b";        // Best coding model on Groq
    public static final String DEFAULT_TOGETHER_MODEL         = "deepseek-ai/DeepSeek-R1";
    public static final String DEFAULT_SAMBANOVA_MODEL        = "Meta-Llama-3.3-70B-Instruct";
    public static final String DEFAULT_GOOGLE_AI_STUDIO_MODEL = "gemini-2.5-flash"; // 1,500 req/day free
    public static final String DEFAULT_DEEPSEEK_MODEL         = "deepseek-chat";
    public static final String DEFAULT_ANTHROPIC_MODEL        = "claude-opus-4-8";  // #1 SWE-bench
    public static final String DEFAULT_OPENAI_MODEL           = "gpt-4o";
    public static final String DEFAULT_GEMINI_MODEL           = "gemini-2.5-pro";   // Tops 13/16 benchmarks
    public static final String DEFAULT_CEREBRAS_MODEL         = "qwen-3-32b";       // Best coding on Cerebras

    /** Cached content of assets/ai_system_prompt.txt — loaded once, reused. */
    private static volatile String cachedDefaultPrompt = null;

    /**
     * Loads the default system prompt from assets/ai_system_prompt.txt.
     * Result is cached in memory after the first read.
     * Falls back to an empty string on I/O error (avoids crashing the AI pipeline).
     */
    @NonNull
    public static String getDefaultSystemPrompt(@NonNull Context context) {
        if (cachedDefaultPrompt != null) return cachedDefaultPrompt;
        synchronized (AiPreferences.class) {
            if (cachedDefaultPrompt != null) return cachedDefaultPrompt;
            try {
                InputStream is = context.getAssets().open("ai_system_prompt.txt");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                reader.close();
                cachedDefaultPrompt = sb.toString().trim();
            } catch (IOException e) {
                cachedDefaultPrompt = "";
            }
        }
        return cachedDefaultPrompt;
    }

    private static volatile AiPreferences instance;
    private final SharedPreferences prefs;
    private final Gson gson;
    private final SecureKeyStore secureStore;
    private final Context appContext;

    private AiPreferences(@NonNull Context context) {
        Context appCtx = context.getApplicationContext();
        this.appContext = appCtx;
        prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        secureStore = SecureKeyStore.getInstance(appCtx);
        migratePlainApiKeys();
    }

    /**
     * One-time migration: moves any API keys stored in plain SharedPreferences
     * into SecureKeyStore. Removes the old plain-text entries afterward.
     */
    private void migratePlainApiKeys() {
        for (AiProvider provider : AiProvider.values()) {
            String legacyKey = KEY_API_KEY_PREFIX + provider.name();
            if (prefs.contains(legacyKey)) {
                String value = prefs.getString(legacyKey, null);
                if (value != null && !value.isEmpty()) {
                    secureStore.put(legacyKey, value);
                }
                prefs.edit().remove(legacyKey).apply();
            }
        }
    }

    @NonNull
    public static AiPreferences getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AiPreferences.class) {
                if (instance == null) {
                    instance = new AiPreferences(context);
                }
            }
        }
        return instance;
    }

    @NonNull
    public SharedPreferences prefs() { return prefs; }

    public void setApiKey(@NonNull AiProvider provider, @NonNull String key) {
        secureStore.put(KEY_API_KEY_PREFIX + provider.name(), key.trim());
        CircuitBreaker.getInstance().reset(provider.name());
    }

    @Nullable
    public String getApiKey(@NonNull AiProvider provider) {
        return secureStore.get(KEY_API_KEY_PREFIX + provider.name());
    }

    public boolean hasApiKey(@NonNull AiProvider provider) {
        if (!provider.requiresApiKey()) return true;
        String key = getApiKey(provider);
        return key != null && !key.isEmpty();
    }

    /**
     * Returns true if the provider has been toggled ON by the user in AI Settings.
     * Default enabled: all providers that require NO API key (work immediately, zero setup).
     * All other providers (free-with-key or paid) start disabled until the user adds a key.
     */
    @SuppressWarnings("deprecation") // intentionally checks legacy LLM7/CHUTES to exclude them
    public boolean isProviderEnabled(@NonNull AiProvider provider) {
        if (provider == AiProvider.LLM7 || provider == AiProvider.CHUTES
                || provider == AiProvider.DEEPSEEK) return false; // legacy, hidden everywhere
        boolean defaultEnabled = !provider.requiresApiKey(); // POLLINATIONS auto-enabled (free, no key)
        return prefs.getBoolean(KEY_PROVIDER_ENABLED + provider.name(), defaultEnabled);
    }

    public void clearApiKey(@NonNull AiProvider provider) {
        secureStore.remove(KEY_API_KEY_PREFIX + provider.name());
        CircuitBreaker.getInstance().reset(provider.name());
    }

    public void setCachedModels(@NonNull AiProvider provider, @NonNull List<ModelInfo> models) {
        prefs.edit().putString(KEY_CACHED_MODELS_PREFIX + provider.name(), gson.toJson(models)).apply();
    }

    @NonNull
    public List<ModelInfo> getCachedModels(@NonNull AiProvider provider) {
        String json = prefs.getString(KEY_CACHED_MODELS_PREFIX + provider.name(), null);
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type t = new TypeToken<List<ModelInfo>>() {}.getType();
            List<ModelInfo> m = gson.fromJson(json, t);
            return m != null ? m : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void clearCachedModels(@NonNull AiProvider provider) {
        prefs.edit().remove(KEY_CACHED_MODELS_PREFIX + provider.name()).apply();
    }

    /**
     * Removes ONE stale model from a provider's cached list, keeping the rest.
     *
     * <p>Audit fix (the "300 models collapse to 1" field report, e.g. NVIDIA): when a single
     * cached model ID went stale (API removed it), {@code AgentExecutor}'s model-not-found
     * retry used to call {@link #clearCachedModels} — wiping the whole fetched list (hundreds
     * of models for large providers) over one bad entry. Every models UI then fell back to
     * {@code AiProviderModels.getStaticModels()}, which for several providers holds exactly one
     * verified entry — so the user watched "300 models" become "1" without any obvious cause.
     * Dropping only the offending model keeps the rest of the user's fetched list intact.
     */
    public void removeCachedModel(@NonNull AiProvider provider, @NonNull String modelId) {
        List<ModelInfo> cached = getCachedModels(provider);
        if (cached.isEmpty()) return;
        boolean removed = cached.removeIf(m -> modelId.equals(m.getId()));
        if (!removed) return;
        if (cached.isEmpty()) {
            clearCachedModels(provider);
        } else {
            setCachedModels(provider, cached);
        }
    }

    public void setSelectedModel(@NonNull AiProvider provider, @NonNull String modelId) {
        prefs.edit().putString(KEY_SELECTED_MODEL_PREFIX + provider.name(), modelId).apply();
    }

    @Nullable
    public String getSelectedModel(@NonNull AiProvider provider) {
        String saved = prefs.getString(KEY_SELECTED_MODEL_PREFIX + provider.name(), null);
        if (saved != null && !saved.isEmpty()) {
            List<String> staticList = AiProviderModels.getStaticModels(provider);
            if (staticList.isEmpty()) {
                // No curated list for this provider — trust whatever was saved.
                return saved;
            }
            // Model is in the verified static list → always valid.
            if (staticList.contains(saved)) return saved;
            // Model came from a dynamic API fetch — valid only while still in the cache.
            for (ModelInfo m : getCachedModels(provider)) {
                if (saved.equals(m.getId())) return saved;
            }
            // Saved model ID is stale (API removed it). Clear it and fall through to default.
            prefs.edit().remove(KEY_SELECTED_MODEL_PREFIX + provider.name()).apply();
        }

        switch (provider) {
            case POLLINATIONS:     return DEFAULT_POLLINATIONS_MODEL;
            case DEEPINFRA:        return DEFAULT_DEEPINFRA_MODEL;
            case GROQ:             return DEFAULT_GROQ_MODEL;
            case DEEPSEEK:         return DEFAULT_DEEPSEEK_MODEL;
            case ANTHROPIC:        return DEFAULT_ANTHROPIC_MODEL;
            case OPENAI:           return DEFAULT_OPENAI_MODEL;
            case GEMINI:           return DEFAULT_GEMINI_MODEL;
            case GOOGLE_AI_STUDIO: return DEFAULT_GOOGLE_AI_STUDIO_MODEL;
            case TOGETHER:         return DEFAULT_TOGETHER_MODEL;
            case SAMBANOVA:        return DEFAULT_SAMBANOVA_MODEL;
            case CEREBRAS:         return DEFAULT_CEREBRAS_MODEL;
            default:               return null;
        }
    }

    public void setSelectedProvider(@NonNull AiProvider provider) {
        prefs.edit().putString(KEY_SELECTED_PROVIDER, provider.name()).apply();
    }

    @NonNull
    public AiProvider getSelectedProvider() {
        String key = prefs.getString(KEY_SELECTED_PROVIDER, null);
        if (key != null) {
            AiProvider p = AiProvider.fromName(key);
            if (p != null) return p;
        }
        return AiProvider.POLLINATIONS; // Default: AirForce AI (free, no API key — works immediately out of box)
    }

    public void setSystemPrompt(@NonNull String prompt) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply();
    }

    @NonNull
    public String getSystemPrompt() {
        String base = prefs.getString(KEY_SYSTEM_PROMPT, getDefaultSystemPrompt(appContext));
        // Always append the capability manifest so the AI knows what tools it has
        return base + pro.sketchware.ai.manifest.AiCapabilityManifest.buildSystemPromptInjection();
    }

    /**
     * Default changed from 0.7f to 0f (2026 fix): 0f is the "no user override configured"
     * sentinel used everywhere in the API-client layer (see {@link pro.sketchware.ai.api.AiApiClient}).
     * Before this fix the 0.7f default made every request look "explicitly configured by the
     * user", which would have silently overridden provider-specific tuned defaults (e.g. Morph's
     * intentionally low 0.2f for precise code edits) the moment temperature-forwarding was wired up.
     * The "AI Performance Profiles" screen (Quick/Deep) still writes a real positive value once the
     * user actually interacts with it — only then does an explicit override apply.
     */
    public float getTemperature() {
        return prefs.getFloat(KEY_TEMPERATURE, 0f);
    }

    public void setTemperature(float temperature) {
        prefs.edit().putFloat(KEY_TEMPERATURE, Math.max(0f, Math.min(1f, temperature))).apply();
    }

    /**
     * Re-added 2026 (was previously deleted as "unused"): it was never dead — the
     * "AI Performance Profiles" screen in AiSettingsActivity always wrote this same
     * SharedPreferences key ("ai_max_tokens") directly; the only thing actually missing
     * was any code path that *read* it back and forwarded it to the API clients.
     * That read path is now {@link pro.sketchware.ai.api.AiClientFactory#createClient}.
     *
     * @return 0 if never configured (callers must treat 0 as "use provider default")
     */
    public int getMaxTokens() {
        return prefs.getInt(KEY_MAX_TOKENS, 0);
    }

    public void setMaxTokens(int maxTokens) {
        prefs.edit().putInt(KEY_MAX_TOKENS, Math.max(0, maxTokens)).apply();
    }

    public boolean isAutoFixOnError() {
        return prefs.getBoolean(KEY_AUTO_FIX_ON_ERROR, true);
    }

    /**
     * Added Phase 3 (ai.engine.budget). Max estimated INPUT tokens allowed in a
     * single outgoing payload before {@link pro.sketchware.ai.engine.budget.TokenBudgetChecker}
     * blocks the request. Separate from {@link #getMaxTokens()}, which caps
     * generated OUTPUT tokens, not the request payload.
     *
     * @return configured limit, or {@link pro.sketchware.ai.engine.budget.TokenBudgetChecker#DEFAULT_MAX_PAYLOAD_TOKENS}
     *         if never configured.
     */
    public int getMaxPayloadTokens() {
        return prefs.getInt(KEY_MAX_PAYLOAD_TOKENS,
                pro.sketchware.ai.engine.budget.TokenBudgetChecker.DEFAULT_MAX_PAYLOAD_TOKENS);
    }

    public void setMaxPayloadTokens(int maxPayloadTokens) {
        prefs.edit().putInt(KEY_MAX_PAYLOAD_TOKENS, Math.max(0, maxPayloadTokens)).apply();
    }

    public void setAutoFixOnError(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_FIX_ON_ERROR, enabled).apply();
    }

    // ── Morph Layout Refinement ─────────────────────────────────────────────

    /** Returns the Morph API key from standard provider storage (with legacy fallback). */
    public String getMorphApiKey() {
        String standard = getApiKey(AiProvider.MORPH);
        if (standard != null && !standard.isEmpty()) return standard;
        // Legacy fallback: migrate old key to new standard storage
        String legacy = prefs.getString(KEY_MORPH_API_KEY, "");
        if (!legacy.isEmpty()) {
            setApiKey(AiProvider.MORPH, legacy);
            prefs.edit().remove(KEY_MORPH_API_KEY).apply();
        }
        return legacy;
    }

    public void setMorphApiKey(@NonNull String key) {
        setApiKey(AiProvider.MORPH, key.trim());
    }

    /** True if Morph is enabled (provider enabled + has API key). */
    public boolean isMorphEnabled() {
        return isProviderEnabled(AiProvider.MORPH) && hasApiKey(AiProvider.MORPH);
    }

    // ── Hugging Face token (Phase 5.4) ──────────────────────────────────────
    //
    // Needed only for gated on-device models (see LocalModelCatalog.isGated()) — public,
    // ungated models (the non-Gemma entries) download fine without this. Kept separate from
    // the AiProvider-keyed getApiKey()/setApiKey() family since Hugging Face isn't a chat
    // provider in this codebase's AiProvider enum, just a download source for LocalModelDownloader.

    /** Returns the stored Hugging Face access token, or an empty string if none is set. */
    @NonNull
    public String getHuggingFaceToken() {
        String token = secureStore.get(KEY_HUGGING_FACE_TOKEN);
        return token != null ? token : "";
    }

    /** True if a (non-empty) Hugging Face token has been saved. */
    public boolean hasHuggingFaceToken() {
        return !getHuggingFaceToken().isEmpty();
    }

    public void setHuggingFaceToken(@NonNull String token) {
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            secureStore.remove(KEY_HUGGING_FACE_TOKEN);
        } else {
            secureStore.put(KEY_HUGGING_FACE_TOKEN, trimmed);
        }
    }

    /** True if Morph should automatically refine AI-generated layouts. */
    public boolean isMorphForLayoutEnabled() {
        return isMorphEnabled()
                && prefs.getBoolean(KEY_MORPH_FOR_LAYOUT, false);
    }

    // ── Profile-Specific Settings ───────────────────────────────────────────

    public void setProfileModel(String profile, String modelId) {
        prefs.edit().putString(KEY_PROFILE_MODEL_PREFIX + profile, modelId).apply();
    }

    @Nullable
    public String getProfileModel(String profile) {
        return prefs.getString(KEY_PROFILE_MODEL_PREFIX + profile, null);
    }

    public void setProfileProvider(String profile, AiProvider provider) {
        prefs.edit().putString(KEY_PROFILE_PROVIDER_PREFIX + profile, provider.name()).apply();
    }

    @Nullable
    public AiProvider getProfileProvider(String profile) {
        String name = prefs.getString(KEY_PROFILE_PROVIDER_PREFIX + profile, null);
        if (name != null) {
            AiProvider p = AiProvider.fromName(name);
            if (p != null) return p;
        }
        return null;
    }

    // ── Agent execution settings ────────────────────────────────────────────

    private static final String KEY_PULSE_STEPS       = "ai_pulse_steps";
    private static final String KEY_REQUEST_TIMEOUT   = "ai_request_timeout_s";

    /**
     * Number of tool calls between pulse confirmation dialogs (default 6).
     * Higher values = fewer interruptions; lower = more control.
     */
    public int getPulseSteps() {
        return prefs.getInt(KEY_PULSE_STEPS, 6);
    }

    public void setPulseSteps(int steps) {
        prefs.edit().putInt(KEY_PULSE_STEPS, Math.max(1, steps)).apply();
    }

    /** Per-model request timeout in seconds (default 120). */
    public int getRequestTimeoutSecs() {
        return prefs.getInt(KEY_REQUEST_TIMEOUT, 120);
    }

    public void setRequestTimeoutSecs(int secs) {
        prefs.edit().putInt(KEY_REQUEST_TIMEOUT, Math.max(30, secs)).apply();
    }

    // ── Active profile ────────────────────────────────────────────────────────

    private static final String KEY_AI_PROFILE = "ai_profile";

    /**
     * Returns the currently active profile key ("QUICK" or "DEEP").
     * Matches the constant stored by AiSettingsActivity's profile toggle.
     */
    public String getActiveProfile() {
        return prefs.getString(KEY_AI_PROFILE, "QUICK");
    }

    // ── Draft message persistence ─────────────────────────────────────────────

    private static final String KEY_DRAFT_PREFIX = "ai_draft_";

    /** Saves the user's in-progress input for a conversation. Pass null/empty to clear. */
    public void saveDraft(@NonNull String conversationId, @Nullable String text) {
        if (text == null || text.trim().isEmpty()) {
            prefs.edit().remove(KEY_DRAFT_PREFIX + conversationId).apply();
        } else {
            prefs.edit().putString(KEY_DRAFT_PREFIX + conversationId, text).apply();
        }
    }

    /** Returns the saved draft for a conversation, or null if none. */
    @Nullable
    public String getDraft(@NonNull String conversationId) {
        return prefs.getString(KEY_DRAFT_PREFIX + conversationId, null);
    }

    /** Removes the saved draft for a conversation (call after the message is sent). */
    public void clearDraft(@NonNull String conversationId) {
        prefs.edit().remove(KEY_DRAFT_PREFIX + conversationId).apply();
    }
}
