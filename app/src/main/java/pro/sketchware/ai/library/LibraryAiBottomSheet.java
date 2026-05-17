package pro.sketchware.ai.library;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pro.sketchware.ai.activities.AiSettingsActivity;
import pro.sketchware.ai.api.AiApiClient;
import pro.sketchware.ai.api.AiClientFactory;
import pro.sketchware.ai.api.StreamingResponseHandler;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.AiProviderModels;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.databinding.BottomSheetLibraryAiBinding;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Library AI Assistant BottomSheet — matches Design-editor sheet UX exactly.
 *
 * Key behaviours:
 *  • Sidebar open (220dp) + categories COLLAPSED by default
 *  • Sidebar tool tap → fills input field (user edits then sends)
 *  • BottomSheet locked EXPANDED so RecyclerView scrolls (not sheet shrinks)
 *  • Model selector chip → shows provider/model picker dialog
 *  • Microphone → RecognizerIntent speech-to-text
 *  • Pulse timer: every 10s while AI is thinking, shows elapsed time
 *  • Non-AI tools execute directly (offline-capable)
 *  • System prompt visible in sidebar header when expanded
 */
public class LibraryAiBottomSheet extends BottomSheetDialogFragment {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final String ARG_SC_ID        = "sc_id";
    private static final String ARG_UNASSOCIATED = "unassociated";
    private static final int    SIDEBAR_COLLAPSED = 48;
    private static final int    SIDEBAR_EXPANDED  = 220;
    private static final int    REQUEST_VOICE     = 0x7E1C;

    // ── Factory ──────────────────────────────────────────────────────────────
    public static LibraryAiBottomSheet newInstance(String scId, boolean notAssociated) {
        LibraryAiBottomSheet f = new LibraryAiBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_SC_ID, scId);
        b.putBoolean(ARG_UNASSOCIATED, notAssociated);
        f.setArguments(b);
        return f;
    }

    // ── Callback ─────────────────────────────────────────────────────────────
    public interface OnLibraryActionListener { void onLibraryAction(String action); }
    private OnLibraryActionListener actionListener;
    public void setOnLibraryActionListener(OnLibraryActionListener l) { actionListener = l; }

    // ── State ─────────────────────────────────────────────────────────────────
    private BottomSheetLibraryAiBinding binding;
    private String scId;
    private boolean notAssociated;
    private boolean sidebarExpanded = true; // sidebar OPEN by default
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private LibraryChatAdapter chatAdapter;
    private LibrarySidebarAdapter sidebarAdapter;
    private AiPreferences preferences;

    // Pulse timer while AI is thinking
    private final Handler pulseHandler = new Handler(Looper.getMainLooper());
    private Runnable pulseRunnable;
    private int pulseSeconds = 0;
    private boolean isAiThinking = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL,
            com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog);
        if (getArguments() != null) {
            scId          = getArguments().getString(ARG_SC_ID, "system");
            notAssociated = getArguments().getBoolean(ARG_UNASSOCIATED, true);
        }
        if (getContext() != null) preferences = AiPreferences.getInstance(getContext());
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetLibraryAiBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        lockBottomSheetExpanded();
        setupHeader();
        setupSidebar();
        setupChat();
        setupInput();
        setupEmptyStateChips();
        // Apply sidebar initial state (open, items collapsed)
        applySidebarWidth(true);
    }

    // ── Lock BottomSheet EXPANDED so chat scrolls instead of shrinking ─────────
    private void lockBottomSheetExpanded() {
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
            FrameLayout sheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                // Lock: prevent dragging down (sheet won't shrink while scrolling chat)
                behavior.setDraggable(false);
            }
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────
    private void setupHeader() {
        binding.libAiTitle.setText("Local Library");
        binding.libAiScopeName.setText(
            notAssociated ? "All Libraries" : "Project: " + scId);

        updateProviderLabel();

        // Model selector chip → provider + model picker
        binding.libAiModelSelector.setOnClickListener(v -> showModelPicker());

        // Settings
        binding.libAiBtnSettings.setOnClickListener(v -> {
            if (getContext() != null)
                startActivity(new Intent(getContext(), AiSettingsActivity.class));
        });

        // Clear
        binding.libAiBtnClear.setOnClickListener(v -> {
            chatHistory.clear();
            chatAdapter.notifyDataSetChanged();
            showEmpty(true);
        });

        // Close
        binding.libAiBtnClose.setOnClickListener(v -> dismiss());
    }

    private void updateProviderLabel() {
        if (preferences == null) return;
        AiProvider p = preferences.getSelectedProvider();
        if (p != null) {
            binding.libAiProviderName.setText(p.getDisplayName());
            String model = preferences.getSelectedModel(p);
            binding.libAiModelChip.setText(
                model.length() > 16 ? model.substring(0, 13) + "…" : model);
        }
    }

    /** Show provider → model picker (same flow as Design page). */
    private void showModelPicker() {
        if (preferences == null) return;
        AiProvider currentProvider = preferences.getSelectedProvider();

        // Build list of enabled providers
        AiProvider[] allProviders = AiProvider.values();
        List<String> labels = new ArrayList<>();
        List<AiProvider> enabledProviders = new ArrayList<>();
        for (AiProvider p : allProviders) {
            if (p == AiProvider.LOCAL_LLM) continue;
            boolean enabled = preferences.prefs().getBoolean("provider_enabled_" + p.name(), false);
            if (!enabled) continue;
            boolean hasKey = !p.requiresApiKey() || preferences.getApiKey(p) != null && !preferences.getApiKey(p).isEmpty();
            if (!hasKey) continue;
            labels.add(p.getDisplayName());
            enabledProviders.add(p);
        }

        if (labels.isEmpty()) {
            SketchwareUtil.toast("No providers enabled. Open AI Settings.");
            return;
        }

        int current = enabledProviders.indexOf(currentProvider);

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Provider")
            .setSingleChoiceItems(labels.toArray(new String[0]), current, (d, which) -> {
                d.dismiss();
                AiProvider selected = enabledProviders.get(which);
                showModelSubPicker(selected);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showModelSubPicker(AiProvider provider) {
        List<String> models = AiProviderModels.getStaticModels(provider);
        if (models.isEmpty()) { SketchwareUtil.toast("No models for " + provider.getDisplayName()); return; }

        String current = preferences.getSelectedModel(provider);
        int idx = models.indexOf(current);

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(provider.getDisplayName() + " — Select Model")
            .setSingleChoiceItems(models.toArray(new String[0]), idx, (d, which) -> {
                d.dismiss();
                // Persist selection via AiPreferences
                preferences.prefs().edit()
                    .putString("selected_provider", provider.name())
                    .putString("selected_model_" + provider.name(), models.get(which))
                    .apply();
                updateProviderLabel();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────
    private void setupSidebar() {
        List<LibrarySidebarAdapter.Tool> tools = buildAllSidebarTools();
        sidebarAdapter = new LibrarySidebarAdapter(tools, new LibrarySidebarAdapter.OnToolListener() {
            @Override public void onFillInput(String template) {
                fillInput(template);
            }
            @Override public void onDirectAction(String key) {
                executeDirectAction(key);
            }
        });
        sidebarAdapter.setSidebarExpanded(true); // labels visible (sidebar open)

        LinearLayoutManager lm = new LinearLayoutManager(getContext());
        binding.libAiToolsRv.setLayoutManager(lm);
        binding.libAiToolsRv.setAdapter(sidebarAdapter);

        binding.libAiSidebarToggle.setOnClickListener(v -> toggleSidebar());
    }

    private void toggleSidebar() {
        sidebarExpanded = !sidebarExpanded;
        int from = dpToPx(sidebarExpanded ? SIDEBAR_COLLAPSED : SIDEBAR_EXPANDED);
        int to   = dpToPx(sidebarExpanded ? SIDEBAR_EXPANDED  : SIDEBAR_COLLAPSED);
        ValueAnimator anim = ValueAnimator.ofInt(from, to);
        anim.setDuration(220);
        anim.addUpdateListener(a -> {
            ViewGroup.LayoutParams lp = binding.libAiSidebarContainer.getLayoutParams();
            lp.width = (int) a.getAnimatedValue();
            binding.libAiSidebarContainer.setLayoutParams(lp);
        });
        anim.start();
        sidebarAdapter.setSidebarExpanded(sidebarExpanded);
    }

    private void applySidebarWidth(boolean expanded) {
        ViewGroup.LayoutParams lp = binding.libAiSidebarContainer.getLayoutParams();
        lp.width = dpToPx(expanded ? SIDEBAR_EXPANDED : SIDEBAR_COLLAPSED);
        binding.libAiSidebarContainer.setLayoutParams(lp);
        sidebarAdapter.setSidebarExpanded(expanded);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    /** All library tools — mirrors the main menu structure. */
    private List<LibrarySidebarAdapter.Tool> buildAllSidebarTools() {
        List<LibrarySidebarAdapter.Tool> t = new ArrayList<>();

        // ── UPDATES ──────────────────────────────────────────────────────────
        t.add(new LibrarySidebarAdapter.Tool("Updates", pro.sketchware.R.drawable.ic_mtrl_update));
        t.add(new LibrarySidebarAdapter.Tool("Find Updates",
            pro.sketchware.R.drawable.ic_mtrl_refresh,
            "Find latest stable versions for all libraries in project " + scId + ". "
            + "List each: current version → latest. Do not modify anything.", true));
        t.add(new LibrarySidebarAdapter.Tool("Update All",
            pro.sketchware.R.drawable.ic_mtrl_update,
            "Which library do you want to update? Enter its name:", false));
        t.add(new LibrarySidebarAdapter.Tool("Version History",
            pro.sketchware.R.drawable.ic_mtrl_history,
            "Show version history for this library: ", false));

        // ── ANALYSIS ─────────────────────────────────────────────────────────
        t.add(new LibrarySidebarAdapter.Tool("Analysis", pro.sketchware.R.drawable.ic_mtrl_warning));
        t.add(new LibrarySidebarAdapter.Tool("Detect Conflicts",
            pro.sketchware.R.drawable.ic_mtrl_warning,
            "Detect version conflicts and incompatibilities in project " + scId
            + ". List real issues with suggested resolutions.", true));
        t.add(new LibrarySidebarAdapter.Tool("Find Duplicates",
            pro.sketchware.R.drawable.ic_mtrl_filter,
            "Find libraries with duplicate groupIds or overlapping functions. "
            + "Suggest which to keep. Do not remove without confirmation.", true));
        t.add(new LibrarySidebarAdapter.Tool("Validate All",
            pro.sketchware.R.drawable.ic_mtrl_check,
            "Full audit: 1) conflicts 2) minSdk compatibility 3) breaking changes. "
            + "Report only. Do not auto-modify.", true));

        // ── LIBRARY TOOLS ─────────────────────────────────────────────────────
        t.add(new LibrarySidebarAdapter.Tool("Library", pro.sketchware.R.drawable.ic_mtrl_box));
        t.add(new LibrarySidebarAdapter.Tool("Search Maven",
            pro.sketchware.R.drawable.ic_mtrl_download,
            "Search Maven Central for: [enter library name here]\n"
            + "Return: groupId:artifactId:version (latest stable).", false));
        t.add(new LibrarySidebarAdapter.Tool("Explain Library",
            pro.sketchware.R.drawable.ic_mtrl_article,
            "Explain this library: [enter library name here]\n"
            + "Include: purpose, Android use cases, and a short code example.", false));
        t.add(new LibrarySidebarAdapter.Tool("Suggest Alternative",
            pro.sketchware.R.drawable.ic_mtrl_bulb,
            "Suggest an alternative to: [enter library name here]\n"
            + "Requirements: minSdk 21, actively maintained, pros/cons vs original.", false));
        t.add(new LibrarySidebarAdapter.Tool("Clean Unused",
            pro.sketchware.R.drawable.ic_mtrl_delete_sweep,
            "Find libraries in project " + scId + " not used in any activity, layout, or Java. "
            + "List them. Wait for my confirmation.", true));

        // ── BACKUP & MANAGEMENT ───────────────────────────────────────────────
        t.add(new LibrarySidebarAdapter.Tool("Management", pro.sketchware.R.drawable.ic_mtrl_settings));
        t.add(new LibrarySidebarAdapter.Tool("Backup All",
            pro.sketchware.R.drawable.ic_mtrl_checklist,
            "Backup all libraries", true));
        t.add(new LibrarySidebarAdapter.Tool("Restore Backup",
            pro.sketchware.R.drawable.ic_mtrl_history,
            "Restore from backup", true));
        t.add(new LibrarySidebarAdapter.Tool("Manage Repos",
            pro.sketchware.R.drawable.ic_mtrl_code,
            "Manage Maven repositories", true));
        t.add(new LibrarySidebarAdapter.Tool("Clean Orphans",
            pro.sketchware.R.drawable.ic_mtrl_clear_all,
            "Clean orphan files", true));
        t.add(new LibrarySidebarAdapter.Tool("Scan JARs",
            pro.sketchware.R.drawable.ic_mtrl_bug_report,
            "Scan JARs for Maven coordinates", true));

        return t;
    }

    /** Fill chat input with template text — user edits and sends. */
    private void fillInput(String template) {
        if (binding == null) return;
        binding.libAiInput.setText(template);
        binding.libAiInput.setSelection(binding.libAiInput.getText().length());
        binding.libAiInput.requestFocus();
        // Scroll chat if empty → show empty state chip area
        showEmpty(chatHistory.isEmpty());
    }

    /** Execute tool that doesn't require AI (direct action). */
    private void executeDirectAction(String toolLabel) {
        if (actionListener != null) actionListener.onLibraryAction(toolLabel);
        // Show confirmation in chat
        String msg = "▶ Executing: " + toolLabel + " (no AI required)";
        pushAssistant(msg);
    }

    // ── Chat ──────────────────────────────────────────────────────────────────
    private void setupChat() {
        chatAdapter = new LibraryChatAdapter(chatHistory);
        LinearLayoutManager lm = new LinearLayoutManager(getContext());
        lm.setStackFromEnd(true);
        binding.libAiMessages.setLayoutManager(lm);
        binding.libAiMessages.setAdapter(chatAdapter);

        binding.libAiMessages.addOnScrollListener(
            new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                @Override public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView rv, int dx, int dy) {
                    LinearLayoutManager llm = (LinearLayoutManager) rv.getLayoutManager();
                    if (llm == null) return;
                    boolean atBottom = llm.findLastCompletelyVisibleItemPosition() >= chatAdapter.getItemCount() - 1;
                    binding.libAiFabScrollDown.setVisibility(atBottom ? View.GONE : View.VISIBLE);
                }
            });
        binding.libAiFabScrollDown.setOnClickListener(v ->
            binding.libAiMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1));
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    private void setupInput() {
        binding.libAiInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendUserMessage(); return true; }
            return false;
        });
        binding.libAiBtnSend.setOnClickListener(v -> sendUserMessage());
        binding.libAiBtnMic.setOnClickListener(v -> launchVoiceInput());
    }

    private void launchVoiceInput() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your library question…");
            startActivityForResult(intent, REQUEST_VOICE);
        } catch (Exception e) {
            SketchwareUtil.toast("Voice input not available on this device");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VOICE
                && resultCode == android.app.Activity.RESULT_OK
                && data != null) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty() && binding != null) {
                binding.libAiInput.setText(results.get(0));
                binding.libAiInput.setSelection(binding.libAiInput.getText().length());
            }
        }
    }

    // ── Empty state chips ─────────────────────────────────────────────────────
    private void setupEmptyStateChips() {
        binding.libAiChipUpdates.setOnClickListener(v ->
            fillInput("Find latest stable versions for all libraries in project " + scId
                + ". List each: current version → latest. Do not modify anything."));
        binding.libAiChipConflicts.setOnClickListener(v ->
            fillInput("Detect conflicts in project " + scId
                + ". List real issues with resolutions."));
        binding.libAiChipSearch.setOnClickListener(v ->
            fillInput("Search Maven Central for: [enter library name]\n"
                + "Return groupId:artifactId:version (latest stable)."));
        binding.libAiChipExplain.setOnClickListener(v ->
            fillInput("Explain this library: [enter library name]\n"
                + "Include: purpose, Android use cases, short code example."));
    }

    // ── Send / receive ────────────────────────────────────────────────────────
    private void sendUserMessage() {
        if (binding == null) return;
        String text = binding.libAiInput.getText() != null
            ? binding.libAiInput.getText().toString().trim() : "";
        if (text.isEmpty()) return;
        binding.libAiInput.setText("");
        pushUser(text);
        executeAi(text);
    }

    private void executeAi(String userText) {
        if (preferences == null) {
            pushAssistant("⚠️ AI not configured. Open AI Settings."); return;
        }
        AiProvider provider = preferences.getSelectedProvider();
        if (provider == null) {
            pushAssistant("⚠️ No provider selected. Open AI Settings."); return;
        }
        String apiKey = preferences.getApiKey(provider);
        if (provider.requiresApiKey() && (apiKey == null || apiKey.isEmpty())) {
            pushAssistant("⚠️ API key missing for " + provider.getDisplayName() + "."); return;
        }
        String modelId = preferences.getSelectedModel(provider);
        AiApiClient client = AiClientFactory.createClient(requireContext(), provider, apiKey);
        if (client == null) {
            pushAssistant("⚠️ Could not create AI client. Check AI Settings."); return;
        }

        startPulse();

        chatHistory.add(ChatMessage.userMessage(null, userText));
        List<ChatMessage> ctx = chatHistory.size() > 8
            ? chatHistory.subList(chatHistory.size() - 8, chatHistory.size())
            : new ArrayList<>(chatHistory);

        client.sendChatRequest(ctx, modelId, buildSystemPrompt(),
            new StreamingResponseHandler() {
                final StringBuilder buf = new StringBuilder();
                @Override public void onChunk(String d) { buf.append(d); }
                @Override public void onToolCall(ToolCall tc) { /* not used */ }
                @Override public void onComplete(String full) {
                    String reply = (full != null && !full.isEmpty()) ? full : buf.toString().trim();
                    requireActivity().runOnUiThread(() -> {
                        stopPulse();
                        if (!reply.isEmpty()) {
                            chatHistory.add(ChatMessage.assistantMessage(reply, null));
                            pushAssistant(reply);
                        }
                        if (actionListener != null) actionListener.onLibraryAction("ai_response");
                    });
                }
                @Override public void onError(String err) {
                    requireActivity().runOnUiThread(() -> { stopPulse(); pushAssistant("❌ " + err); });
                }
            });
    }

    // ── Pulse (10s timer while AI thinks) ────────────────────────────────────
    private void startPulse() {
        isAiThinking = true;
        pulseSeconds = 0;
        showTyping(true);
        pulseRunnable = new Runnable() {
            @Override public void run() {
                if (!isAiThinking || binding == null) return;
                pulseSeconds += 10;
                binding.libAiTypingText.setText("Thinking… " + pulseSeconds + "s");
                pulseHandler.postDelayed(this, 10_000);
            }
        };
        pulseHandler.postDelayed(pulseRunnable, 10_000);
    }

    private void stopPulse() {
        isAiThinking = false;
        pulseHandler.removeCallbacks(pulseRunnable);
        showTyping(false);
    }

    // ── System prompt ─────────────────────────────────────────────────────────
    private String buildSystemPrompt() {
        return "You are the Library Assistant inside Sketchware Pro (mobile Android IDE).\n"
            + "Project sc_id: " + scId + " | "
            + (notAssociated ? "Global library browser" : "Project library manager") + "\n\n"
            + "═══ MANDATORY PIPELINE — CATEGORY 6 (LIBRARY) ═══\n"
            + "1. list_libraries(sc_id) → know current state before anything\n"
            + "2. search_maven(name) → latest stable groupId:artifactId:version\n"
            + "3. validate_libraries(sc_id) → check conflicts before recommending\n"
            + "4. NEVER add or remove without explicit user confirmation ('yes'/'add it')\n"
            + "5. ALWAYS show exact Maven coordinates\n"
            + "6. Flag libraries incompatible with minSdk 21\n"
            + "7. If no network: use static known versions; flag as 'offline estimate'\n\n"
            + "═══ FORBIDDEN ═══\n"
            + "✗ Add/remove libraries without 'yes' from user\n"
            + "✗ Modify project files directly\n"
            + "✗ Recommend libraries requiring minSdk > 21\n"
            + "✗ More than 3 consecutive tool calls without reporting progress\n\n"
            + "FORMAT: short answers, tables for comparisons, exact Maven coords.\n"
            + "Reply in the same language the user writes in.";
    }

    // ── UI helpers ────────────────────────────────────────────────────────────
    private void pushUser(String text) {
        chatHistory.add(ChatMessage.userMessage(null, text));
        notifyAndScroll();
        showEmpty(false);
    }

    private void pushAssistant(String text) {
        chatHistory.add(ChatMessage.assistantMessage(text, null));
        notifyAndScroll();
        showEmpty(false);
    }

    private void notifyAndScroll() {
        if (binding == null) return;
        requireActivity().runOnUiThread(() -> {
            chatAdapter.notifyItemInserted(chatHistory.size() - 1);
            binding.libAiMessages.smoothScrollToPosition(chatHistory.size() - 1);
        });
    }

    private void showTyping(boolean show) {
        if (binding == null) return;
        requireActivity().runOnUiThread(() ->
            binding.libAiTypingIndicator.setVisibility(show ? View.VISIBLE : View.GONE));
    }

    private void showEmpty(boolean show) {
        if (binding == null) return;
        binding.libAiEmpty.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override public void onDestroyView() {
        stopPulse();
        super.onDestroyView();
        binding = null;
    }
}
