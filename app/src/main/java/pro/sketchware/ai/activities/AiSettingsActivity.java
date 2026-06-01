// nikit overhaul — Tasks 1 & 2 — 2026-05
package pro.sketchware.ai.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;

import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

import pro.sketchware.R;
import pro.sketchware.ai.adapters.AiProviderAdapter;
import pro.sketchware.ai.api.AiApiClient;
import pro.sketchware.ai.api.AiClientFactory;
import pro.sketchware.ai.core.AiHealthMonitor;
import pro.sketchware.ai.core.CircuitBreaker;
import pro.sketchware.ai.core.ToolTelemetry;
import pro.sketchware.ai.diagnostics.AiDiagnosticRunner;
import pro.sketchware.ai.diagnostics.AiSessionLogger;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.databinding.ActivityAiSettingsBinding;

import android.os.CountDownTimer;

public class AiSettingsActivity extends AppCompatActivity {

    // ── API Key URLs ──────────────────────────────────────────────────────────
    private static final String URL_GEMINI           = "https://aistudio.google.com/app/apikey";
    private static final String URL_OPENAI           = "https://platform.openai.com/api-keys";
    private static final String URL_ANTHROPIC        = "https://console.anthropic.com/settings/keys";
    private static final String URL_DEEPSEEK         = "https://platform.deepseek.com/api_keys";
    private static final String URL_XAI              = "https://console.x.ai/";
    private static final String URL_NVIDIA           = "https://build.nvidia.com/explore/discover";
    private static final String URL_OPENROUTER       = "https://openrouter.ai/keys";
    private static final String URL_DEEPINFRA        = "https://deepinfra.com/dash/api_keys";
    private static final String URL_GROQ             = "https://console.groq.com/keys";
    private static final String URL_TOGETHER         = "https://api.together.ai/settings/api-keys";
    private static final String URL_HUGGINGFACE      = "https://huggingface.co/settings/tokens";
    private static final String URL_CEREBRAS         = "https://cloud.cerebras.ai/platform";
    private static final String URL_GOOGLE_AI_STUDIO = "https://aistudio.google.com/app/apikey";
    private static final String URL_SAMBANOVA        = "https://cloud.sambanova.ai/apis";
    // ── New providers ─────────────────────────────────────────────────────────
    private static final String URL_MISTRAL          = "https://console.mistral.ai/api-keys";
    private static final String URL_COHERE           = "https://dashboard.cohere.com/api-keys";
    private static final String URL_HYPERBOLIC       = "https://app.hyperbolic.ai/settings";
    private static final String URL_KLUSTER          = "https://platform.kluster.ai/apikeys";
    private static final String URL_OVH              = "https://horizon.cloud.ovh.net/";
    private static final String URL_CLOUDFLARE       = "https://dash.cloudflare.com/profile/api-tokens";
    private static final String URL_GITHUB_MODELS    = "https://github.com/settings/tokens";
    private static final String URL_LAMBDA           = "https://cloud.lambdalabs.com/api-keys";
    private static final String URL_SCALEWAY         = "https://console.scaleway.com/iam/api-keys";
    private static final String URL_FIREWORKS        = "https://fireworks.ai/account/api-keys";
    private static final String URL_NOVITA           = "https://novita.ai/settings/key-management";
    private static final String URL_AIRFORCE         = "https://api.airforce";

    private static final String PREF_ENABLED    = "provider_enabled_";
    private static final String PREF_AI_PROFILE = "ai_profile";
    private static final String PROFILE_QUICK    = "QUICK";
    private static final String PROFILE_DEEP     = "DEEP";

    private static final float QUICK_TEMPERATURE = 0.8f;
    private static final int   QUICK_MAX_TOKENS  = 2048;
    private static final float DEEP_TEMPERATURE  = 0.3f;
    private static final int   DEEP_MAX_TOKENS   = 8192;

    private static final int REQUEST_EXPORT = 9001;
    private static final int REQUEST_IMPORT = 9002;

    private ActivityAiSettingsBinding binding;
    private AiPreferences             preferences;
    private AiProviderAdapter         providerAdapter;
    private String                    defaultSystemPrompt;
    private final ExecutorService     executor = Executors.newCachedThreadPool();

    /** Active failover countdown timer */
    private CountDownTimer failoverCountdown;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityAiSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferences         = AiPreferences.getInstance(this);
        defaultSystemPrompt = preferences.getSystemPrompt();

        // Migrate legacy provider settings on startup
        migrateLegacyProviderIfNeeded();

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupProvidersRecyclerView();
        setupAiProfiles();
        setupFailoverBanner();
        setupLayoutGenerationSettings();
        setupMorphSettings();
        setupSystemPrompt();
        setupStreamingSettings();
        setupAdvancedSettings();
        setupToolTelemetry();
        setupHealthDashboard();
        setupDiagnosticRunner();
        setupDiagnosticLog();
        handleIncomingIntent();
    }

    // ── Provider migration + model validation ────────────────────────────────

    private void migrateLegacyProviderIfNeeded() {
        AiProvider saved = preferences.getSelectedProvider();
        if (saved == null) return;
        String savedModel = preferences.getSelectedModel(saved);
        String defModel   = pro.sketchware.ai.models.AiProviderModels.getDefaultModel(saved);
        if (!pro.sketchware.ai.models.AiProviderModels.isModelValidForProvider(saved, savedModel)
                && !defModel.isEmpty()) {
            preferences.setSelectedModel(saved, defModel);
        }
    }

    // ── RecyclerView providers ────────────────────────────────────────────────

    private void setupProvidersRecyclerView() {
        List<AiProviderAdapter.ProviderState> states = new ArrayList<>();
        for (AiProvider p : AiProvider.values()) {
            // Default-enabled on first install: CHUTES (free, no key), plus GOOGLE_AI_STUDIO
            // and SAMBANOVA for failover coverage. Groq is NOT default to avoid rate-limit
            // exhaustion for users who haven't yet entered an API key.
            boolean defaultEnabled = (p == AiProvider.CHUTES
                    || p == AiProvider.GOOGLE_AI_STUDIO
                    || p == AiProvider.SAMBANOVA);
            boolean enabled = preferences.prefs().getBoolean(PREF_ENABLED + p.name(), defaultEnabled);
            String  key     = p.requiresApiKey() ? preferences.getApiKey(p) : "";
            String  count   = buildModelCountText(p);
            states.add(new AiProviderAdapter.ProviderState(p, enabled, key, count));
        }

        providerAdapter = new AiProviderAdapter(new AiProviderAdapter.ProviderCallback() {
            @Override
            public void onToggle(AiProvider provider, boolean enabled) {
                preferences.prefs().edit()
                        .putBoolean(PREF_ENABLED + provider.name(), enabled).apply();
            }

            @Override
            public void onKeyChanged(AiProvider provider, String key) {
                saveKey(provider, key);
            }

            @Override
            public void onGetKey(AiProvider provider) {
                openUrl(getUrlFor(provider));
            }

            @Override
            public void onRefresh(AiProvider provider) {
                fetchModels(provider);
            }
        });

        binding.providersRecycler.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        binding.providersRecycler.setAdapter(providerAdapter);
        binding.providersRecycler.setHasFixedSize(false);
        binding.providersRecycler.setNestedScrollingEnabled(false);

        providerAdapter.setStates(states);
    }

    private String buildModelCountText(AiProvider p) {
        List<ModelInfo> cached = preferences.getCachedModels(p);
        if (cached != null && !cached.isEmpty())
            return cached.size() + " models loaded";
        // Task 1: If no cached models, show built-in static count so the card
        // doesn't appear empty for new providers (KLUSTER, CLOUDFLARE, etc.)
        List<String> staticModels = pro.sketchware.ai.models.AiProviderModels.getStaticModels(p);
        if (!staticModels.isEmpty())
            return staticModels.size() + " models (built-in)";
        if (p.requiresApiKey())
            return preferences.hasApiKey(p) ? "No models \u2014 tap \u21bb" : "Enter API key";
        return "Free \u2014 tap \u21bb to load models";
    }

    // ── Fetch models ──────────────────────────────────────────────────────────

    private void fetchModels(AiProvider provider) {
        if (provider.requiresApiKey()) {
            String key = preferences.getApiKey(provider);
            if (key == null || key.isEmpty()) {
                providerAdapter.setModelCount(provider, "\u26a0\ufe0f Enter API key first");
                return;
            }
        }
        providerAdapter.setModelCount(provider, "Fetching models\u2026");
        executor.execute(() -> {
            try {
                String key = provider.requiresApiKey() ? preferences.getApiKey(provider) : "";
                AiApiClient client = AiClientFactory.createClient(this, provider, key);
                if (client == null) return;
                List<ModelInfo> all = client.fetchModels();
                List<ModelInfo> valid = validateModels(client, provider, all);
                preferences.setCachedModels(provider, valid);
                client.shutdown();
                int total = all.size(), ok = valid.size();
                String label = ok == total
                        ? ok + " models \u2705"
                        : ok + "/" + total + " working \u2705";
                runOnUiThread(() -> providerAdapter.setModelCount(provider, label));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                runOnUiThread(() -> providerAdapter.setModelCount(provider, "\u274c " + msg));
            }
        });
    }

    private List<ModelInfo> validateModels(AiApiClient client, AiProvider provider,
                                           List<ModelInfo> all) {
        // Skip ping for large or free providers — too many models / no key to test.
        // Also skip NVIDIA and LLM7/Pollinations: NVIDIA returns many models but most fail
        // a "hi" ping due to specific model requirements; LLM7/Pollinations are free proxies
        // with rate limits that make per-model validation impractical.
        if (all.size() > 25
                || provider == AiProvider.OPENROUTER
                || provider == AiProvider.DEEPINFRA
                || provider == AiProvider.TOGETHER
                || provider == AiProvider.SAMBANOVA
                || provider == AiProvider.HUGGINGFACE
                || provider == AiProvider.CHUTES
                || provider == AiProvider.CLOUDFLARE
                || provider == AiProvider.GITHUB_MODELS
                || provider == AiProvider.FIREWORKS
                || provider == AiProvider.NOVITA
                || provider == AiProvider.KLUSTER
                || provider == AiProvider.NVIDIA
                || provider == AiProvider.LLM7
                || provider == AiProvider.POLLINATIONS) {
            return all;
        }

        List<ModelInfo> valid = new java.util.ArrayList<>();
        List<pro.sketchware.ai.models.ChatMessage> ping =
                java.util.Collections.singletonList(
                        pro.sketchware.ai.models.ChatMessage.userMessage(null, "hi"));

        for (ModelInfo model : all) {
            try {
                final boolean[] ok = {false};
                final java.util.concurrent.CountDownLatch latch =
                        new java.util.concurrent.CountDownLatch(1);

                client.sendChatRequest(ping, model.getId(), "Say ok", (Object) null,
                        new pro.sketchware.ai.api.StreamingResponseHandler() {
                            @Override
                            public void onChunk(String textDelta) { ok[0] = true; }
                            @Override
                            public void onToolCall(pro.sketchware.ai.models.ToolCall toolCall) {
                                ok[0] = true;
                            }
                            @Override
                            public void onComplete(String fullResponse) { latch.countDown(); }
                            @Override
                            public void onError(String error) {
                                String lo = error.toLowerCase(java.util.Locale.ROOT);
                                if (lo.contains("429") || lo.contains("rate")
                                        || lo.contains("500") || lo.contains("502")
                                        || lo.contains("503") || lo.contains("overload")
                                        || lo.contains("timed out")
                                        || lo.contains("timeout")) {
                                    ok[0] = true;
                                }
                                latch.countDown();
                            }
                        });

                latch.await(12, java.util.concurrent.TimeUnit.SECONDS);
                if (ok[0]) valid.add(model);

            } catch (Exception ignored) {
                valid.add(model);
            }
        }
        return valid.isEmpty() ? all : valid;
    }

    // ── API Key helpers ───────────────────────────────────────────────────────

    private void saveKey(AiProvider p, String key) {
        if (!p.requiresApiKey()) return;
        String ex = preferences.getApiKey(p);
        if (key.equals(ex != null ? ex : "")) return;
        if (key.isEmpty()) {
            preferences.clearApiKey(p);
            preferences.clearCachedModels(p);
            providerAdapter.setModelCount(p, buildModelCountText(p));
        } else {
            preferences.setApiKey(p, key);
            fetchModels(p);
        }
    }

    private String getUrlFor(AiProvider p) {
        switch (p) {
            case GEMINI:           return URL_GEMINI;
            case OPENAI:           return URL_OPENAI;
            case ANTHROPIC:        return URL_ANTHROPIC;
            case DEEPSEEK:         return URL_DEEPSEEK;
            case XAI_GROK:         return URL_XAI;
            case NVIDIA:           return URL_NVIDIA;
            case OPENROUTER:       return URL_OPENROUTER;
            case DEEPINFRA:        return URL_DEEPINFRA;
            case GROQ:             return URL_GROQ;
            case TOGETHER:         return URL_TOGETHER;
            case HUGGINGFACE:      return URL_HUGGINGFACE;
            case CEREBRAS:         return URL_CEREBRAS;
            case GOOGLE_AI_STUDIO: return URL_GOOGLE_AI_STUDIO;
            case SAMBANOVA:        return URL_SAMBANOVA;
            // New providers
            case MISTRAL:          return URL_MISTRAL;
            case COHERE:           return URL_COHERE;
            case HYPERBOLIC:       return URL_HYPERBOLIC;
            case KLUSTER:          return URL_KLUSTER;
            case OVH:              return URL_OVH;
            case CLOUDFLARE:       return URL_CLOUDFLARE;
            case GITHUB_MODELS:    return URL_GITHUB_MODELS;
            case LAMBDA:           return URL_LAMBDA;
            case SCALEWAY:         return URL_SCALEWAY;
            case FIREWORKS:        return URL_FIREWORKS;
            case NOVITA:           return URL_NOVITA;
            case CHUTES:           return URL_AIRFORCE;
            case MORPH:            return "https://morphllm.com/dashboard/api-keys";
            default:               return "";
        }
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception ignored) {}
    }

    // ── AI Performance Profiles ───────────────────────────────────────────────

    private void setupAiProfiles() {
        String saved = preferences.prefs().getString(PREF_AI_PROFILE, PROFILE_QUICK);
        if (PROFILE_DEEP.equals(saved)) {
            binding.profileToggleGroup.check(R.id.btn_profile_deep);
            binding.profileDescription.setText(
                    "Deep mode for thorough analysis and higher quality responses");
        } else {
            binding.profileToggleGroup.check(R.id.btn_profile_quick);
            binding.profileDescription.setText(
                    "Quick mode for faster responses and lower resource usage");
        }

        binding.profileToggleGroup.addOnButtonCheckedListener(
                (group, checkedId, isChecked) -> {
                    if (!isChecked) return;
                    if (checkedId == R.id.btn_profile_deep) {
                        preferences.prefs().edit()
                                .putString(PREF_AI_PROFILE, PROFILE_DEEP)
                                .putFloat("ai_temperature", DEEP_TEMPERATURE)
                                .putInt("ai_max_tokens", DEEP_MAX_TOKENS)
                                .apply();
                        binding.profileDescription.setText(
                                "Deep mode for thorough analysis and higher quality responses");
                    } else {
                        preferences.prefs().edit()
                                .putString(PREF_AI_PROFILE, PROFILE_QUICK)
                                .putFloat("ai_temperature", QUICK_TEMPERATURE)
                                .putInt("ai_max_tokens", QUICK_MAX_TOKENS)
                                .apply();
                        binding.profileDescription.setText(
                                "Quick mode for faster responses and lower resource usage");
                    }
                });

        binding.btnProfileQuick.setOnLongClickListener(v -> {
            showConfigureProfileDialog(PROFILE_QUICK);
            return true;
        });
        binding.btnProfileDeep.setOnLongClickListener(v -> {
            showConfigureProfileDialog(PROFILE_DEEP);
            return true;
        });
    }

    private void showConfigureProfileDialog(String profile) {
        boolean isQuick = PROFILE_QUICK.equals(profile);
        String title    = isQuick ? "Configure Quick Profile" : "Configure Deep Profile";

        float   curTemp   = preferences.prefs().getFloat("ai_temperature",
                isQuick ? QUICK_TEMPERATURE : DEEP_TEMPERATURE);
        int     curTokens = preferences.prefs().getInt("ai_max_tokens",
                isQuick ? QUICK_MAX_TOKENS : DEEP_MAX_TOKENS);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        android.widget.EditText tempInput = new android.widget.EditText(this);
        tempInput.setHint("Temperature (0.0 \u2013 1.0)");
        tempInput.setText(String.valueOf(curTemp));
        tempInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(tempInput);

        android.widget.EditText tokensInput = new android.widget.EditText(this);
        tokensInput.setHint("Max tokens");
        tokensInput.setText(String.valueOf(curTokens));
        tokensInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(tokensInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(layout)
                .setPositiveButton(R.string.common_word_save, (d, w) -> {
                    try {
                        float t = Float.parseFloat(tempInput.getText().toString().trim());
                        int   k = Integer.parseInt(tokensInput.getText().toString().trim());
                        preferences.prefs().edit()
                                .putFloat("ai_temperature", t)
                                .putInt("ai_max_tokens", k)
                                .apply();
                        Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid value", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    // ── Failover Banner ───────────────────────────────────────────────────────

    private void setupFailoverBanner() {
        binding.failoverBanner.setVisibility(View.GONE);
        binding.btnCancelFailover.setOnClickListener(v -> dismissFailoverBanner());
    }

    public void showFailoverBanner(String fromProvider, String toProvider, int countdownSecs) {
        runOnUiThread(() -> {
            binding.failoverBanner.setVisibility(View.VISIBLE);
            if (failoverCountdown != null) failoverCountdown.cancel();
            failoverCountdown = new CountDownTimer(countdownSecs * 1000L, 1000) {
                @Override public void onTick(long ms) {
                    binding.failoverText.setText(
                            "\u26a0\ufe0f Failing over from " + fromProvider +
                            " \u2192 " + toProvider + " in " + (ms / 1000) + "s");
                }
                @Override public void onFinish() {
                    binding.failoverText.setText("\u2705 Switched to " + toProvider);
                }
            };
            failoverCountdown.start();
        });
    }

    private void dismissFailoverBanner() {
        if (failoverCountdown != null) { failoverCountdown.cancel(); failoverCountdown = null; }
        binding.failoverBanner.setVisibility(View.GONE);
    }

    // ── Layout Generation Provider ─────────────────────────────────────────────

    private void setupLayoutGenerationSettings() {
        java.util.List<String> providerLabels = new java.util.ArrayList<>();
        providerLabels.add("Same as Chat (default)");
        for (AiProvider p : AiProvider.values()) {
            providerLabels.add(p.getDisplayName());
        }

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, providerLabels);
        binding.dropdownLayoutProvider.setAdapter(adapter);

        String saved = preferences.prefs().getString(
                AiPreferences.KEY_LAYOUT_AI_PROVIDER, "");
        if (saved.isEmpty()) {
            binding.dropdownLayoutProvider.setText(providerLabels.get(0), false);
        } else {
            AiProvider savedProvider = AiProvider.fromName(saved);
            binding.dropdownLayoutProvider.setText(
                    savedProvider != null ? savedProvider.getDisplayName()
                            : providerLabels.get(0), false);
        }
        updateLayoutProviderHint(saved);

        binding.dropdownLayoutProvider.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                preferences.prefs().edit()
                        .remove(AiPreferences.KEY_LAYOUT_AI_PROVIDER)
                        .apply();
                updateLayoutProviderHint("");
            } else {
                AiProvider selected = AiProvider.values()[position - 1];
                preferences.prefs().edit()
                        .putString(AiPreferences.KEY_LAYOUT_AI_PROVIDER, selected.name())
                        .apply();
                updateLayoutProviderHint(selected.name());
            }
        });
    }

    private void updateLayoutProviderHint(String providerName) {
        if (providerName.isEmpty() || providerName.equalsIgnoreCase("CHUTES")) {
            binding.tvLayoutProviderHint.setText(
                    "\u2605 AirForce AI \u2014 completely free, no API key required");
            binding.tvLayoutProviderHint.setVisibility(View.VISIBLE);
        } else if (providerName.equalsIgnoreCase("GROQ")) {
            binding.tvLayoutProviderHint.setText(
                    "\u2605 Groq \u2014 fast LPU inference, 14,400 req/day free tier");
            binding.tvLayoutProviderHint.setVisibility(View.VISIBLE);
        } else if (providerName.equalsIgnoreCase("SAMBANOVA")) {
            binding.tvLayoutProviderHint.setText(
                    "\u2605 SambaNova \u2014 free fast inference for Llama & Gemma models");
            binding.tvLayoutProviderHint.setVisibility(View.VISIBLE);
        } else if (providerName.equalsIgnoreCase("GOOGLE_AI_STUDIO")) {
            binding.tvLayoutProviderHint.setText(
                    "\u2605 Google AI Studio \u2014 free Gemini Flash access, generous quota");
            binding.tvLayoutProviderHint.setVisibility(View.VISIBLE);
        } else {
            binding.tvLayoutProviderHint.setVisibility(View.GONE);
        }
    }

    // ── Morph Layout Refinement ─────────────────────────────────────────────

    private void setupMorphSettings() {
        binding.switchMorphForLayout.setChecked(
                preferences.prefs().getBoolean(AiPreferences.KEY_MORPH_FOR_LAYOUT, false));
        binding.switchMorphForLayout.setOnCheckedChangeListener((btn, checked) ->
                preferences.prefs().edit()
                        .putBoolean(AiPreferences.KEY_MORPH_FOR_LAYOUT, checked)
                        .apply());
    }

    private void setupSystemPrompt() {
        String cur = preferences.getSystemPrompt();
        if (!cur.equals(defaultSystemPrompt)) binding.inputSystemPrompt.setText(cur);
        binding.inputSystemPrompt.setOnFocusChangeListener((v, f) -> { if (!f) saveSystemPrompt(); });
        binding.btnResetSystemPrompt.setOnClickListener(v -> {
            preferences.setSystemPrompt(defaultSystemPrompt);
            binding.inputSystemPrompt.setText("");
            Toast.makeText(this, "System prompt reset to Sketchware default",
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void saveSystemPrompt() {
        String text = binding.inputSystemPrompt.getText() != null
                ? binding.inputSystemPrompt.getText().toString().trim() : "";
        if (!text.isEmpty()) preferences.setSystemPrompt(text);
    }

    // ── Intent Handling ───────────────────────────────────────────────────────

    private void handleIncomingIntent() {
        Intent intent = getIntent();
        if (intent == null) return;
        String action = intent.getStringExtra("ACTION");
        if ("DOWNLOAD_MODEL".equals(action)) {
            String url  = intent.getStringExtra("URL");
            String name = intent.getStringExtra("NAME");
            String id   = intent.getStringExtra("ID");
            if (url != null && name != null && id != null) {
                binding.getRoot().post(() -> startModelDownload(url, name, id));
            }
        }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    private void startModelDownload(String url, String modelName, String modelId) {
        binding.globalDownloadProgress.setVisibility(View.VISIBLE);
        binding.globalDownloadProgress.setProgressCompat(0, false);

        pro.sketchware.util.LlmDownloader.downloadModel(
                this, url, modelName + ".bin",
                new pro.sketchware.util.LlmDownloader.DownloadListener() {
                    @Override
                    public void onProgress(int progress) {
                        runOnUiThread(() ->
                                binding.globalDownloadProgress.setProgressCompat(progress, true));
                    }
                    @Override
                    public void onSuccess(java.io.File file) {
                        runOnUiThread(() -> {
                            binding.globalDownloadProgress.setVisibility(View.GONE);
                            Toast.makeText(AiSettingsActivity.this,
                                    "\u2705 Download complete: " + modelName,
                                    Toast.LENGTH_LONG).show();

                        });
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            binding.globalDownloadProgress.setVisibility(View.GONE);
                            Toast.makeText(AiSettingsActivity.this,
                                    "\u274c Download failed: " + error,
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    // ── Streaming & Timeouts ──────────────────────────────────────────────────

    private void setupStreamingSettings() {
        int savedTimeout = preferences.getRequestTimeoutSecs();
        binding.sliderTimeout.setValue(
                Math.max(30, Math.min(300, (savedTimeout / 10) * 10))); // snap to step
        updateTimeoutLabel(savedTimeout);

        binding.sliderTimeout.addOnChangeListener((slider, value, fromUser) -> {
            int secs = (int) value;
            preferences.setRequestTimeoutSecs(secs);
            updateTimeoutLabel(secs);
        });
    }

    private void updateTimeoutLabel(int secs) {
        binding.tvTimeoutLabel.setText("Request timeout: " + secs + "s");
    }

    // ── Advanced Execution ────────────────────────────────────────────────────

    private void setupAdvancedSettings() {
        int savedSteps = preferences.getPulseSteps();
        binding.sliderPulseSteps.setValue(Math.max(1, Math.min(20, savedSteps)));
        updatePulseStepsLabel(savedSteps);

        binding.sliderPulseSteps.addOnChangeListener((slider, value, fromUser) -> {
            int steps = (int) value;
            preferences.setPulseSteps(steps);
            updatePulseStepsLabel(steps);
        });
    }

    private void updatePulseStepsLabel(int steps) {
        binding.tvPulseStepsLabel.setText(
                "Pulse confirmation every: " + steps + " tool call" + (steps == 1 ? "" : "s"));
    }

    // ── Full Diagnostic Runner ────────────────────────────────────────────────

    private AiDiagnosticRunner diagnosticRunner;
    private java.io.File       lastDiagnosticReport;

    private void setupDiagnosticRunner() {
        diagnosticRunner = new AiDiagnosticRunner(this);

        AiDiagnosticRunner.ProgressCallback cb = new AiDiagnosticRunner.ProgressCallback() {
            @Override
            public void onStatusUpdate(String status, int done, int total) {
                runOnUiThread(() -> {
                    binding.tvDiagnosticStatus.setVisibility(android.view.View.VISIBLE);
                    binding.tvDiagnosticStatus.setText(status);
                    if (total > 0) {
                        binding.pbDiagnosticProgress.setVisibility(android.view.View.VISIBLE);
                        binding.pbDiagnosticProgress.setProgress((int) (done * 100L / total));
                    }
                });
            }
            @Override
            public void onComplete(java.io.File report) {
                lastDiagnosticReport = report;
                runOnUiThread(() -> {
                    setDiagnosticRunning(false);
                    binding.tvDiagnosticStatus.setText("✅ Done — " + report.getName());
                    binding.btnShareDiagnosticReport.setVisibility(android.view.View.VISIBLE);
                    Toast.makeText(AiSettingsActivity.this,
                            "Report saved: " + report.getName(), Toast.LENGTH_LONG).show();
                });
            }
            @Override
            public void onCancelled() {
                runOnUiThread(() -> {
                    setDiagnosticRunning(false);
                    binding.tvDiagnosticStatus.setText("Cancelled.");
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setDiagnosticRunning(false);
                    binding.tvDiagnosticStatus.setText("❌ " + message);
                    Toast.makeText(AiSettingsActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        };

        binding.btnRunModelTests.setOnClickListener(v -> {
            setDiagnosticRunning(true);
            diagnosticRunner.runModelTests(cb);
        });

        binding.btnRunToolTests.setOnClickListener(v -> {
            setDiagnosticRunning(true);
            diagnosticRunner.runToolTests(cb);
        });

        binding.btnRunAllDiagnostics.setOnClickListener(v -> {
            setDiagnosticRunning(true);
            diagnosticRunner.runAll(cb);
        });

        binding.btnCancelDiagnostic.setOnClickListener(v -> diagnosticRunner.cancel());

        binding.btnShareDiagnosticReport.setOnClickListener(v -> shareReport(lastDiagnosticReport));
    }

    private void setDiagnosticRunning(boolean running) {
        binding.pbDiagnosticProgress.setVisibility(running
                ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.tvDiagnosticStatus.setVisibility(running
                ? android.view.View.VISIBLE : android.view.View.VISIBLE);
        binding.btnCancelDiagnostic.setVisibility(running
                ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.btnRunModelTests.setEnabled(!running);
        binding.btnRunToolTests.setEnabled(!running);
        binding.btnRunAllDiagnostics.setEnabled(!running);
        if (running) binding.btnShareDiagnosticReport.setVisibility(android.view.View.GONE);
    }

    private void shareReport(java.io.File report) {
        if (report == null || !report.exists()) {
            Toast.makeText(this, "No report yet. Run a test first.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".provider", report);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share diagnostic report"));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot share: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ── Diagnostic Session Log ────────────────────────────────────────────────

    private void setupDiagnosticLog() {
        AiSessionLogger logger = AiSessionLogger.getInstance(this);
        binding.tvLogPath.setText(logger.getCurrentLogPath());

        updateLoggingToggleLabel(logger);
        binding.btnToggleLogging.setOnClickListener(v -> {
            logger.setEnabled(!logger.isEnabled());
            updateLoggingToggleLabel(logger);
        });

        binding.btnShareLog.setOnClickListener(v -> {
            java.io.File[] logs = logger.listLogs();
            if (logs.length == 0) {
                Toast.makeText(this, "No log file yet. Start a chat first.", Toast.LENGTH_SHORT).show();
                return;
            }
            java.io.File latest = logs[0];
            try {
                android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        this, getPackageName() + ".provider", latest);
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "Share AI session log"));
            } catch (Exception e) {
                Toast.makeText(this, "Cannot share: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateLoggingToggleLabel(AiSessionLogger logger) {
        binding.btnToggleLogging.setText(logger.isEnabled() ? "Logging: ON" : "Logging: OFF");
    }

    // ── Tool Telemetry ────────────────────────────────────────────────────────

    private void setupToolTelemetry() {
        refreshToolTelemetry();
        ToolTelemetry.getInstance().setListener(this::refreshToolTelemetry);
        binding.btnResetTelemetry.setOnClickListener(v -> {
            ToolTelemetry.getInstance().reset();
            Toast.makeText(this, "Tool metrics reset", Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshToolTelemetry() {
        binding.tvToolTelemetry.setText(
                pro.sketchware.ai.core.ToolTelemetry.getInstance().buildReport());
    }

    // ── AI Health Dashboard ───────────────────────────────────────────────────

    private void setupHealthDashboard() {
        refreshHealthReport();

        AiHealthMonitor.getInstance().setListener(this::refreshHealthReport);

        binding.btnResetHealth.setOnClickListener(v -> {
            AiHealthMonitor.getInstance().reset();
            CircuitBreaker.getInstance().resetAll();
            refreshHealthReport();
            Toast.makeText(this, "Health metrics reset", Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshHealthReport() {
        String report = AiHealthMonitor.getInstance().buildDiagnosticReport(this);
        binding.tvHealthReport.setText(report);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onPause() {
        super.onPause();
        saveSystemPrompt();
        // Keys are saved immediately via AiProviderAdapter.onKeyChanged callbacks
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AiHealthMonitor.getInstance().setListener(null);
        ToolTelemetry.getInstance().setListener(null);
        dismissFailoverBanner();
        executor.shutdownNow();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String t(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
