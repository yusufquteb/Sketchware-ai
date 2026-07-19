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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

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
    private static final String URL_HYPERBOLIC       = "https://app.hyperbolic.ai/settings";
    private static final String URL_KLUSTER          = "https://platform.kluster.ai/apikeys";
    private static final String URL_LAMBDA           = "https://cloud.lambdalabs.com/api-keys";
    private static final String URL_SCALEWAY         = "https://console.scaleway.com/iam/api-keys";
    private static final String URL_FIREWORKS        = "https://fireworks.ai/account/api-keys";
    private static final String URL_NOVITA           = "https://novita.ai/settings/key-management";

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
    private pro.sketchware.ai.adapters.OfflineModelAdapter offlineModelAdapter;
    private pro.sketchware.ai.offline.LocalModelManager    localModelManager;
    private pro.sketchware.ai.offline.knowledge.KnowledgeStore knowledgeStore;
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
        setupOfflineModels();
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
        setupApiKeyBackup();
        setupAdvancedSettingsToggle();
        setupOfflineModelsToggle();
        setupProvidersToggle();
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

    @SuppressWarnings("deprecation") // intentionally checks legacy LLM7/CHUTES/DEEPSEEK to exclude them
    private void setupProvidersRecyclerView() {
        List<AiProviderAdapter.ProviderState> states = new ArrayList<>();
        for (AiProvider p : AiProvider.values()) {
            if (p == AiProvider.LLM7 || p == AiProvider.CHUTES || p == AiProvider.DEEPSEEK) continue; // legacy hidden
            // Default-enabled on first install: POLLINATIONS (free, no key), plus GOOGLE_AI_STUDIO
            // and SAMBANOVA for failover coverage. Groq is NOT default to avoid rate-limit
            // exhaustion for users who haven't yet entered an API key.
            boolean defaultEnabled = (p == AiProvider.POLLINATIONS
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
        // doesn't appear empty for new providers (KLUSTER, etc.)
        List<String> staticModels = pro.sketchware.ai.models.AiProviderModels.getStaticModels(p);
        if (!staticModels.isEmpty())
            return staticModels.size() + " models (built-in)";
        if (p.requiresApiKey())
            return preferences.hasApiKey(p) ? "No models \u2014 tap \u21bb" : "Enter API key";
        return "Free \u2014 tap \u21bb to load models";
    }

    // ── Offline AI Models (on-device, LiteRT-LM) ────────────────────────────

    private void setupOfflineModels() {
        localModelManager = new pro.sketchware.ai.offline.LocalModelManager(this);

        if (localModelManager.isLowRamDevice()) {
            binding.tvOfflineRamWarning.setVisibility(View.VISIBLE);
        }

        // GPU acceleration switch — isGpuBackendPreferred()/setGpuBackendPreferred() already
        // existed on LocalModelManager (LiteRtLmEngineBridge already reads this and falls back
        // to CPU automatically on any GPU init failure), but nothing in the UI ever set it, so
        // on-device generation always silently ran CPU-only. Wiring it here is what actually
        // turns GPU on for users whose device supports it.
        binding.switchGpuBackend.setOnCheckedChangeListener(null);
        binding.switchGpuBackend.setChecked(localModelManager.isGpuBackendPreferred());
        binding.switchGpuBackend.setOnCheckedChangeListener((btn, checked) -> {
            localModelManager.setGpuBackendPreferred(checked);
            // The engine bridge reloads automatically on the next generate() call when the
            // requested backend differs from the currently-loaded one (see
            // LiteRtLmEngineBridge#generate's `needsReload` check) — no extra action needed here.
        });

        offlineModelAdapter = new pro.sketchware.ai.adapters.OfflineModelAdapter(
                pro.sketchware.ai.offline.LocalModelCatalog.all(),
                localModelManager,
                new pro.sketchware.ai.adapters.OfflineModelAdapter.Callback() {
                    @Override
                    public void onDownload(pro.sketchware.ai.offline.LocalModelCatalog model) {
                        startOfflineModelDownload(model);
                    }

                    @Override
                    public void onCancelDownload(pro.sketchware.ai.offline.LocalModelCatalog model) {
                        // Routed through the service so its own tracking/notification state stays
                        // in sync — LocalModelDownloadService.cancel() does the same
                        // LocalModelDownloader.cancel() + partial-file-delete + state-reset this
                        // used to do directly, then removes/replaces its notification for this model.
                        pro.sketchware.ai.offline.LocalModelDownloadService.cancel(AiSettingsActivity.this, model);
                        offlineModelAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onPauseDownload(pro.sketchware.ai.offline.LocalModelCatalog model) {
                        pro.sketchware.ai.offline.LocalModelDownloadService.pause(AiSettingsActivity.this, model);
                        offlineModelAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onResumeDownload(pro.sketchware.ai.offline.LocalModelCatalog model) {
                        // Resuming is just calling download() again — it detects the existing
                        // .part file and continues via a Range request from that byte offset.
                        startOfflineModelDownload(model);
                    }

                    @Override
                    public void onDelete(pro.sketchware.ai.offline.LocalModelCatalog model) {
                        new MaterialAlertDialogBuilder(AiSettingsActivity.this)
                                .setTitle("Delete model?")
                                .setMessage("\"" + model.getDisplayName() + "\" will be removed from storage. You'll need to download it again to use it.")
                                .setPositiveButton("Delete", (d, w) -> {
                                    localModelManager.deleteModel(model);
                                    offlineModelAdapter.notifyDataSetChanged();
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }

                    @Override
                    public void onSelect(pro.sketchware.ai.offline.LocalModelCatalog model) {
                        localModelManager.setSelectedModel(model);
                        offlineModelAdapter.notifyDataSetChanged();
                        Toast.makeText(AiSettingsActivity.this,
                                model.getDisplayName() + " is now active as the offline model",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onOpenModelPage(pro.sketchware.ai.offline.LocalModelCatalog model) {
                        // Sends the user straight to the exact repo page to log in and click
                        // "Agree" on the license — searching huggingface.co for "Gemma" does
                        // not reliably surface repos like litert-community/Gemma3-1B-IT.
                        openUrl(model.getModelPageUrl());
                    }
                });

        binding.offlineModelsRecycler.setAdapter(offlineModelAdapter);
        binding.offlineModelsRecycler.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        binding.offlineModelsRecycler.setNestedScrollingEnabled(false);

        // Persistent project knowledge (rules/env/tool notes) that survive regardless of how
        // long the chat history has grown — see KnowledgeStore's javadoc for why this exists
        // as a separate store rather than relying on chat history trimming.
        knowledgeStore = new pro.sketchware.ai.offline.knowledge.KnowledgeStore(this);
        binding.btnManageKnowledge.setOnClickListener(v -> showKnowledgeManagerDialog());
    }

    /**
     * Minimal list+add/delete UI for {@link pro.sketchware.ai.offline.knowledge.KnowledgeStore}.
     * Deliberately dialog-based rather than a dedicated screen — this is a small, infrequently
     * used management surface (add a handful of rules once, revisit rarely), so a full Activity/
     * Fragment would be more scaffolding than the feature warrants.
     */
    private void showKnowledgeManagerDialog() {
        List<pro.sketchware.ai.offline.knowledge.KnowledgeStore.Entry> entries = knowledgeStore.getAll();

        String[] labels = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            pro.sketchware.ai.offline.knowledge.KnowledgeStore.Entry e = entries.get(i);
            String priorityMark = e.priority == pro.sketchware.ai.offline.knowledge.KnowledgeStore.Priority.CRITICAL
                    ? "★ " : "";
            labels[i] = priorityMark + "[" + e.category.name() + "] " + e.title;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Project Knowledge (" + entries.size() + ")")
                .setMessage(entries.isEmpty()
                        ? "No saved rules yet. Add project rules, environment facts, or tool "
                        + "notes here so the offline model keeps following them no matter how "
                        + "long the conversation gets — unlike chat history, these are never "
                        + "trimmed away."
                        : "★ = always included in every prompt. Tap an entry to delete it.")
                .setItems(labels, (d, which) -> confirmDeleteKnowledgeEntry(entries.get(which)))
                .setPositiveButton("Add Rule", (d, w) -> showAddKnowledgeEntryDialog())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDeleteKnowledgeEntry(pro.sketchware.ai.offline.knowledge.KnowledgeStore.Entry entry) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this entry?")
                .setMessage(entry.title + ": " + entry.content)
                .setPositiveButton("Delete", (d, w) -> {
                    knowledgeStore.delete(entry.id);
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Simple add-entry form: title + content free text, plus a CRITICAL/NORMAL choice
     * presented as two buttons rather than a spinner, since there are only two options and
     * the difference (always included vs. only when relevant) is worth explaining inline
     * rather than as a dropdown label.
     */
    private void showAddKnowledgeEntryDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        com.google.android.material.textfield.TextInputEditText titleInput =
                new com.google.android.material.textfield.TextInputEditText(this);
        titleInput.setHint("Short title, e.g. \"Preferred language\"");
        layout.addView(titleInput);

        com.google.android.material.textfield.TextInputEditText contentInput =
                new com.google.android.material.textfield.TextInputEditText(this);
        contentInput.setHint("Rule text, e.g. \"Always use Kotlin, not Java\"");
        contentInput.setMinLines(2);
        android.widget.LinearLayout.LayoutParams contentParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        contentParams.topMargin = pad / 2;
        layout.addView(contentInput, contentParams);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Add Project Rule")
                .setView(layout)
                .setPositiveButton("Save — Always Include", (d, w) ->
                        saveKnowledgeEntry(titleInput, contentInput,
                                pro.sketchware.ai.offline.knowledge.KnowledgeStore.Priority.CRITICAL))
                .setNeutralButton("Save — When Relevant", (d, w) ->
                        saveKnowledgeEntry(titleInput, contentInput,
                                pro.sketchware.ai.offline.knowledge.KnowledgeStore.Priority.NORMAL))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveKnowledgeEntry(com.google.android.material.textfield.TextInputEditText titleInput,
                                     com.google.android.material.textfield.TextInputEditText contentInput,
                                     pro.sketchware.ai.offline.knowledge.KnowledgeStore.Priority priority) {
        String title = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
        String content = contentInput.getText() != null ? contentInput.getText().toString().trim() : "";
        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Both fields are required", Toast.LENGTH_SHORT).show();
            return;
        }
        // keywords = title + content gives the FTS index something to match on immediately,
        // without requiring the user to fill in a separate keywords field for a small,
        // infrequently-edited list of rules.
        knowledgeStore.upsert(
                pro.sketchware.ai.offline.knowledge.KnowledgeStore.Category.RULE,
                title, content, title + " " + content, priority);
        Toast.makeText(this,
                priority == pro.sketchware.ai.offline.knowledge.KnowledgeStore.Priority.CRITICAL
                        ? "Saved — will be included in every prompt"
                        : "Saved — will be included when relevant",
                Toast.LENGTH_SHORT).show();
    }

    /** Step 1: confirm RAM/disk-space warnings (if any), then start the actual download. */
    private void startOfflineModelDownload(pro.sketchware.ai.offline.LocalModelCatalog model) {
        if (localModelManager.isInsufficientDiskSpace(model)) {
            Toast.makeText(this,
                    "Not enough storage space to download this model (about " + model.getApproxSizeLabel() + " required).",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String warning = null;
        if (localModelManager.isBelowModelRecommendation(model)) {
            warning = "Your device has less RAM than recommended for this model (about " + model.getMinRamGb()
                    + " GB recommended). It may run slowly or fail to load on your device. Continue anyway?";
        }

        Runnable startDownload = () -> {
            offlineModelAdapter.notifyDataSetChanged();
            // The actual transfer now runs inside LocalModelDownloadService, a foreground service
            // with its own ongoing notification — it keeps running after this Activity leaves the
            // foreground, is backgrounded, or is destroyed, and only stops on completion, error,
            // or an explicit user Stop (from the notification or this screen). This Activity no
            // longer receives a direct DownloadCallback for progress; startDownloadProgressPolling
            // below picks up progress from LocalModelManager's persisted state instead, since that
            // state is what the service itself writes to on every progress tick regardless of
            // whether this screen is even open to observe it.
            pro.sketchware.ai.offline.LocalModelDownloadService.start(AiSettingsActivity.this, model);
            startDownloadProgressPolling();
        };

        // Explicit size warning before starting, per phase requirement — shown every time,
        // not just once, since a multi-GB download over metered data is a real user cost.
        String sizeMessage = "\"" + model.getDisplayName() + "\" (about " + model.getApproxSizeLabel()
                + ") will be downloaded in the background — you can leave this screen or close the "
                + "app and it will keep going, with progress shown in a notification. Make sure "
                + "you're connected to Wi-Fi to avoid using mobile data."
                + (warning != null ? "\n\n⚠️ " + warning : "");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Download model")
                .setMessage(sizeMessage)
                .setPositiveButton("Download", (d, w) -> startDownload.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private final android.os.Handler downloadPollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean downloadPollingActive = false;

    /**
     * Polls {@link LocalModelManager}'s persisted download state every second to refresh
     * {@link #offlineModelAdapter} while this screen is open. This replaces the direct
     * {@code DownloadCallback} this screen used to get straight from
     * {@code LocalModelDownloader.download()} — now that the transfer runs inside
     * {@link pro.sketchware.ai.offline.LocalModelDownloadService} instead of being called
     * directly from here, this Activity has no callback reference to listen to (the service may
     * be updating progress while this screen doesn't even exist), so it polls the same
     * SharedPreferences-backed state the service itself writes to on every progress tick. Stops
     * automatically once no tracked model is downloading, and is also torn down in
     * {@link #onDestroy()} to avoid leaking the polling loop past this screen's lifecycle.
     */
    private void startDownloadProgressPolling() {
        if (downloadPollingActive) return;
        downloadPollingActive = true;
        downloadPollHandler.post(downloadPollRunnable);
    }

    private final Runnable downloadPollRunnable = new Runnable() {
        @Override
        public void run() {
            boolean anyDownloading = false;
            for (pro.sketchware.ai.offline.LocalModelCatalog model :
                    pro.sketchware.ai.offline.LocalModelCatalog.all()) {
                if (localModelManager.getState(model) == pro.sketchware.ai.offline.LocalModelState.DOWNLOADING) {
                    anyDownloading = true;
                    break;
                }
            }
            if (offlineModelAdapter != null) {
                offlineModelAdapter.notifyDataSetChanged();
            }
            if (anyDownloading) {
                downloadPollHandler.postDelayed(this, 1000);
            } else {
                downloadPollingActive = false;
            }
        }
    };


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
        // Skip NVIDIA (models require specific parameters) and POLLINATIONS (free proxy, no ping needed)
        // with rate limits that make per-model validation impractical.
        if (all.size() > 25
                || provider == AiProvider.OPENROUTER
                || provider == AiProvider.DEEPINFRA
                || provider == AiProvider.TOGETHER
                || provider == AiProvider.SAMBANOVA
                || provider == AiProvider.HUGGINGFACE
                || provider == AiProvider.FIREWORKS
                || provider == AiProvider.NOVITA
                || provider == AiProvider.KLUSTER
                || provider == AiProvider.NVIDIA
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

                boolean responded = latch.await(12, java.util.concurrent.TimeUnit.SECONDS);
                // If the model never responded within the window, this is most likely a
                // slow network / busy server, not an invalid model — don't punish it by
                // silently dropping it from the cached list (ok[0] stays false otherwise).
                if (!responded) ok[0] = true;
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
            case HYPERBOLIC:       return URL_HYPERBOLIC;
            case KLUSTER:          return URL_KLUSTER;
            case LAMBDA:           return URL_LAMBDA;
            case SCALEWAY:         return URL_SCALEWAY;
            case FIREWORKS:        return URL_FIREWORKS;
            case NOVITA:           return URL_NOVITA;
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

    @SuppressWarnings("deprecation") // intentionally checks legacy LLM7/CHUTES/DEEPSEEK to exclude them
    private void setupLayoutGenerationSettings() {
        // Build visible provider list — exclude legacy hidden entries
        final java.util.List<AiProvider> visibleProviders = new java.util.ArrayList<>();
        for (AiProvider p : AiProvider.values()) {
            if (p == AiProvider.LLM7 || p == AiProvider.CHUTES || p == AiProvider.DEEPSEEK) continue;
            visibleProviders.add(p);
        }
        java.util.List<String> providerLabels = new java.util.ArrayList<>();
        providerLabels.add("Same as Chat (default)");
        for (AiProvider p : visibleProviders) {
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
                AiProvider selected = visibleProviders.get(position - 1);
                preferences.prefs().edit()
                        .putString(AiPreferences.KEY_LAYOUT_AI_PROVIDER, selected.name())
                        .apply();
                updateLayoutProviderHint(selected.name());
            }
        });
    }

    private void updateLayoutProviderHint(String providerName) {
        if (providerName.isEmpty() || providerName.equalsIgnoreCase("POLLINATIONS")) {
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
        // Always visible (not tied to `running`): it shows the final result text
        // ("✅ Done…" / "Cancelled." / "❌ …") set right after this call returns,
        // in onComplete/onCancelled/onError below — hiding it here would hide that message too.
        binding.tvDiagnosticStatus.setVisibility(android.view.View.VISIBLE);
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
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
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
                Uri uri = androidx.core.content.FileProvider.getUriForFile(
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

    // ── API Key Backup (Export / Import) ──────────────────────────────────────
    // See pro.sketchware.ai.security.ApiKeyExportImport for the encryption
    // design (AES-256-GCM with a PBKDF2-derived, user-password-based key,
    // deliberately independent of SecureKeyStore/Android Keystore so the
    // resulting file survives an uninstall/reinstall — audit report §7).

    /** Bytes prepared by exportKeys(), waiting for the user to pick a save location. */
    private byte[] pendingExportBytes;

    private void setupApiKeyBackup() {
        binding.btnExportApiKeys.setOnClickListener(v -> promptExportPassword());
        binding.btnImportApiKeys.setOnClickListener(v -> {
            Intent openIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            openIntent.addCategory(Intent.CATEGORY_OPENABLE);
            openIntent.setType("*/*");
            try {
                startActivityForResult(openIntent, REQUEST_IMPORT);
            } catch (Exception e) {
                Toast.makeText(this, "No file picker app available", Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Step 1 of export: ask for the password that will protect the file. */
    private void promptExportPassword() {
        android.widget.EditText passwordInput = new EditText(this);
        passwordInput.setHint("Choose a password");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Export API Keys")
                .setMessage("This file will contain every API key you've saved, protected by "
                        + "the password you enter here. Anyone with both the file and this "
                        + "password can use your keys — keep it safe and don't share it.")
                .setView(wrapWithPadding(passwordInput))
                .setPositiveButton("Continue", (dialog, which) -> {
                    char[] password = toCharArray(passwordInput.getText());
                    if (password.length < 4) {
                        Toast.makeText(this, "Choose a longer password", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    doExport(password);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Step 2 of export: encrypt now, then let the user pick where to save the file. */
    private void doExport(char[] password) {
        try {
            pro.sketchware.ai.security.ApiKeyExportImport.ExportResult result =
                    pro.sketchware.ai.security.ApiKeyExportImport.exportKeys(this, password);
            pendingExportBytes = result.fileContents;

            Intent createIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            createIntent.addCategory(Intent.CATEGORY_OPENABLE);
            createIntent.setType(pro.sketchware.ai.security.ApiKeyExportImport.MIME_TYPE);
            String fileName = "sketchware-ai-keys-"
                    + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date())
                    + pro.sketchware.ai.security.ApiKeyExportImport.FILE_EXTENSION;
            createIntent.putExtra(Intent.EXTRA_TITLE, fileName);
            try {
                startActivityForResult(createIntent, REQUEST_EXPORT);
            } catch (Exception e) {
                pendingExportBytes = null;
                Toast.makeText(this, "No file picker app available", Toast.LENGTH_LONG).show();
            }
        } catch (pro.sketchware.ai.security.ApiKeyExportImport.ExportImportException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Step 1 of import: ask for the password once a file has been picked. */
    private void promptImportPassword(@NonNull Uri fileUri) {
        android.widget.EditText passwordInput = new EditText(this);
        passwordInput.setHint("Enter the export password");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Import API Keys")
                .setMessage("Enter the password you used when this file was exported. "
                        + "Importing will overwrite any existing key for the same provider.")
                .setView(wrapWithPadding(passwordInput))
                .setPositiveButton("Import", (dialog, which) -> {
                    char[] password = toCharArray(passwordInput.getText());
                    doImport(fileUri, password);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Step 2 of import: read the picked file and decrypt it. */
    private void doImport(@NonNull Uri fileUri, char[] password) {
        try {
            byte[] fileContents;
            try (java.io.InputStream in = getContentResolver().openInputStream(fileUri)) {
                if (in == null) throw new java.io.IOException("Could not open the selected file");
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                fileContents = out.toByteArray();
            }

            pro.sketchware.ai.security.ApiKeyExportImport.ImportResult result =
                    pro.sketchware.ai.security.ApiKeyExportImport.importKeys(this, fileContents, password);

            // Refresh the provider list so restored keys show up immediately.
            if (providerAdapter != null) {
                providerAdapter.notifyDataSetChanged();
            }

            StringBuilder providerNames = new StringBuilder();
            for (AiProvider provider : result.importedProviders) {
                if (providerNames.length() > 0) providerNames.append(", ");
                providerNames.append(provider.getDisplayName());
            }
            Toast.makeText(this,
                    "Imported " + result.importedProviders.size() + " key(s): " + providerNames,
                    Toast.LENGTH_LONG).show();
        } catch (java.io.IOException e) {
            Toast.makeText(this, "Could not read the selected file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (pro.sketchware.ai.security.ApiKeyExportImport.ExportImportException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
            pendingExportBytes = null;
            return;
        }
        Uri uri = data.getData();

        if (requestCode == REQUEST_EXPORT) {
            if (pendingExportBytes == null) return;
            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new java.io.IOException("Could not open the destination file");
                out.write(pendingExportBytes);
                Toast.makeText(this, "API keys exported successfully", Toast.LENGTH_SHORT).show();
            } catch (java.io.IOException e) {
                Toast.makeText(this, "Failed to write export file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            } finally {
                pendingExportBytes = null;
            }
        } else if (requestCode == REQUEST_IMPORT) {
            promptImportPassword(uri);
        }
    }

    @NonNull
    private static char[] toCharArray(@Nullable Editable text) {
        if (text == null || text.length() == 0) return new char[0];
        char[] result = new char[text.length()];
        android.text.TextUtils.getChars(text, 0, text.length(), result, 0);
        return result;
    }

    /** Wraps a view with standard MaterialAlertDialog content padding. */
    @NonNull
    private View wrapWithPadding(@NonNull View view) {
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        frame.setPadding(padding, padding / 2, padding, 0);
        frame.addView(view, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        return frame;
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
    protected void onResume() {
        super.onResume();
        // Refresh the list immediately (picks up any progress/state change that happened while
        // this screen wasn't visible — including a download that finished, errored, or is still
        // running via LocalModelDownloadService), and resume the progress-polling loop if a
        // download is still active so the list keeps live-updating without needing another tap.
        if (offlineModelAdapter != null) {
            offlineModelAdapter.notifyDataSetChanged();
        }
        if (localModelManager != null) {
            for (pro.sketchware.ai.offline.LocalModelCatalog model :
                    pro.sketchware.ai.offline.LocalModelCatalog.all()) {
                if (localModelManager.getState(model) == pro.sketchware.ai.offline.LocalModelState.DOWNLOADING) {
                    startDownloadProgressPolling();
                    break;
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSystemPrompt();
        // Keys are saved immediately via AiProviderAdapter.onKeyChanged callbacks
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Downloads no longer need to be paused here — LocalModelDownloadService (a foreground
        // service with its own ongoing notification) now owns the actual transfer and keeps
        // running independently of this Activity's lifecycle. Leaving this screen (backgrounding
        // the app, switching to another app, locking the screen) no longer pauses or interrupts
        // an in-flight download; only an explicit user action (Pause/Stop in the notification or
        // in this screen's list) does. See LocalModelDownloadService's class javadoc.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AiHealthMonitor.getInstance().setListener(null);
        ToolTelemetry.getInstance().setListener(null);
        dismissFailoverBanner();
        executor.shutdownNow();
        // Stop the progress-polling loop from startDownloadProgressPolling() — the download(s)
        // themselves keep running in LocalModelDownloadService regardless (that's the whole
        // point), only this screen's own UI-refresh polling needs to stop here so it doesn't
        // keep firing against a destroyed Activity/adapter.
        downloadPollHandler.removeCallbacksAndMessages(null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────


    private void setupAdvancedSettingsToggle() {
        binding.advancedSettingsContent.setVisibility(android.view.View.GONE);
        binding.ivAdvancedArrow.setRotation(0f);
        binding.advancedSettingsHeader.setOnClickListener(v -> {
            boolean visible = binding.advancedSettingsContent.getVisibility() == android.view.View.VISIBLE;
            if (visible) {
                binding.advancedSettingsContent.setVisibility(android.view.View.GONE);
                animateArrow(binding.ivAdvancedArrow, 0f);
            } else {
                binding.advancedSettingsContent.setVisibility(android.view.View.VISIBLE);
                animateArrow(binding.ivAdvancedArrow, 180f);
            }
        });
    }

    /** Same collapsible pattern as {@link #setupAdvancedSettingsToggle()}, applied to the
     *  "Offline AI Models" card. Unlike the other collapsible sections on this screen, this one
     *  starts EXPANDED by default so on-device models are immediately visible/discoverable. */
    private void setupOfflineModelsToggle() {
        binding.offlineModelsContent.setVisibility(android.view.View.VISIBLE);
        binding.ivOfflineModelsArrow.setRotation(180f);
        binding.offlineModelsHeader.setOnClickListener(v -> {
            boolean visible = binding.offlineModelsContent.getVisibility() == android.view.View.VISIBLE;
            if (visible) {
                binding.offlineModelsContent.setVisibility(android.view.View.GONE);
                animateArrow(binding.ivOfflineModelsArrow, 0f);
            } else {
                binding.offlineModelsContent.setVisibility(android.view.View.VISIBLE);
                animateArrow(binding.ivOfflineModelsArrow, 180f);
            }
        });
    }

    /** Same collapsible pattern as {@link #setupAdvancedSettingsToggle()}, applied to the
     *  "AI Providers" list (free/API-key providers like Pollinations) so "Offline AI Models" is
     *  the section that stands out expanded by default on this screen. Collapsed by default. */
    private void setupProvidersToggle() {
        binding.providersContent.setVisibility(android.view.View.GONE);
        binding.ivProvidersArrow.setRotation(0f);
        binding.providersHeader.setOnClickListener(v -> {
            boolean visible = binding.providersContent.getVisibility() == android.view.View.VISIBLE;
            if (visible) {
                binding.providersContent.setVisibility(android.view.View.GONE);
                animateArrow(binding.ivProvidersArrow, 0f);
            } else {
                binding.providersContent.setVisibility(android.view.View.VISIBLE);
                animateArrow(binding.ivProvidersArrow, 180f);
            }
        });
    }

    private void animateArrow(android.widget.ImageView arrow, float to) {
        android.view.animation.RotateAnimation r = new android.view.animation.RotateAnimation(
                arrow.getRotation(), to,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
        r.setDuration(200);
        r.setFillAfter(true);
        arrow.startAnimation(r);
        arrow.setRotation(to);
    }
}
