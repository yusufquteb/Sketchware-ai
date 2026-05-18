package pro.sketchware.ai.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.ai.activities.AiSettingsActivity;
import pro.sketchware.ai.adapters.ChatAdapter;
import pro.sketchware.ai.adapters.ModelSelectorAdapter;
import pro.sketchware.ai.engine.AgentExecutor;
import pro.sketchware.ai.engine.TokenOptimizer;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.AiProviderModels;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.Conversation;
import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.models.Workspace;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.storage.ConversationManager;
import pro.sketchware.ai.storage.WorkspaceManager;
import pro.sketchware.databinding.ActivityChatBinding;
import pro.sketchware.databinding.DialogModelSelectorBinding;

public class ChatActivity extends AppCompatActivity implements AgentExecutor.AgentCallback,
        ChatAdapter.OnArtifactActionListener {

    public static final String EXTRA_CONVERSATION_ID = "conversation_id";
    public static final String EXTRA_WORKSPACE_ID = "workspace_id";
    public static final String EXTRA_INITIAL_PROMPT = "initial_prompt";
    /** Pass a single project scId to launch the assistant in in-project scope. */
    public static final String EXTRA_PROJECT_ID = "project_id";
    /** Optional page context hint (e.g. "errors", "blocks") so the model knows where it was launched from. */
    public static final String EXTRA_PAGE_CONTEXT = "page_context";

    private ActivityChatBinding binding;
    private ConversationManager conversationManager;
    private WorkspaceManager workspaceManager;
    private AiPreferences preferences;
    private ChatAdapter chatAdapter;
    private AgentExecutor agentExecutor;

    private String conversationId;
    private String workspaceId;
    /** Non-null when launched from inside a project (in-project scope). */
    private String scopedProjectId;
    /** Optional page context (e.g. "errors", "blocks") injected into system prompt. */
    private String pageContext;
    private Conversation conversation;
    private Workspace workspace;

    private AiProvider currentProvider;
    private String currentModelId;
    private boolean isAgentRunning = false;

    private static final int REQUEST_SPEECH_INPUT = 1001;
    private static final int REQUEST_FILE_PICK    = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       

        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ✅ FIX (TRD §4): Apply bottom insets so the input bar never hides behind the
        // Android navigation bar or soft keyboard (adjustResize is set in Manifest).
        setupKeyboardHandling();

        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        workspaceId = getIntent().getStringExtra(EXTRA_WORKSPACE_ID);
        scopedProjectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        pageContext = getIntent().getStringExtra(EXTRA_PAGE_CONTEXT);

        if (conversationId == null || workspaceId == null) {
            finish();
            return;
        }

        conversationManager = new ConversationManager(this);
        workspaceManager = new WorkspaceManager(this);
        preferences = AiPreferences.getInstance(this);

        conversation = conversationManager.getConversation(conversationId, workspaceId);
        workspace = workspaceManager.getWorkspace(workspaceId);

        if (conversation == null) {
            // Toast removed: "Conversation not found"
            finish();
            return;
        }

        setupToolbar();
        setupChat();
        setupInput();
       
        loadModelInfo();
        loadMessages();
        maybeSendInitialPrompt(savedInstanceState);
        setupScrollToBottomFab();
        setupTokenBadge();
    }

    /**
     * Applies bottom WindowInsets to the input bar container so it is always rendered
     * above the Android navigation bar (gesture bar or 3-button nav) AND above the
     * soft keyboard.
     *
     * This is the correct fix for the send button and text input being hidden behind
     * the nav bar or keyboard. Works in conjunction with:
     *   - android:windowSoftInputMode="adjustResize" in AndroidManifest.xml
     *   - CoordinatorLayout + fitsSystemWindows="true" in the layout
     *
     * TRD §4 — Keyboard Handling (CRITICAL)
     */
    private void setupKeyboardHandling() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.inputBarContainer, (view, insets) -> {
            int bottomInset = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()
            ).bottom;
            view.setPadding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    bottomInset
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void maybeSendInitialPrompt(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            return;
        }
        if (conversationManager == null || conversationManager.getMessages(conversationId).size() > 0) {
            return;
        }
        String initialPrompt = getIntent().getStringExtra(EXTRA_INITIAL_PROMPT);
        if (initialPrompt == null || initialPrompt.trim().isEmpty()) {
            return;
        }
        binding.inputMessage.setText(initialPrompt.trim());
        binding.inputMessage.post(this::sendMessage);
    }

    private void setupToolbar() {
        // Ask the user whether to keep the conversation before closing.
        // (Conversations are auto-saved after every message, but we still ask
        //  so the user can actively discard an unwanted one.)
        binding.toolbar.setNavigationOnClickListener(v -> showExitDialog());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitDialog();
            }
        });
        binding.toolbarTitle.setText(conversation.getTitle());
        binding.modelSelectorRow.setOnClickListener(v -> showModelSelector());
        // Settings button in toolbar
        binding.toolbar.inflateMenu(R.menu.menu_chat);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_chat_settings) {
                startActivity(new Intent(this, AiSettingsActivity.class));
                return true;
            }
            if (item.getItemId() == R.id.action_clear_chat) {
                confirmClearConversation();
                return true;
            }
            return false;
        });
    }

    private void setupChat() {
        chatAdapter = new ChatAdapter();
        chatAdapter.setArtifactActionListener(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.messagesList.setLayoutManager(layoutManager);
        binding.messagesList.setAdapter(chatAdapter);
    }

    private void setupInput() {
        binding.btnSend.setOnClickListener(v -> {
            if (isAgentRunning) {
                stopAgent();
            } else {
                sendMessage();
            }
        });
        binding.btnSend.setEnabled(false);

        binding.inputMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshComposerState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Mic button — launch speech recogniser
        binding.btnMic.setOnClickListener(v -> startSpeechInput());

        // Clear button — confirm then wipe conversation
        binding.btnClear.setOnClickListener(v -> confirmClearConversation());

        // Attach button — file picker for text/JSON/Java/XML
        binding.btnAttach.setOnClickListener(v -> openFilePicker());

        // Tools button — shows the AI capability catalogue
        if (binding.btnTools != null) {
            binding.btnTools.setOnClickListener(v ->
                pro.sketchware.ai.bottomsheet.AiToolsBottomSheet.show(this, tool -> {
                    // Pre-fill input with a helpful prompt about the chosen tool
                    String prompt = "Use the \"" + tool.name + "\" tool to help me: ";
                    binding.inputMessage.setText(prompt);
                    binding.inputMessage.setSelection(prompt.length());
                    binding.inputMessage.requestFocus();
                })
            );
        }
    }

    private void startSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message…");
        try {
            startActivityForResult(intent, REQUEST_SPEECH_INPUT);
        } catch (ActivityNotFoundException e) {
            // Toast removed: "Speech recognition not available on this device."
        }
    }

    private void confirmClearConversation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear conversation")
                .setMessage("This will delete all messages in this conversation. This cannot be undone.")
                .setPositiveButton("Clear", (d, w) -> {
                    conversationManager.deleteMessages(conversationId);
                    chatAdapter.setMessages(new ArrayList<>());
                    updateEmptyState();
                    // Toast removed: "Conversation cleared."
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/plain", "application/json", "text/x-java-source", "text/xml", "application/xml"
        });
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select file to attach"), REQUEST_FILE_PICK);
        } catch (ActivityNotFoundException e) {
            // Toast removed: "No file manager found."
        }
    }

    // ── Exit / Save dialog ──────────────────────────────────────────────────
    /**
     * Shown when the user taps the back arrow or device back button.
     * Conversations are already auto-saved after every message; this dialog
     * lets the user explicitly discard the conversation if they don't want it.
     */
    private void showExitDialog() {
        if (chatAdapter == null || chatAdapter.getItemCount() == 0) {
            // Nothing to save — exit immediately
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Exit conversation")
                .setMessage("Your conversation is saved automatically.\nDo you want to keep it?")
                .setPositiveButton("Keep & Exit", (d, w) -> finish())
                .setNegativeButton("Delete & Exit", (d, w) -> {
                    // Delete this conversation from storage then exit
                    if (conversationManager != null && conversationId != null && workspaceId != null) {
                        conversationManager.deleteConversation(conversationId, workspaceId);
                    }
                    finish();
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    // ── Scroll-to-bottom FAB ─────────────────────────────────────────────────
    /**
     * Shows a floating "scroll to bottom" button whenever the user scrolls up
     * far enough that the last message is off-screen. Tapping it smooth-scrolls
     * to the latest message and hides the button again.
     *
     * The FAB is injected programmatically so no layout XML changes are needed.
     */
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabScrollDown;

    private void setupScrollToBottomFab() {
        // Use the FAB already declared in activity_chat.xml
        fabScrollDown = binding.fabScrollDown;
        fabScrollDown.setVisibility(View.GONE);
        fabScrollDown.setOnClickListener(v -> scrollToBottom(true));

        binding.messagesList.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView rv, int dx, int dy) {
                androidx.recyclerview.widget.LinearLayoutManager lm =
                        (androidx.recyclerview.widget.LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int lastVisible = lm.findLastCompletelyVisibleItemPosition();
                int total = rv.getAdapter() != null ? rv.getAdapter().getItemCount() : 0;
                boolean atBottom = (total == 0) || (lastVisible >= total - 1);
                if (dy < 0) userScrolledUp = true;
                if (atBottom) userScrolledUp = false;
                fabScrollDown.setVisibility(atBottom ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void scrollToBottom() {
        scrollToBottom(false);
    }

    private void scrollToBottom(boolean force) {
        if (!force && userScrolledUp) return;
        int count = chatAdapter != null ? chatAdapter.getItemCount() : 0;
        if (count > 0) {
            binding.messagesList.smoothScrollToPosition(count - 1);
        }
    }

    // ── Pulse Confirmation System ────────────────────────────────────────────
    /**
     * Shows a plan dialog before the AI executes a major action.
     * Continues automatically after 30 seconds if user doesn't respond.
     */
    private void showPulseConfirmation(String plan, Runnable onContinue, Runnable onCancel) {
        if (isFinishing() || isDestroyed()) { onContinue.run(); return; }
        android.widget.LinearLayout view = new android.widget.LinearLayout(this);
        view.setOrientation(android.widget.LinearLayout.VERTICAL);
        int p = (int)(16 * getResources().getDisplayMetrics().density);
        view.setPadding(p, p, p, p);

        android.widget.TextView planTv = new android.widget.TextView(this);
        planTv.setText(plan);
        planTv.setTextSize(14f);
        planTv.setLineSpacing(0, 1.4f);
        view.addView(planTv);

        android.widget.ProgressBar bar = new android.widget.ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(30); bar.setProgress(30);
        android.widget.LinearLayout.LayoutParams lp2 = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = (int)(12 * getResources().getDisplayMetrics().density);
        bar.setLayoutParams(lp2);
        view.addView(bar);

        android.widget.TextView timerTv = new android.widget.TextView(this);
        timerTv.setText("Continuing in 30s…");
        timerTv.setTextSize(12f);
        timerTv.setTextColor(0xFF9E9E9E);
        view.addView(timerTv);

        final boolean[] decided = {false};
        androidx.appcompat.app.AlertDialog dialog =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("⚡ AI Plan")
                        .setView(view)
                        .setCancelable(false)
                        .setPositiveButton("Continue ▶", (d, w) -> { decided[0] = true; onContinue.run(); })
                        .setNegativeButton("Cancel", (d, w) -> { decided[0] = true; onCancel.run(); })
                        .create();
        dialog.show();

        final int[] left = {30};
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] tick = {null};
        tick[0] = () -> {
            if (decided[0] || !dialog.isShowing()) return;
            left[0]--;
            bar.setProgress(left[0]);
            timerTv.setText("Continuing in " + left[0] + "s…");
            if (left[0] <= 0) { decided[0] = true; dialog.dismiss(); onContinue.run(); }
            else h.postDelayed(tick[0], 1000);
        };
        h.postDelayed(tick[0], 1000);
    }

        // ── Token Optimizer UI ───────────────────────────────────────────────────
    /** True when user has scrolled up manually — suppresses auto-scroll during streaming. */
    private boolean userScrolledUp = false;

    private android.widget.TextView tokenBadge;

    private void setupTokenBadge() {
        tokenBadge = new android.widget.TextView(this);
        tokenBadge.setTextSize(10f);
        tokenBadge.setTextColor(0xFF9d8ec0);
        tokenBadge.setPadding(8, 2, 8, 2);
        tokenBadge.setVisibility(android.view.View.GONE);
        binding.toolbar.addView(tokenBadge);
    }

    /**
     * Updates the token badge in the toolbar showing the optimised vs raw message count.
     * Visible only when TokenOptimizer actually reduced the history.
     */
    private void updateTokenBadge() {
        if (tokenBadge == null || conversationId == null || conversationManager == null) return;
        List<ChatMessage> history = conversationManager.getMessages(conversationId);
        if (history == null || history.isEmpty()) return;
        int raw  = history.size();
        int opts = TokenOptimizer.optimise(new java.util.ArrayList<>(history)).size();
        if (opts < raw) {
            tokenBadge.setText("⚡ " + opts + "/" + raw + " msgs");
            tokenBadge.setVisibility(android.view.View.VISIBLE);
        } else {
            tokenBadge.setVisibility(android.view.View.GONE);
        }
    }

    private void refreshComposerState() {
        if (isAgentRunning) {
            binding.btnSend.setEnabled(true);
            binding.btnSend.setIconResource(R.drawable.ic_mtrl_cancel);
            binding.btnSend.setContentDescription("Stop");
        } else {
            boolean hasInput = binding.inputMessage.getText() != null && binding.inputMessage.getText().length() > 0;
            binding.btnSend.setEnabled(hasInput);
            binding.btnSend.setIconResource(R.drawable.ic_send);
            binding.btnSend.setContentDescription("Send");
        }
    }

    private void stopAgent() {
        if (agentExecutor != null) {
            agentExecutor.cancel();
        }
        binding.typingText.setText("Stopping\u2026");
        setAgentRunning(false);
    }

    private void loadModelInfo() {
        String providerName = conversation.getProviderName();
        if (providerName != null && !providerName.isEmpty()) {
            currentProvider = AiProvider.fromName(providerName);
        }
        if (currentProvider == null) {
            currentProvider = preferences.getSelectedProvider();
        }

        String modelId = conversation.getModelId();
        if (modelId != null && !modelId.isEmpty()) {
            currentModelId = modelId;
        } else {
            currentModelId = preferences.getSelectedModel(currentProvider);
            if (currentModelId == null) {
                List<ModelInfo> models = preferences.getCachedModels(currentProvider);
                if (!models.isEmpty()) {
                    currentModelId = models.get(0).getId();
                }
            }
        }

        updateModelDisplay();
    }

    private void updateModelDisplay() {
        if (currentModelId != null && !currentModelId.isEmpty()) {
            String displayName = currentModelId;
            if (displayName.contains("/")) {
                displayName = displayName.substring(displayName.lastIndexOf('/') + 1);
            }
            binding.toolbarModel.setText(displayName);
        } else {
            binding.toolbarModel.setText("Select model");
        }
    }

    private void loadMessages() {
        List<ChatMessage> messages = conversationManager.getMessages(conversationId);
        chatAdapter.setMessages(messages);
        updateEmptyState();
        if (!messages.isEmpty()) {
            binding.messagesList.scrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    private void updateEmptyState() {
        boolean showEmpty = chatAdapter.getItemCount() == 0 && !isAgentRunning;
        binding.emptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        binding.messagesList.setVisibility(showEmpty ? View.INVISIBLE : View.VISIBLE);
    }

    private void sendMessage() {
        String text = binding.inputMessage.getText() != null
                ? binding.inputMessage.getText().toString().trim() : "";
        if (text.isEmpty() || isAgentRunning) return;

        if (currentModelId == null || currentModelId.isEmpty()) {
            // Toast removed
            showModelSelector();
            return;
        }

        String apiKey = preferences.getApiKey(currentProvider);
        if (currentProvider.requiresApiKey() && (apiKey == null || apiKey.isEmpty())) {
            // Toast removed
            return;
        }

        binding.inputMessage.setText("");

        ChatMessage userMsg = new ChatMessage(conversationId, text);
        conversationManager.saveMessage(conversationId, userMsg);
        chatAdapter.addUserMessage(userMsg);
        updateConversationTimestamp();
        updateEmptyState();
        scrollToBottom();

        if ("New Chat".equals(conversation.getTitle())) {
            String title = text.length() > 50 ? text.substring(0, 50) + "..." : text;
            conversation.setTitle(title);
            conversationManager.saveConversation(conversation);
            binding.toolbarTitle.setText(title);
        }

        ChatMessage assistantPlaceholder = new ChatMessage(conversationId, "", null);
        assistantPlaceholder.setStreaming(true);
        chatAdapter.addAssistantMessage(assistantPlaceholder);
        updateEmptyState();
        scrollToBottom();

        setAgentRunning(true);
        binding.typingText.setText("Thinking\u2026");

        List<ChatMessage> history = conversationManager.getMessages(conversationId);
        String systemPrompt = preferences.getSystemPrompt();
        List<String> projectIds;
        String scope;
        if (scopedProjectId != null && !scopedProjectId.isEmpty()) {
            // In-project scope: restrict tools to this project only
            projectIds = new ArrayList<>();
            projectIds.add(scopedProjectId);
            scope = AgentExecutor.SCOPE_PROJECT;
        } else {
            // Global scope: full access to all workspace projects
            projectIds = workspace != null ? workspace.getProjectIds() : new ArrayList<>();
            scope = AgentExecutor.SCOPE_GLOBAL;
        }

        agentExecutor = new AgentExecutor(this, projectIds, workspaceId, scope, scopedProjectId);
        // Wire Pulse: AI announces plan → user sees Continue/Cancel dialog with 30s timer
        agentExecutor.setPulseCallback((plan, onContinue, onCancel) ->
                runOnUiThread(() -> showPulseConfirmation(plan, onContinue, onCancel)));
        agentExecutor.execute(history, currentModelId, currentProvider, systemPrompt,
                projectIds, workspaceId, pageContext, this);
    }

    private void setAgentRunning(boolean running) {
        isAgentRunning = running;
        binding.inputMessage.setEnabled(!running);
        binding.typingIndicator.setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) {
            binding.emptyDescription.setText("The agent is planning and executing tools in this workspace.");
            // Start background service
            Intent serviceIntent = new Intent(this, pro.sketchware.ai.engine.AiBackgroundService.class);
            serviceIntent.putExtra(EXTRA_CONVERSATION_ID, conversationId);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } else {
            binding.emptyDescription.setText("Create or open a conversation and ask for a Sketchware-compatible app, feature, fix, or refactor.");
            // Stop background service
            Intent serviceIntent = new Intent(this, pro.sketchware.ai.engine.AiBackgroundService.class);
            stopService(serviceIntent);
        }
        refreshComposerState();
        updateEmptyState();
    }



    private void updateConversationTimestamp() {
        conversation.setUpdatedAt(System.currentTimeMillis());
        conversationManager.saveConversation(conversation);
    }

    private void persistToolMessage(ChatMessage toolMessage) {
        conversationManager.saveMessage(conversationId, toolMessage);
        updateConversationTimestamp();
    }

    private void showModelSelector() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        DialogModelSelectorBinding dialogBinding = DialogModelSelectorBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        // Only providers that are ENABLED in AI Settings AND ready (key or free)
        // LOCAL_LLM is excluded — it's configured separately in AI Settings
        List<AiProvider> availableProviders = new ArrayList<>();
        for (AiProvider p : AiProvider.values()) {
            if (p == AiProvider.LOCAL_LLM) continue;  // handled separately
            // Default-enabled mirrors AiSettingsActivity defaults
            boolean defaultEnabled = (p == AiProvider.GOOGLE_AI_STUDIO
                    || p == AiProvider.SAMBANOVA
                    || p == AiProvider.CHUTES);
            boolean enabled = preferences.prefs().getBoolean("provider_enabled_" + p.name(), defaultEnabled);
            if (!enabled) continue;
            if (!p.requiresApiKey() || preferences.hasApiKey(p)) {
                availableProviders.add(p);
            }
        }

        if (availableProviders.isEmpty()) {
            dialogBinding.emptyState.setVisibility(View.VISIBLE);
            dialogBinding.modelsList.setVisibility(View.GONE);
            dialogBinding.emptyText.setText("No providers enabled.\nGo to AI Settings to enable a provider and add an API key.");
            dialog.show();
            return;
        }

        // Tabs show "Groq \u221e" / "DeepInfra \uD83C\uDD13" labels
        for (AiProvider p : availableProviders) {
            dialogBinding.providerTabs.addTab(
                    dialogBinding.providerTabs.newTab().setText(p.getSelectorLabel()).setTag(p));
        }

        ModelSelectorAdapter modelAdapter = new ModelSelectorAdapter(model -> {
            currentProvider = model.getProvider();
            currentModelId  = model.getId();
            conversation.setModelId(currentModelId);
            conversation.setProviderName(currentProvider.name());
            conversationManager.saveConversation(conversation);
            preferences.setSelectedModel(currentProvider, currentModelId);
            preferences.setSelectedProvider(currentProvider);
            updateModelDisplay();
            dialog.dismiss();
        });

        // Long-press a model → show info dialog
        modelAdapter.setOnModelLongClickListener(model -> {
            showModelInfoDialog(model);
            return true;
        });

        modelAdapter.setSelectedModelId(currentModelId);
        dialogBinding.modelsList.setAdapter(modelAdapter);

        AiProvider firstProvider = availableProviders.get(0);
        for (int i = 0; i < availableProviders.size(); i++) {
            if (availableProviders.get(i) == currentProvider) {
                TabLayout.Tab tab = dialogBinding.providerTabs.getTabAt(i);
                if (tab != null) { tab.select(); firstProvider = currentProvider; }
                break;
            }
        }
        loadModelsForProvider(firstProvider, modelAdapter, dialogBinding);

        dialogBinding.providerTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                AiProvider p = (AiProvider) tab.getTag();
                if (p != null) loadModelsForProvider(p, modelAdapter, dialogBinding);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        dialog.show();
    }

    /** Long-press model \u2192 brief info dialog with provider description */
    private void showModelInfoDialog(ModelInfo model) {
        AiProvider provider = model.getProvider();
        String title = (model.getName() != null && !model.getName().isEmpty())
                ? model.getName() : model.getId();
        StringBuilder sb = new StringBuilder();
        sb.append("Provider: ").append(provider.getSelectorLabel()).append("\n\n");
        if (model.getContextLength() > 0) {
            long ctx = model.getContextLength();
            String ctxStr = ctx >= 1000 ? (ctx / 1000) + "k" : String.valueOf(ctx);
            sb.append("Context window: ").append(ctxStr).append(" tokens\n\n");
        }
        if (model.getDescription() != null && !model.getDescription().isEmpty()) {
            sb.append(model.getDescription()).append("\n\n");
        }
        sb.append(provider.getDescription());

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(sb.toString())
                .setPositiveButton("Use this model", (d, w) -> {
                    currentProvider = provider;
                    currentModelId  = model.getId();
                    conversation.setModelId(currentModelId);
                    conversation.setProviderName(currentProvider.name());
                    conversationManager.saveConversation(conversation);
                    preferences.setSelectedModel(currentProvider, currentModelId);
                    preferences.setSelectedProvider(currentProvider);
                    updateModelDisplay();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadModelsForProvider(AiProvider provider, ModelSelectorAdapter adapter,
                                       DialogModelSelectorBinding dialogBinding) {
        List<ModelInfo> cached = preferences.getCachedModels(provider);
        if (cached != null && !cached.isEmpty()) {
            adapter.setModels(cached);
            dialogBinding.modelsList.setVisibility(View.VISIBLE);
            dialogBinding.emptyState.setVisibility(View.GONE);
        } else {
            // FIX: Fall back to static built-in model list so BottomSheet always shows models
            // even before the user has refreshed in AI Settings.
            List<String> staticIds = AiProviderModels.getStaticModels(provider);
            if (!staticIds.isEmpty()) {
                List<ModelInfo> staticModels = new ArrayList<>();
                for (String id : staticIds) {
                    staticModels.add(new ModelInfo(id, id, provider, 0, "Built-in model"));
                }
                adapter.setModels(staticModels);
                dialogBinding.modelsList.setVisibility(View.VISIBLE);
                dialogBinding.emptyState.setVisibility(View.GONE);
            } else {
                adapter.setModels(new ArrayList<>());
                dialogBinding.modelsList.setVisibility(View.GONE);
                dialogBinding.emptyState.setVisibility(View.VISIBLE);
                dialogBinding.emptyText.setText("No models found for " + provider.getSelectorLabel()
                        + ".\nTap Refresh in AI Settings \u21bb to load models.");
            }
        }
    }

    @Override
    public void onStreamingChunk(String chunk) {
        chatAdapter.updateLastAssistantMessage(chunk);
        updateEmptyState();
        scrollToBottom();
    }

    @Override
    public void onAssistantMessage(ChatMessage assistantMessage) {
        conversationManager.saveMessage(conversationId, assistantMessage);
        updateConversationTimestamp();
        chatAdapter.replaceStreamingAssistantMessage(assistantMessage);
        updateEmptyState();
        scrollToBottom();
    }

    @Override
    public void onToolCallStarted(ToolCall toolCall) {
        chatAdapter.addToolCall(toolCall);
        scrollToBottom();
    }

    @Override
    public void onToolCallProgress(String toolCallId, String status, int progress, boolean indeterminate) {
        chatAdapter.updateToolCallProgress(toolCallId, status, progress, indeterminate);
        if (status != null && !status.isEmpty()) {
            binding.typingText.setText(status);
        }
    }

    @Override
    public void onToolCallCompleted(ToolCall toolCall, ToolResult result) {
        chatAdapter.updateToolCallResult(toolCall.getId(), result);
        if (toolCall != null) {
            String name = toolCall.getName();
            if ("create_project".equals(name)
                    || "duplicate_project".equals(name)
                    || "delete_project".equals(name)) {
                workspace = workspaceManager.getWorkspace(workspaceId);
            }
        }
        scrollToBottom();
    }

    @Override
    public void onToolMessage(ChatMessage toolMessage) {
        persistToolMessage(toolMessage);
    }

    @Override
    public void onResponseComplete(ChatMessage assistantMessage) {
        setAgentRunning(false);
        pro.sketchware.ai.engine.AiBackgroundService.notifyCompletion(this, conversationId);
    }

    @Override
    public void onCancelled() {
        setAgentRunning(false);
        binding.typingText.setText("Stopped");
        // Toast removed: "Agent stopped"
    }

    @Override
    public void onError(String error) {
        setAgentRunning(false);

        // Build user-friendly message — shown inline in the chat, no Toast
        String displayError = (error != null && !error.isEmpty()) ? error : "An unexpected error occurred.";
        String hint = null;
        if (displayError.contains("403") || displayError.contains("rate limit") || displayError.contains("Rate Limit") || displayError.contains("429")) {
            hint = "\uD83D\uDCA1 Tip: Switch to Groq \u221e (unlimited) or Cerebras (free) — tap the model chip.";
        } else if (displayError.contains("401") || displayError.contains("Invalid API Key")) {
            hint = "\uD83D\uDCA1 Check your API key in AI Settings.";
        } else if (displayError.contains("timeout") || displayError.contains("failed to connect")
                || displayError.contains("Unable to resolve host")) {
            hint = "\uD83D\uDCA1 Check your internet connection and try again.";
        } else if (displayError.contains("404") || displayError.contains("Model Not Found")) {
            hint = "\uD83D\uDCA1 The selected model may be unavailable. Try refreshing models in AI Settings.";
        } else if (displayError.contains("503") || displayError.contains("Service Unavailable")) {
            hint = "\uD83D\uDCA1 The AI provider is temporarily overloaded. Please try again in a moment.";
        }

        StringBuilder msg = new StringBuilder("\u26a0\ufe0f ").append(displayError);
        if (hint != null) msg.append("\n\n").append(hint);

        ChatMessage errorMessage = new ChatMessage(conversationId, msg.toString(), null);
        conversationManager.saveMessage(conversationId, errorMessage);
        updateConversationTimestamp();
        chatAdapter.replaceStreamingAssistantMessage(errorMessage);
        updateEmptyState();
        scrollToBottom();
        // No Toast — error is displayed inline in the chat for a cleaner UX
    }

    @Override
    public void onThinking(String status) {
        binding.typingText.setText(status);
    }

    @Override
    public void onInstallArtifact(@NonNull String artifactPath) {
        try {
            File artifact = new File(artifactPath);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", artifact);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            startActivity(intent);
        } catch (Exception e) {
            // Show error inline in chat instead of Toast
            String errMsg = "\u26a0\ufe0f Unable to open installer: "
                    + (e.getMessage() != null ? e.getMessage() : "Unknown error")
                    + "\n\n\uD83D\uDCA1 Make sure the APK file exists and you have allowed installing from unknown sources.";
            ChatMessage errChat = new ChatMessage(conversationId, errMsg, null);
            chatAdapter.addAssistantMessage(errChat);
            scrollToBottom();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (agentExecutor != null) {
            agentExecutor.shutdown();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spoken = results.get(0);
                String current = binding.inputMessage.getText() != null
                        ? binding.inputMessage.getText().toString() : "";
                binding.inputMessage.setText(current.isEmpty() ? spoken : current + " " + spoken);
                binding.inputMessage.setSelection(binding.inputMessage.getText().length());
            }
        }

        if (requestCode == REQUEST_FILE_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    int charCount = 0;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                        while ((line = reader.readLine()) != null && charCount < 8000) {
                            sb.append(line).append("\n");
                            charCount += line.length();
                        }
                    }
                    String fileName = uri.getLastPathSegment();
                    String fileContent = "```\n// File: " + fileName + "\n" + sb.toString().trim() + "\n```";
                    String current = binding.inputMessage.getText() != null
                            ? binding.inputMessage.getText().toString().trim() : "";
                    binding.inputMessage.setText(current.isEmpty() ? fileContent : current + "\n\n" + fileContent);
                    binding.inputMessage.setSelection(0); // scroll to top so user sees the file
                    // Toast removed: "File attached: " + fileName
                }
            } catch (Exception e) {
                // Toast removed: "Could not read file: " + e.getMessage()
            }
        }
    }
    
   
}
