package pro.sketchware.ai.shared;

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
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pro.sketchware.ai.activities.AiSettingsActivity;
import pro.sketchware.ai.engine.AgentExecutor;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.AiProviderModels;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.Conversation;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.storage.ConversationManager;
import pro.sketchware.databinding.DesignAiBottomSheetBinding;
import pro.sketchware.utility.SketchwareUtil;

/**
 * AiAssistantBottomSheet — ONE unified AI sheet for ALL pages.
 *
 * Uses design_ai_bottom_sheet.xml (Design editor's layout).
 * Behaviour is configured per-page via AiPageConfig.
 *
 *   • Sidebar: OPEN (220dp) by default, categories COLLAPSED
 *   • BottomSheet locked EXPANDED — RecyclerView scrolls, sheet never shrinks
 *   • Tool tap → fills input field; user edits then taps Send
 *   • DIRECT tools → system executes on Send + shows result in chat
 *   • AI tools → AI responds on Send
 *   • Mic → RecognizerIntent, Locale.getDefault() (auto language)
 *   • Model chip → provider → model picker dialog
 *   • Pulse: every 10 s while AI thinks, typing text shows elapsed time
 *   • Attach button hidden (design decision for all pages)
 */
public class AiAssistantBottomSheet extends BottomSheetDialogFragment {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int  SIDEBAR_COLLAPSED_DP = 48;
    private static final int  SIDEBAR_EXPANDED_DP  = 220;
    private static final int  REQUEST_VOICE        = 0x7E1C;
    private static final long PULSE_INTERVAL_MS    = 10_000L;

    // ── Factory ──────────────────────────────────────────────────────────────
    private AiPageConfig config;

    public static AiAssistantBottomSheet newInstance(@NonNull AiPageConfig config) {
        AiAssistantBottomSheet f = new AiAssistantBottomSheet();
        f.config = config;
        return f;
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private DesignAiBottomSheetBinding binding;
    private boolean sidebarExpanded = true;  // open by default
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private AiChatAdapter      chatAdapter;
    private AiSidebarAdapter   sidebarAdapter;
    private AiPreferences      preferences;
    private ConversationManager conversationManager;
    private String              conversationId;
    @Nullable private String pendingDirectKey = null; // set when user taps a DIRECT tool

    // Agent executor
    private AgentExecutor agentExecutor;
    private boolean       isAgentRunning = false;

    // Pulse
    private final Handler  pulseHandler  = new Handler(Looper.getMainLooper());
    private       Runnable pulseRunnable;
    private       int      pulseSeconds  = 0;
    private       boolean  isThinking    = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL,
            com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog);
        if (getContext() != null) {
            preferences         = AiPreferences.getInstance(getContext());
            conversationManager = new ConversationManager(getContext());
            // Restore persisted conversation for this page so dismiss→reopen preserves history.
            String pageKey = config != null
                    ? (config.pageTitle + "_" + config.workspaceId).replaceAll("[^A-Za-z0-9_-]", "_")
                    : "assistant";
            String wsId = "assistant_" + pageKey;
            java.util.List<Conversation> existing = conversationManager
                    .getConversationsForWorkspace(wsId);
            if (!existing.isEmpty()) {
                Conversation cv = existing.get(existing.size() - 1);
                conversationId = cv.getId();
                java.util.List<ChatMessage> saved = conversationManager.getMessages(conversationId);
                chatHistory.addAll(saved);
            } else {
                Conversation cv = new Conversation(wsId,
                        config != null ? config.pageTitle : "AI Assistant",
                        null, null);
                conversationManager.saveConversation(cv);
                conversationId = cv.getId();
            }
        }
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DesignAiBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        lockExpanded();
        setupHeader();
        setupSidebar();
        setupChat();
        setupInput();
        setupEmptyChips();
        // Apply initial sidebar width (220dp = open)
        applyWidth(dpToPx(SIDEBAR_EXPANDED_DP));
        sidebarAdapter.setShowLabels(true);
    }

    // ── BottomSheet: 82% height, drag only when chat is at top ────────────────
    private void lockExpanded() {
        if (!(getDialog() instanceof BottomSheetDialog)) return;
        FrameLayout sheet = ((BottomSheetDialog) getDialog())
            .findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;

        // 82% of screen height — matches AiProjectBottomSheet
        int targetH = (int) (getResources().getDisplayMetrics().heightPixels * 0.82f);
        ViewGroup.LayoutParams lp = sheet.getLayoutParams();
        lp.height = targetH;
        sheet.setLayoutParams(lp);

        BottomSheetBehavior<FrameLayout> b = BottomSheetBehavior.from(sheet);
        b.setPeekHeight(targetH);
        b.setState(BottomSheetBehavior.STATE_EXPANDED);
        b.setSkipCollapsed(true);
        // Fix drag/scroll conflict: allow sheet drag only when chat RecyclerView is at the top.
        // When the user scrolls down in the chat, dragging is disabled so the sheet doesn't close.
        b.setDraggable(true);
        binding.aiSheetMessages.setNestedScrollingEnabled(true);
        binding.aiSheetMessages.addOnScrollListener(
            new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView rv, int dx, int dy) {
                    // canScrollVertically(-1) = true means there is content above (not at top)
                    b.setDraggable(!rv.canScrollVertically(-1));
                }
            });
    }

    // ── Header ────────────────────────────────────────────────────────────────
    private void setupHeader() {
        if (config != null) {
            binding.aiSheetTitle.setText(config.pageTitle);
            binding.aiSheetActivityName.setText(config.scopeLabel);
        }
        refreshProviderChip();

        binding.aiSheetModelSelector.setOnClickListener(v -> showProviderPicker());
        binding.aiSheetBtnSettings.setOnClickListener(v -> {
            if (getContext() != null)
                startActivity(new Intent(getContext(), AiSettingsActivity.class));
        });
        binding.aiSheetBtnClearReal.setOnClickListener(v -> {
            int oldSize = chatHistory.size();
            chatHistory.clear();
            chatAdapter.notifyItemRangeRemoved(0, oldSize);
            if (conversationManager != null && conversationId != null) {
                conversationManager.deleteMessages(conversationId);
            }
            showEmpty(true);
        });
        binding.aiSheetBtnClose.setOnClickListener(v -> dismiss());
        // Hide undo layout (library/other pages don't need it)
        binding.aiSheetBtnClear.setVisibility(View.GONE);
        // Undo button hidden
        if (binding.aiBtnUndoLayout != null)
            binding.aiBtnUndoLayout.setVisibility(View.GONE);
    }

    private void refreshProviderChip() {
        if (preferences == null) return;
        AiProvider p = preferences.getSelectedProvider();
        if (p == null) return;
        binding.aiSheetProviderName.setText(p.getDisplayName());
        String m = preferences.getSelectedModel(p);
        binding.aiSheetModelChip.setText(m.length() > 18 ? m.substring(0, 15) + "…" : m);
    }

    /** Show enabled models in a BottomSheetDialog — exact same style as AiProjectBottomSheet. */
    private void showProviderPicker() {
        if (preferences == null) return;

        // Collect all enabled providers' models into a flat list (same as Design page)
        java.util.List<pro.sketchware.ai.models.ModelInfo> all = new java.util.ArrayList<>();
        for (AiProvider p : AiProvider.values()) {
            if (!preferences.prefs().getBoolean("provider_enabled_" + p.name(), true)) continue;
            if (p.requiresApiKey() && !preferences.hasApiKey(p)) continue;
            // Use cached models first, fall back to static list
            java.util.List<pro.sketchware.ai.models.ModelInfo> cached = preferences.getCachedModels(p);
            if (cached != null && !cached.isEmpty()) {
                all.addAll(cached);
            } else {
                for (String id : AiProviderModels.getStaticModels(p)) {
                    all.add(new pro.sketchware.ai.models.ModelInfo(id, id, p, 0L, null));
                }
            }
        }

        if (all.isEmpty()) {
            SketchwareUtil.toast("No providers enabled. Open AI Settings to enable one.");
            return;
        }

        // Show BottomSheetDialog with RecyclerView — matches Design page exactly
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        androidx.recyclerview.widget.RecyclerView rv = new androidx.recyclerview.widget.RecyclerView(requireContext());
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 16, 0, 32);

        pro.sketchware.ai.adapters.ModelSelectorAdapter adapter =
            new pro.sketchware.ai.adapters.ModelSelectorAdapter(model -> {
                preferences.setSelectedProvider(model.getProvider());
                preferences.setSelectedModel(model.getProvider(), model.getId());
                refreshProviderChip();
                dialog.dismiss();
            });
        adapter.setSelectedModelId(preferences.getSelectedModel(preferences.getSelectedProvider()));
        adapter.setModels(all);
        rv.setAdapter(adapter);
        dialog.setContentView(rv);
        dialog.show();
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────
    private void setupSidebar() {
        List<AiPageConfig.Tool> tools = config != null ? config.tools : new ArrayList<>();
        sidebarAdapter = new AiSidebarAdapter(tools, tool -> {
            if (tool.inputTemplate == null) return;
            // Fill input field — user edits then sends
            binding.aiSheetInput.setText(tool.inputTemplate);
            binding.aiSheetInput.setSelection(binding.aiSheetInput.getText().length());
            binding.aiSheetInput.requestFocus();
            pendingDirectKey = (tool.type == AiPageConfig.ToolType.DIRECT)
                ? tool.actionKey : null;
            showEmpty(chatHistory.isEmpty());
            // Collapse sidebar so the chat area is fully visible
            if (sidebarExpanded) toggleSidebar();
        });

        binding.aiToolsSidebarRv.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.aiToolsSidebarRv.setAdapter(sidebarAdapter);
        binding.aiSidebarToggle.setOnClickListener(v -> toggleSidebar());
    }

    private void toggleSidebar() {
        sidebarExpanded = !sidebarExpanded;
        int fromPx = dpToPx(sidebarExpanded ? SIDEBAR_COLLAPSED_DP : SIDEBAR_EXPANDED_DP);
        int toPx   = dpToPx(sidebarExpanded ? SIDEBAR_EXPANDED_DP  : SIDEBAR_COLLAPSED_DP);
        ValueAnimator anim = ValueAnimator.ofInt(fromPx, toPx);
        anim.setDuration(220);
        anim.addUpdateListener(a -> applyWidth((int) a.getAnimatedValue()));
        anim.start();
        sidebarAdapter.setShowLabels(sidebarExpanded);
    }

    private void applyWidth(int widthPx) {
        ViewGroup.LayoutParams lp = binding.aiSidebarContainer.getLayoutParams();
        lp.width = widthPx;
        binding.aiSidebarContainer.setLayoutParams(lp);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ── Chat ──────────────────────────────────────────────────────────────────
    private void setupChat() {
        chatAdapter = new AiChatAdapter(chatHistory);
        LinearLayoutManager lm = new LinearLayoutManager(getContext());
        lm.setStackFromEnd(true);
        binding.aiSheetMessages.setLayoutManager(lm);
        binding.aiSheetMessages.setAdapter(chatAdapter);

        binding.aiSheetMessages.addOnScrollListener(
            new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                @Override public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView rv, int dx, int dy) {
                    LinearLayoutManager llm = (LinearLayoutManager) rv.getLayoutManager();
                    if (llm == null) return;
                    boolean atBottom = llm.findLastCompletelyVisibleItemPosition()
                        >= chatAdapter.getItemCount() - 1;
                    binding.aiSheetFabScrollDown.setVisibility(atBottom ? View.GONE : View.VISIBLE);
                }
            });
        binding.aiSheetFabScrollDown.setOnClickListener(v ->
            binding.aiSheetMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1));
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    private void setupInput() {
        if (config != null) binding.aiSheetInput.setHint(config.inputHint);

        // Hide attach button for all pages
        binding.aiSheetBtnAttach.setVisibility(View.GONE);

        binding.aiSheetInput.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEND) { onSend(); return true; }
            return false;
        });
        binding.aiSheetBtnSend.setOnClickListener(v -> {
            if (isAgentRunning) stopAgent();
            else onSend();
        });
        binding.aiSheetBtnMic.setOnClickListener(v -> launchMic());
    }

    private void stopAgent() {
        if (agentExecutor != null) agentExecutor.shutdown();
    }

    private void setAgentRunning(boolean running) {
        isAgentRunning = running;
        if (binding == null) return;
        binding.aiSheetBtnSend.setIconResource(running
                ? pro.sketchware.R.drawable.ic_mtrl_cancel
                : pro.sketchware.R.drawable.ic_chat_send);
    }

    private void onSend() {
        if (binding.aiSheetInput.getText() == null) return;
        String text = binding.aiSheetInput.getText().toString().trim();
        if (text.isEmpty()) return;
        binding.aiSheetInput.setText("");

        String directKey = pendingDirectKey;
        pendingDirectKey = null;

        pushUser(text);

        if (directKey != null && config != null && config.directActionHandler != null) {
            // DIRECT tool: show "executing" then run + show result
            String toolLabel = getToolLabel(directKey);
            pushAssistant("⚡ Running: " + toolLabel + "…");
            final String key = directKey;
            new Thread(() -> {
                String result = config.directActionHandler.execute(key, text);
                String reply = (result != null && !result.isEmpty())
                    ? result : "✅ " + toolLabel + " complete.";
                if (isAdded()) requireActivity().runOnUiThread(() -> pushAssistant(reply));
            }).start();
        } else {
            sendToAi(text);
        }
    }

    private String getToolLabel(String actionKey) {
        if (config == null || actionKey == null) return actionKey;
        for (AiPageConfig.Tool t : config.tools)
            if (actionKey.equals(t.actionKey)) return t.label;
        return actionKey;
    }

    // ── Mic: RecognizerIntent with device locale (auto language) ──────────────
    private void launchMic() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            // Auto-detect: use device's default locale, NOT forced English
            String langTag = Locale.getDefault().toLanguageTag();
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag);
            intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message…");
            startActivityForResult(intent, REQUEST_VOICE);
        } catch (Exception e) {
            SketchwareUtil.toast("Voice input not available");
        }
    }

    @Override
    public void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQUEST_VOICE && res == android.app.Activity.RESULT_OK && data != null) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty() && binding != null) {
                String spoken = results.get(0);
                binding.aiSheetInput.setText(spoken);
                binding.aiSheetInput.setSelection(spoken.length());
            }
        }
    }

    // ── Empty state chips — wired to first 4 non-category tools ──────────────
    private void setupEmptyChips() {
        Chip[] chips = {
            binding.aiChipGenerateUi, binding.aiChipFixBugs,
            binding.aiChipAddFeature, binding.aiChipExplain
        };

        List<AiPageConfig.Tool> quick = new ArrayList<>();
        if (config != null) {
            for (AiPageConfig.Tool t : config.tools) {
                if (t.type != AiPageConfig.ToolType.CATEGORY && t.inputTemplate != null) {
                    quick.add(t);
                    if (quick.size() == 4) break;
                }
            }
        }

        for (int i = 0; i < chips.length; i++) {
            if (i < quick.size()) {
                final AiPageConfig.Tool tool = quick.get(i);
                chips[i].setText(tool.label);
                chips[i].setVisibility(View.VISIBLE);
                chips[i].setOnClickListener(v -> {
                    binding.aiSheetInput.setText(tool.inputTemplate);
                    binding.aiSheetInput.setSelection(binding.aiSheetInput.getText().length());
                    pendingDirectKey = (tool.type == AiPageConfig.ToolType.DIRECT)
                        ? tool.actionKey : null;
                    showEmpty(false);
                });
            } else {
                chips[i].setVisibility(View.GONE);
            }
        }
    }

    // ── AI call (AgentExecutor — full tool execution + pulse) ────────────────
    private void sendToAi(String userText) {
        if (isAgentRunning) return;
        if (preferences == null) { pushAssistant("⚠️ AI not configured. Open AI Settings."); return; }
        AiProvider provider = preferences.getSelectedProvider();
        if (provider == null) { pushAssistant("⚠️ No provider selected. Open AI Settings."); return; }
        String apiKey = preferences.getApiKey(provider);
        if (provider.requiresApiKey() && (apiKey == null || apiKey.isEmpty())) {
            pushAssistant("⚠️ API key missing for " + provider.getDisplayName() + "."); return;
        }
        String modelId = preferences.getSelectedModel(provider);

        List<String> projectIds = config != null ? config.projectIds : new ArrayList<>();
        String workspaceId = config != null && !config.workspaceId.isEmpty()
                ? config.workspaceId : "ai_assistant";
        String scopedId = projectIds.isEmpty() ? null : projectIds.get(0);
        String scope = scopedId != null ? AgentExecutor.SCOPE_PROJECT : AgentExecutor.SCOPE_GLOBAL;

        setAgentRunning(true);
        startPulse();

        chatHistory.add(ChatMessage.userMessage(null, userText));
        String sysPrompt = config != null ? config.systemPrompt : "";

        agentExecutor = new AgentExecutor(requireContext(), projectIds, workspaceId, scope, scopedId);
        agentExecutor.setPulseCallback((summary, onContinue, onCancel) -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() ->
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Step completed")
                            .setMessage(summary)
                            .setPositiveButton("Continue", (d, w) -> onContinue.run())
                            .setNegativeButton("Stop", (d, w) -> onCancel.run())
                            .setCancelable(false)
                            .show());
        });
        agentExecutor.execute(chatHistory, modelId, provider, sysPrompt,
                projectIds, workspaceId, null, new AgentExecutor.AgentCallback() {
            @Override public void onStreamingChunk(String chunk) {}
            @Override public void onAssistantMessage(ChatMessage msg) {
                if (msg != null && msg.getContent() != null && !msg.getContent().isEmpty()) {
                    if (isAdded()) requireActivity().runOnUiThread(() -> {
                        chatHistory.add(msg);
                        pushAssistant(msg.getContent());
                    });
                }
            }
            @Override public void onToolCallStarted(ToolCall tc) {
                if (tc != null && isAdded()) requireActivity().runOnUiThread(() ->
                        binding.aiSheetTypingText.setText("⚙ " + tc.getName() + "…"));
            }
            @Override public void onToolCallProgress(String id, String status, int p, boolean ind) {
                if (status != null && !status.isEmpty() && isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding != null) binding.aiSheetTypingText.setText(status);
                    });
                }
            }
            @Override public void onToolCallCompleted(ToolCall tc, ToolResult result) {}
            @Override public void onToolMessage(ChatMessage msg) {}
            @Override public void onResponseComplete(ChatMessage msg) {
                if (isAdded()) requireActivity().runOnUiThread(() -> {
                    setAgentRunning(false);
                    stopPulse();
                });
            }
            @Override public void onCancelled() {
                if (isAdded()) requireActivity().runOnUiThread(() -> {
                    setAgentRunning(false);
                    stopPulse();
                    pushAssistant("⛔ Stopped.");
                });
            }
            @Override public void onError(String error) {
                if (isAdded()) requireActivity().runOnUiThread(() -> {
                    setAgentRunning(false);
                    stopPulse();
                    pushAssistant("❌ " + error);
                });
            }
            @Override public void onThinking(String status) {
                if (isAdded()) requireActivity().runOnUiThread(() ->
                        binding.aiSheetTypingText.setText(status != null ? status : "Thinking…"));
            }
            @Override public void onFailover(String fromProvider, String toProvider, String toModel) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    // Update provider / model chip in header
                    if (binding != null) {
                        binding.aiSheetProviderName.setText(toProvider);
                        String display = toModel != null && toModel.length() > 18
                                ? toModel.substring(0, 15) + "…" : toModel;
                        if (display != null) binding.aiSheetModelChip.setText(display);
                    }
                    // Post a system notification in chat so the user can see the switch
                    pushAssistant("⚡ Switched to " + toModel + " (" + toProvider + ")");
                });
            }
        });
    }

    // ── Pulse: 10-second timer while AI is thinking ───────────────────────────
    private void startPulse() {
        isThinking = true;
        pulseSeconds = 0;
        showTyping(true);
        pulseRunnable = new Runnable() {
            @Override public void run() {
                if (!isThinking || binding == null) return;
                pulseSeconds += 10;
                binding.aiSheetTypingText.setText("Thinking… " + pulseSeconds + "s");
                pulseHandler.postDelayed(this, PULSE_INTERVAL_MS);
            }
        };
        pulseHandler.postDelayed(pulseRunnable, PULSE_INTERVAL_MS);
    }

    private void stopPulse() {
        isThinking = false;
        pulseHandler.removeCallbacks(pulseRunnable);
        showTyping(false);
    }

    // ── UI helpers ────────────────────────────────────────────────────────────
    private void pushUser(String text) {
        ChatMessage msg = ChatMessage.userMessage(conversationId, text);
        chatHistory.add(msg);
        if (conversationManager != null && conversationId != null) {
            conversationManager.saveMessage(conversationId, msg);
        }
        notifyInsert();
        showEmpty(false);
    }

    private void pushAssistant(String text) {
        ChatMessage msg = ChatMessage.assistantMessage(text, null);
        chatHistory.add(msg);
        if (conversationManager != null && conversationId != null) {
            conversationManager.saveMessage(conversationId, msg);
        }
        notifyInsert();
        showEmpty(false);
    }

    private void notifyInsert() {
        if (binding == null) return;
        requireActivity().runOnUiThread(() -> {
            chatAdapter.notifyItemInserted(chatHistory.size() - 1);
            binding.aiSheetMessages.smoothScrollToPosition(chatHistory.size() - 1);
        });
    }

    private void showTyping(boolean show) {
        if (binding == null) return;
        requireActivity().runOnUiThread(() ->
            binding.aiSheetTypingIndicator.setVisibility(show ? View.VISIBLE : View.GONE));
    }

    private void showEmpty(boolean show) {
        if (binding == null) return;
        binding.aiSheetEmpty.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override public void onDestroyView() {
        stopPulse();
        if (agentExecutor != null) agentExecutor.shutdown();
        super.onDestroyView();
        binding = null;
    }
}
