package pro.sketchware.ai.chat.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.ai.activities.AiSettingsActivity;
import pro.sketchware.ai.adapters.ModelProviderPagerAdapter;
import pro.sketchware.ai.chat.adapter.ChatMessageAdapter;
import pro.sketchware.ai.chat.coordinator.AgentExecutorAiDelegate;
import pro.sketchware.ai.chat.coordinator.ChatCoordinator;
import pro.sketchware.ai.chat.model.ChatMessage;
import pro.sketchware.ai.engine.AiBackgroundService;
import pro.sketchware.ai.engine.TokenEstimator;
import pro.sketchware.ai.file.FileAttachManager;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.Conversation;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.storage.ConversationManager;
import pro.sketchware.ai.storage.WorkspaceManager;
import pro.sketchware.databinding.DialogModelSelectorBinding;

/**
 * ChatActivity — Stage 3 FINAL (fully wired).
 *
 * <p>Architecture:
 * <pre>
 * ChatActivity (UI only)
 *   └── ChatCoordinator (message list + streaming state)
 *         └── AgentExecutorAiDelegate (AI calls + persistence)
 *               └── AgentExecutor (real AI engine)
 * </pre>
 *
 * <p>Zero AI logic here — all delegated through ChatCoordinator → AgentExecutorAiDelegate.
 */
public class ChatActivity extends AppCompatActivity
        implements ChatCoordinator.CoordinatorListener {

    // ─── Intent extras ─────────────────────────────────────────────────────────

    public static final String EXTRA_CONVERSATION_ID = "conversation_id";
    public static final String EXTRA_WORKSPACE_ID    = "workspace_id";
    public static final String EXTRA_INITIAL_PROMPT  = "initial_prompt";
    public static final String EXTRA_PROJECT_ID      = "project_id";
    public static final String EXTRA_PAGE_CONTEXT    = "page_context";

    private static final int REQUEST_SPEECH_INPUT = 1001;
    private static final int REQUEST_FILE_PICK    = 1002;

    // ─── Core components ───────────────────────────────────────────────────────

    @NonNull  private ChatCoordinator coordinator;
    @NonNull  private ChatMessageAdapter adapter;
    @NonNull  private AgentExecutorAiDelegate aiDelegate;
    @Nullable private FileAttachManager fileAttachManager;

    // ─── Session data ──────────────────────────────────────────────────────────

    @Nullable private String conversationId;
    @Nullable private String workspaceId;
    @Nullable private Conversation conversation;
    @Nullable private ConversationManager conversationManager;
    @Nullable private AiPreferences preferences;

    // ─── Model state ───────────────────────────────────────────────────────────

    @Nullable private AiProvider currentProvider;
    @Nullable private String currentModelId;

    // ─── Views ─────────────────────────────────────────────────────────────────

    @Nullable private MaterialToolbar toolbar;
    @Nullable private RecyclerView recyclerView;
    @Nullable private View typingIndicator;
    @Nullable private View emptyStateView;
    @Nullable private View scrollToBottomFab;
    @Nullable private TextInputEditText inputEditText;
    @Nullable private View btnSend;
    @Nullable private View btnMic;
    @Nullable private View btnAttach;
    @Nullable private TextView tokenBadge;

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_ui);

        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        workspaceId    = getIntent().getStringExtra(EXTRA_WORKSPACE_ID);

        if (conversationId == null || workspaceId == null) {
            finish();
            return;
        }

        conversationManager = new ConversationManager(this);
        preferences         = AiPreferences.getInstance(this);

        conversation = conversationManager.getConversation(conversationId, workspaceId);
        if (conversation == null) {
            finish();
            return;
        }

        // File attach (must register before onStart)
        fileAttachManager = new FileAttachManager(this);
        fileAttachManager.registerLauncher(this);
        fileAttachManager.setCallback(result -> coordinator.sendUserMessage(result.getChatText()));

        // Build core components
        coordinator = new ChatCoordinator(this);
        adapter     = new ChatMessageAdapter(this);
        coordinator.setCoordinatorListener(this);

        // Build and wire AI delegate
        aiDelegate = new AgentExecutorAiDelegate(this);
        aiDelegate.setConversationId(conversationId);
        aiDelegate.setWorkspaceId(workspaceId);
        aiDelegate.setScopedProjectId(getIntent().getStringExtra(EXTRA_PROJECT_ID));
        aiDelegate.setPageContext(getIntent().getStringExtra(EXTRA_PAGE_CONTEXT));
        aiDelegate.setCoordinator(coordinator);
        aiDelegate.setPulseListener(this::showPulseConfirmation);
        coordinator.setAiDelegate(aiDelegate);

        // Load model info and wire to delegate
        loadModelInfo();
        aiDelegate.setCurrentProvider(currentProvider);
        aiDelegate.setCurrentModelId(currentModelId);

        // Bind and setup views
        bindViews();
        setupToolbar();
        setupRecyclerView();
        setupInputArea();
        setupKeyboardInsets();
        setupTokenBadge();

        // Attach coordinator (wires adapter + scroll-to-bottom FAB)
        coordinator.attach(adapter, recyclerView, typingIndicator,
                emptyStateView, scrollToBottomFab);

        // Override adapter listener so long-press shows MessageActionsBottomSheet
        adapter.setListener(buildAdapterListener());

        // Load existing conversation history
        List<ChatMessage> history = aiDelegate.loadHistory(conversationId);
        if (!history.isEmpty()) coordinator.loadMessages(history);

        // Restore draft
        if (preferences != null && inputEditText != null) {
            String draft = preferences.getDraft(conversationId);
            if (draft != null && !draft.isEmpty()) {
                inputEditText.setText(draft);
                inputEditText.setSelection(draft.length());
            }
        }

        // Back press handler
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { showExitDialog(); }
        });

        // Auto-send initial prompt if provided
        if (savedInstanceState == null) maybeSendInitialPrompt();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        coordinator.destroy();
        aiDelegate.shutdown();
        if (fileAttachManager != null) fileAttachManager.destroy();
    }

    // ─── View binding ──────────────────────────────────────────────────────────

    private void bindViews() {
        toolbar           = findViewById(R.id.chat_toolbar);
        recyclerView      = findViewById(R.id.chat_recycler_view);
        typingIndicator   = findViewById(R.id.chat_typing_indicator_container);
        emptyStateView    = findViewById(R.id.chat_empty_state);
        scrollToBottomFab = findViewById(R.id.chat_scroll_to_bottom_fab);
        inputEditText     = findViewById(R.id.chat_input_edit_text);
        btnSend           = findViewById(R.id.chat_btn_send);
        btnMic            = findViewById(R.id.chat_btn_mic);
        btnAttach         = findViewById(R.id.chat_btn_attach);
    }

    private void setupToolbar() {
        if (toolbar == null) return;
        if (conversation != null) toolbar.setTitle(conversation.getTitle());
        updateModelDisplay();
        toolbar.setNavigationOnClickListener(v -> showExitDialog());
        toolbar.setOnClickListener(v -> showModelSelector());
        toolbar.inflateMenu(R.menu.menu_chat);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_chat_settings) {
                startActivity(new Intent(this, AiSettingsActivity.class));
                return true;
            }
            if (id == R.id.action_clear_chat) {
                confirmClearConversation();
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        if (recyclerView == null) return;
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        recyclerView.setLayoutManager(lm);
        recyclerView.setAdapter(adapter);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setHasFixedSize(false);
    }

    private void setupInputArea() {
        if (btnSend != null)   btnSend.setOnClickListener(v -> submitInput());
        if (btnMic != null)    btnMic.setOnClickListener(v -> startSpeechInput());
        if (btnAttach != null) btnAttach.setOnClickListener(v -> {
            if (fileAttachManager != null) fileAttachManager.openFilePicker();
            else openFilePicker();
        });

        if (inputEditText != null) {
            inputEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    submitInput();
                    return true;
                }
                return false;
            });

            inputEditText.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override
                public void afterTextChanged(Editable s) {
                    boolean hasText = s != null && s.length() > 0;
                    if (btnSend != null) btnSend.setVisibility(hasText ? View.VISIBLE : View.GONE);
                    if (btnMic  != null) btnMic.setVisibility(hasText ? View.GONE : View.VISIBLE);
                    String text = s != null ? s.toString() : "";
                    if (preferences != null && conversationId != null) {
                        preferences.saveDraft(conversationId, text);
                    }
                    updateTokenHint(text);
                }
            });
        }
    }

    private void setupKeyboardInsets() {
        View inputArea = findViewById(R.id.chat_input_area);
        if (inputArea == null) return;
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(inputArea, (v, insets) -> {
            int bottom = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.ime()
            ).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupTokenBadge() {
        if (toolbar == null) return;
        tokenBadge = new TextView(this);
        tokenBadge.setTextSize(10f);
        tokenBadge.setTextColor(0xFF9d8ec0);
        tokenBadge.setPadding(8, 2, 8, 2);
        tokenBadge.setVisibility(View.GONE);
        toolbar.addView(tokenBadge);
    }

    // ─── Adapter listener (handles long-press) ────────────────────────────────

    @NonNull
    private ChatMessageAdapter.ChatMessageListener buildAdapterListener() {
        return new ChatMessageAdapter.ChatMessageListener() {
            @Override
            public void onCopyMessage(@NonNull ChatMessage message) {
                coordinator.onCopyMessage(message);
            }

            @Override
            public void onShareMessage(@NonNull ChatMessage message) {
                coordinator.onShareMessage(message);
            }

            @Override
            public void onLongPressMessage(@NonNull ChatMessage message) {
                MessageActionsBottomSheet sheet =
                        MessageActionsBottomSheet.show(getSupportFragmentManager(), message);
                sheet.setOnSelectAllListener(id -> coordinator.onCopyMessage(message));
            }

            @Override
            public void onToggleExpand(@NonNull ChatMessage message, boolean isExpanded) {
                coordinator.onToggleExpand(message, isExpanded);
            }
        };
    }

    // ─── Input submission ─────────────────────────────────────────────────────

    private void submitInput() {
        if (inputEditText == null) return;
        String text = inputEditText.getText() != null
                ? inputEditText.getText().toString().trim() : "";
        if (text.isEmpty()) return;

        if (currentModelId == null || currentModelId.isEmpty()) {
            showModelSelector();
            return;
        }

        if (preferences != null && conversationId != null) {
            preferences.clearDraft(conversationId);
        }

        inputEditText.setText("");

        // Update conversation title on first message
        if (conversation != null && conversationManager != null
                && "New Chat".equals(conversation.getTitle())) {
            String title = text.length() > 50 ? text.substring(0, 50) + "…" : text;
            conversation.setTitle(title);
            conversationManager.saveConversation(conversation);
            if (toolbar != null) toolbar.setTitle(title);
        }

        coordinator.sendUserMessage(text);
        startAiService();
    }

    // ─── Model selector ───────────────────────────────────────────────────────

    private void loadModelInfo() {
        if (preferences == null) return;
        if (conversation != null) {
            String providerName = conversation.getProviderName();
            if (providerName != null && !providerName.isEmpty()) {
                currentProvider = AiProvider.fromName(providerName);
            }
            String modelId = conversation.getModelId();
            if (modelId != null && !modelId.isEmpty()) {
                currentModelId = modelId;
                if (currentProvider == null) currentProvider = preferences.getSelectedProvider();
                return;
            }
        }
        if (currentProvider == null) currentProvider = preferences.getSelectedProvider();
        currentModelId = preferences.getSelectedModel(currentProvider);
    }

    private void updateModelDisplay() {
        if (toolbar == null) return;
        if (currentModelId != null && !currentModelId.isEmpty()) {
            String display = currentModelId.contains("/")
                    ? currentModelId.substring(currentModelId.lastIndexOf('/') + 1)
                    : currentModelId;
            toolbar.setSubtitle(display);
        } else {
            toolbar.setSubtitle("Select model");
        }
    }

    private void showModelSelector() {
        if (preferences == null) return;
        List<AiProvider> available = new ArrayList<>();
        for (AiProvider p : AiProvider.values()) {
            boolean def = (p == AiProvider.GOOGLE_AI_STUDIO
                    || p == AiProvider.SAMBANOVA
                    || p == AiProvider.CHUTES);
            boolean enabled = preferences.prefs().getBoolean("provider_enabled_" + p.name(), def);
            if (!enabled) continue;
            if (!p.requiresApiKey() || preferences.hasApiKey(p)) available.add(p);
        }
        if (available.isEmpty()) {
            startActivity(new Intent(this, AiSettingsActivity.class));
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        DialogModelSelectorBinding binding = DialogModelSelectorBinding.inflate(getLayoutInflater());
        dialog.setContentView(binding.getRoot());

        ModelProviderPagerAdapter pager = new ModelProviderPagerAdapter(
                available, preferences, currentModelId, model -> {
                    currentProvider  = model.getProvider();
                    currentModelId   = model.getId();
                    if (conversation != null && conversationManager != null) {
                        conversation.setModelId(currentModelId);
                        conversation.setProviderName(currentProvider.name());
                        conversationManager.saveConversation(conversation);
                    }
                    preferences.setSelectedModel(currentProvider, currentModelId);
                    preferences.setSelectedProvider(currentProvider);
                    aiDelegate.setCurrentProvider(currentProvider);
                    aiDelegate.setCurrentModelId(currentModelId);
                    updateModelDisplay();
                    dialog.dismiss();
                });

        binding.pager.setAdapter(pager);
        new TabLayoutMediator(binding.providerTabs, binding.pager,
                (tab, pos) -> tab.setText(available.get(pos).getSelectorLabel()))
                .attach();
        for (int i = 0; i < available.size(); i++) {
            if (available.get(i) == currentProvider) {
                binding.pager.setCurrentItem(i, false);
                break;
            }
        }
        dialog.show();
    }

    // ─── Token badge ──────────────────────────────────────────────────────────

    private void updateTokenHint(String input) {
        if (tokenBadge == null) return;
        int tokens = TokenEstimator.estimate(input);
        if (tokens >= 200) {
            tokenBadge.setText("⌨ ~" + tokens + " tok");
            tokenBadge.setTextColor(tokens >= 800 ? 0xFFFF8F00 : 0xFF9d8ec0);
            tokenBadge.setVisibility(View.VISIBLE);
        } else if (input.isEmpty()) {
            tokenBadge.setVisibility(View.GONE);
        }
    }

    // ─── Voice input ──────────────────────────────────────────────────────────

    private void startSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message…");
        try {
            startActivityForResult(intent, REQUEST_SPEECH_INPUT);
        } catch (ActivityNotFoundException ignored) {}
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/plain", "application/json",
                "text/x-java-source", "text/xml", "application/xml"
        });
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select file"), REQUEST_FILE_PICK);
        } catch (ActivityNotFoundException ignored) {}
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty() && inputEditText != null) {
                String spoken  = results.get(0);
                String current = inputEditText.getText() != null
                        ? inputEditText.getText().toString() : "";
                inputEditText.setText(current.isEmpty() ? spoken : current + " " + spoken);
                inputEditText.setSelection(inputEditText.getText().length());
            }
        }

        if (requestCode == REQUEST_FILE_PICK && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            attachFile(data.getData());
        }
    }

    private void attachFile(Uri uri) {
        if (inputEditText == null) return;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return;
            StringBuilder sb = new StringBuilder();
            int charCount = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null && charCount < 8000) {
                    sb.append(line).append("\n");
                    charCount += line.length();
                }
            }
            String fileName    = uri.getLastPathSegment();
            String fileContent = "```\n// File: " + fileName + "\n"
                    + sb.toString().trim() + "\n```";
            String current = inputEditText.getText() != null
                    ? inputEditText.getText().toString().trim() : "";
            inputEditText.setText(current.isEmpty() ? fileContent : current + "\n\n" + fileContent);
            inputEditText.setSelection(0);
        } catch (Exception ignored) {}
    }

    // ─── Pulse confirmation ───────────────────────────────────────────────────

    private void showPulseConfirmation(String plan, Runnable onContinue, Runnable onCancel) {
        if (isFinishing() || isDestroyed()) { onContinue.run(); return; }

        android.widget.LinearLayout view = new android.widget.LinearLayout(this);
        view.setOrientation(android.widget.LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        view.setPadding(p, p, p, p);

        android.widget.TextView planTv = new android.widget.TextView(this);
        planTv.setText(plan);
        planTv.setTextSize(14f);
        planTv.setLineSpacing(0, 1.4f);
        view.addView(planTv);

        android.widget.ProgressBar bar = new android.widget.ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(30);
        bar.setProgress(30);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
        bar.setLayoutParams(lp);
        view.addView(bar);

        android.widget.TextView timerTv = new android.widget.TextView(this);
        timerTv.setText("Continuing in 30s…");
        timerTv.setTextSize(12f);
        timerTv.setTextColor(0xFF9E9E9E);
        view.addView(timerTv);

        final boolean[] decided = {false};
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ai_plan_title)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(R.string.ai_plan_continue, (d, w) -> {
                    decided[0] = true;
                    onContinue.run();
                })
                .setNegativeButton(R.string.common_word_cancel, (d, w) -> {
                    decided[0] = true;
                    onCancel.run();
                })
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
            if (left[0] <= 0) {
                decided[0] = true;
                dialog.dismiss();
                onContinue.run();
            } else {
                h.postDelayed(tick[0], 1000);
            }
        };
        h.postDelayed(tick[0], 1000);
    }

    // ─── Dialogs ─────────────────────────────────────────────────────────────

    private void confirmClearConversation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ai_chat_clear_title)
                .setMessage(R.string.ai_chat_clear_message)
                .setPositiveButton(R.string.ai_chat_clear_button, (d, w) -> {
                    if (conversationManager != null && conversationId != null) {
                        conversationManager.deleteMessages(conversationId);
                    }
                    coordinator.clearConversation();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showExitDialog() {
        if (adapter.getItemCount() == 0) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ai_chat_exit_title)
                .setMessage(R.string.ai_chat_exit_message)
                .setPositiveButton(R.string.ai_chat_keep_exit, (d, w) -> finish())
                .setNegativeButton(R.string.ai_chat_delete_exit, (d, w) -> {
                    if (conversationManager != null && conversationId != null
                            && workspaceId != null) {
                        conversationManager.deleteConversation(conversationId, workspaceId);
                    }
                    finish();
                })
                .setNeutralButton(R.string.common_word_cancel, null)
                .show();
    }

    // ─── Background service ───────────────────────────────────────────────────

    private void startAiService() {
        Intent intent = new Intent(this, AiBackgroundService.class);
        intent.putExtra(EXTRA_CONVERSATION_ID, conversationId);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopAiService() {
        stopService(new Intent(this, AiBackgroundService.class));
    }

    // ─── Misc ─────────────────────────────────────────────────────────────────

    private void maybeSendInitialPrompt() {
        String initial = getIntent().getStringExtra(EXTRA_INITIAL_PROMPT);
        if (initial == null || initial.trim().isEmpty()) return;
        if (conversationManager != null && conversationId != null
                && !conversationManager.getMessages(conversationId).isEmpty()) return;
        if (inputEditText != null) {
            inputEditText.setText(initial.trim());
            inputEditText.post(this::submitInput);
        }
    }

    // ─── CoordinatorListener ─────────────────────────────────────────────────

    @Override
    public void onAiStarted() {
        if (btnSend != null) btnSend.setEnabled(false);
    }

    @Override
    public void onAiFinished() {
        if (btnSend != null) btnSend.setEnabled(true);
        stopAiService();
        if (conversation != null && conversationManager != null) {
            conversation.setUpdatedAt(System.currentTimeMillis());
            conversationManager.saveConversation(conversation);
        }
        AiBackgroundService.notifyCompletion(this, conversationId);
    }

    @Override
    public void onAiError(@NonNull String errorMessage) {
        if (btnSend != null) btnSend.setEnabled(true);
        stopAiService();
    }

    @Override
    public void onMessageCountChanged(int count) {}
}
