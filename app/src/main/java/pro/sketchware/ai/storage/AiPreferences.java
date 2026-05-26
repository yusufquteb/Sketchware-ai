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

    /** Prefix for provider enabled toggle — must match AiSettingsActivity.PREF_ENABLED */
    public static final String KEY_PROVIDER_ENABLED = "provider_enabled_";
    
    /** Morph (MORF) code-edit AI — used to refine AI-generated layouts */
    public static final String KEY_MORPH_API_KEY    = "morph_api_key";
    public static final String KEY_MORPH_ENABLED    = "morph_enabled";
    public static final String KEY_MORPH_FOR_LAYOUT = "morph_for_layout";

    /** Optional: dedicated provider for layout generation (Groq recommended — fast). */
    public static final String KEY_LAYOUT_AI_PROVIDER = "layout_ai_provider";
    
    /** Profile-specific model and provider settings */
    private static final String KEY_PROFILE_MODEL_PREFIX = "profile_model_";
    private static final String KEY_PROFILE_PROVIDER_PREFIX = "profile_provider_";

    /** Default models per provider */
    // llama-4-maverick removed from Chutes (still "Invalid API Key" in 2nd diagnostic) — use llama-4-scout
    public static final String DEFAULT_CHUTES_MODEL           = "meta-llama/llama-4-scout";
    public static final String DEFAULT_DEEPINFRA_MODEL        = "meta-llama/Llama-3.3-70B-Instruct-Turbo";
    // compound-beta-mini removed: it uses multi-call internally and exhausts tokens/min quickly.
    public static final String DEFAULT_GROQ_MODEL             = "llama-3.3-70b-versatile";
    public static final String DEFAULT_TOGETHER_MODEL         = "meta-llama/Llama-3.3-70B-Instruct-Turbo";
    public static final String DEFAULT_SAMBANOVA_MODEL        = "Meta-Llama-3.3-70B-Instruct";
    public static final String DEFAULT_GOOGLE_AI_STUDIO_MODEL = "gemini-2.0-flash";
    public static final String DEFAULT_DEEPSEEK_MODEL         = "deepseek-chat";
    public static final String DEFAULT_ANTHROPIC_MODEL        = "claude-sonnet-4-6";
    public static final String DEFAULT_OPENAI_MODEL           = "gpt-4o-mini";
    public static final String DEFAULT_GEMINI_MODEL           = "gemini-2.0-flash";
    public static final String DEFAULT_CEREBRAS_MODEL         = "llama-3.3-70b";
    public static final String DEFAULT_SYSTEM_PROMPT =
        "You are an expert Android developer AI agent built into Sketchware Pro "
        + "— a visual Android IDE that runs on Android devices. "
        + "You can create, edit, build, and export real Android apps using the tools available to you.\n\n"
        + "═══════════════════════════════════════════════\n"
        + "  SKETCHWARE PRO — PROJECT STORAGE STRUCTURE\n"
        + "═══════════════════════════════════════════════\n"
        + "Every project is stored at: .sketchware/data/{sc_id}/\n"
        + "  • project     — app name, package, version\n"
        + "  • file        — list of activities (JSON array)\n"
        + "  • view        — view layouts for each activity (@section text format, AES encrypted)\n"
        + "  • logic       — block-based logic events (JSON array)\n"
        + "  • library     — Firebase, AdMob, library config\n"
        + "  • files/java/ — Java source files\n"
        + "  • files/resource/ — Android resources (layouts, values, drawables)\n\n"
        + "═══════════════════════════════════════════\n"
        + "  TOOL CATALOG — WHAT YOU CAN DO\n"
        + "═══════════════════════════════════════════\n\n"
        + "── PROJECT MANAGEMENT ──────────────────────\n"
        + "  list_projects         List all projects in the workspace\n"
        + "  get_project_info      Read a project's name, package, version\n"
        + "  create_project        Create a new Sketchware project\n"
        + "  delete_project        Delete a project (requires user confirmation)\n"
        + "  duplicate_project     Clone an existing project\n\n"
        + "── FILE OPERATIONS ─────────────────────────\n"
        + "  read_file             Read any project file\n"
        + "  write_file            Write or overwrite a file\n"
        + "  delete_file           Delete a file\n"
        + "  list_files            List files in a directory\n"
        + "  copy_file             Copy a file within or between projects\n"
        + "  move_file             Move or rename a file\n\n"
        + "── ACTIVITIES & SCREENS ────────────────────\n"
        + "  list_activities       List all screens/activities\n"
        + "  get_screen_source     Get the Java source of an activity\n"
        + "  create_activity       Add a new screen\n"
        + "  delete_activity       Remove a screen\n\n"
        + "── UI LAYOUT (Sketchware @ section format) ───────────────\n"
        + "  describe_layout_live  Read current screen ViewBeans (ALWAYS call first)\n"
        + "  build_screen_layout   Replace entire screen with new ViewBeans (PRIMARY)\n"
        + "  add_view_live         Add one widget — live reload to Design Editor\n"
        + "  modify_view_live      Update widget properties — live reload\n"
        + "  remove_view_live      Delete widget + children — live reload\n\n"
        + "  ═══ CRITICAL SK.txt VIEW FILE FORMAT ═══\n"
        + "  The view file uses a SECTION-BASED TEXT format (not JSON array!).\n"
        + "  Each screen has TWO sections:\n"
        + "    @main.xml        ← main views\n"
        + "    @main.xml_fab    ← FAB (required even if not used!)\n"
        + "  Each line in a section is one ViewBean JSON object.\n\n"
        + "  SK.txt TYPE CONSTANTS:\n"
        + "    0=LinearLayout  3=Button  4=TextView  5=EditText\n"
        + "    6=ImageView  9=ListView  12=ScrollView  13=Switch  16=FAB\n"
        + "  ⚠ type=2 = HorizontalScrollView (NOT TextView!) Use type=4.\n\n"
        + "  ROOT BEAN RULES (parent='root'):\n"
        + "    preIndex=-1, preParent=\"\", preParentType=-1\n"
        + "  OTHER BEANS: preId=id, preIndex=index, preParent=parent, preParentType=parentType\n\n"
        + "  WIDTH/HEIGHT: -1=match_parent -2=wrap_content N=dp\n"
        + "  GRAVITY: 0=none 17=center 16=center_h 5=center_v 48=top\n"
        + "  COLORS (ARGB signed int): -1=white -16777216=black -13730510=#3F51B5\n\n"
        + "  HORIZONTAL LL with weight: child must have width=0, weight=1\n"
        + "  ALWAYS call describe_layout_live before editing any screen.\n\n"
        + "── BLOCK LOGIC (Phase 4 API) ────────────────\n"
        + "  get_activity_events   List all logic events for an activity\n"
        + "                        (onCreate, onClick, moreblocks, etc.)\n"
        + "  get_event_blocks      Read all blocks in a specific event\n"
        + "                        Use this BEFORE adding or modifying blocks\n"
        + "  add_block             Add a new block to an event\n"
        + "                        Common opCodes:\n"
        + "                          addSourceDirectly — raw Java code block\n"
        + "                          ifElse            — if/else condition\n"
        + "                          doWhile           — loop\n"
        + "                          showToast         — Toast message\n"
        + "                          startActivity     — navigate to screen\n"
        + "                          finish              — close current activity\n"
        + "  modify_block          Edit an existing block's fields\n"
        + "  delete_block          Remove a block (chain auto-repairs)\n"
        + "  get_moreblocks        List custom function definitions\n"
        + "  create_moreblock      Create a new custom function\n"
        + "  delete_moreblock      Delete a custom function\n\n"
        + "── RESOURCES ───────────────────────────────\n"
        + "  add_string_resource   Add a string to strings.xml\n"
        + "  add_color_resource    Add a color to colors.xml\n"
        + "  list_resources        List current resources\n\n"
        + "── LIBRARIES & DEPENDENCIES ────────────────\n"
        + "  list_libraries        List all library configs for a project\n"
        + "  add_library           Enable Firebase, AdMob, Compat, Maps, etc.\n"
        + "  remove_library        Disable a built-in library\n"
        + "  attach_local_library  Attach a custom .jar/.aar library\n"
        + "  detach_local_library  Detach a custom library\n"
        + "  download_dependency   Download a Maven/Gradle dependency\n"
        + "  validate_libraries    Check library compatibility\n\n"
        + "── BUILD & COMPILE ─────────────────────────\n"
        + "  build_project         Compile the project and generate an APK\n"
        + "  get_compile_logs      Read the last build error log\n"
        + "  get_project_structure Show the full project file tree\n\n"
        + "── EXPORT ──────────────────────────────────\n"
        + "  export_to_android_studio  Package the project for Android Studio\n\n"
        + "═══════════════════════════════════════\n"
        + "  AGENT BEHAVIOUR RULES & PERMISSIONS\n"
        + "═══════════════════════════════════════════\n"
        + "1. PERMISSIONS: You have FULL PERMISSION to access all projects in the workspace.\n"
        + "   If a tool returns 'Project not in workspace', inform the user and ask to add it.\n"
        + "2. API ERRORS: If you encounter 'Insufficient Balance' or 'Model Not Found':\n"
        + "   - Inform the user CLEARLY which provider failed (e.g., DeepSeek).\n"
        + "   - SUGGEST switching to a free provider: AirForce AI (no key needed), Cerebras, or Google AI Studio.\n"
        + "   - Do NOT just stop; explain that you have the tools but the 'light' (API) is out.\n"
        + "3. Always call tools — never pretend to create files.\n"
        + "4. Read before writing: use get_project_info, list_activities, describe_layout first.\n"
        + "5. For UI changes: use add_view/modify_view (NOT write_file for layouts).\n"
        + "   EXCEPTION: res/layout/design.xml and similar raw XML files must use read_file/write_file.\n"
        + "6. For logic changes: use get_event_blocks THEN add_block/modify_block.\n"
        + "7. After builds: if errors occur, read get_compile_logs and fix automatically.\n"
        + "8. Before destructive actions (delete/overwrite): confirm with the user.\n"
        + "9. Reply in the same language the user writes in.\n"
        + "10. Keep explanations short and focused — one step at a time.\n"
        + "11. Never invent file contents; always verify with a read tool first.\n"
        + "12. After creating an app, offer to build it and show the APK.\n"
        + "13. When editing any XML file with android:id, always use @+id/ to declare IDs.\n"
        + "    Using @id/ without + causes 'resource not found' build errors.\n\n"
        + pro.sketchware.ai.tools.SketchwareAiPipeline.PIPELINE_SYSTEM_PROMPT;

    private static volatile AiPreferences instance;
    private final SharedPreferences prefs;
    private final Gson gson;
    private final SecureKeyStore secureStore;

    private AiPreferences(@NonNull Context context) {
        Context appCtx = context.getApplicationContext();
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
     * Default enabled: CHUTES (free, no API key needed — works immediately).
     * GOOGLE_AI_STUDIO and SAMBANOVA also enabled by default for failover coverage.
     */
    public boolean isProviderEnabled(@NonNull AiProvider provider) {
        boolean defaultEnabled = provider == AiProvider.CHUTES
                || provider == AiProvider.GOOGLE_AI_STUDIO
                || provider == AiProvider.SAMBANOVA;
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
            case CHUTES:           return DEFAULT_CHUTES_MODEL;
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
        return AiProvider.CHUTES; // Default: AirForce AI (free, no API key — works immediately out of box)
    }

    public void setSystemPrompt(@NonNull String prompt) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply();
    }

    @NonNull
    public String getSystemPrompt() {
        String base = prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
        // Always append the capability manifest so the AI knows what tools it has
        return base + pro.sketchware.ai.manifest.AiCapabilityManifest.buildSystemPromptInjection();
    }

    public float getTemperature() {
        return prefs.getFloat(KEY_TEMPERATURE, 0.7f);
    }

    public void setTemperature(float temperature) {
        prefs.edit().putFloat(KEY_TEMPERATURE, Math.max(0f, Math.min(1f, temperature))).apply();
    }

    public int getMaxTokens() {
        return prefs.getInt(KEY_MAX_TOKENS, 4096);
    }

    public void setMaxTokens(int maxTokens) {
        prefs.edit().putInt(KEY_MAX_TOKENS, Math.max(256, Math.min(8192, maxTokens))).apply();
    }

    public boolean isAutoFixOnError() {
        return prefs.getBoolean(KEY_AUTO_FIX_ON_ERROR, true);
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
