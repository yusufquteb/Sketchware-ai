package pro.sketchware.ai.chat.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import pro.sketchware.R;
import pro.sketchware.ai.AiPlatformInitializer;
import pro.sketchware.ai.chat.adapter.ChatMessageAdapter;
import pro.sketchware.ai.chat.coordinator.ChatCoordinator;
import pro.sketchware.ai.chat.model.ChatMessage;
import pro.sketchware.ai.file.FileAttachManager;

/**
 * ChatActivity — Stage 3 FINAL.
 *
 * <p>Full-screen chat host that uses {@code layout/activity_chat_ui.xml}
 * which {@code <include>}s {@code layout/layout_chat_content.xml}.
 *
 * <p><b>Stage 3 integrations:</b>
 * <ul>
 *   <li>{@link FileAttachManager} — file picker on attach button.</li>
 *   <li>{@link MessageActionsBottomSheet} — long-press action sheet.</li>
 *   <li>{@link AiPlatformInitializer} — wires Stage 2 AI + tools.</li>
 *   <li>Mic ↔ Send button toggle based on input text.</li>
 * </ul>
 *
 * <p><b>Architecture rules:</b>
 * <ul>
 *   <li>ZERO AI/tool logic here — all delegated to ChatCoordinator.</li>
 *   <li>ZERO duplicated UI logic — all via the shared ChatCoordinator.</li>
 * </ul>
 */
public class ChatActivity extends AppCompatActivity
        implements ChatCoordinator.CoordinatorListener,
                   ChatMessageAdapter.ChatMessageListener {

    // ─── Core components ──────────────────────────────────────────────────────

    @NonNull  private ChatCoordinator coordinator;
    @NonNull  private ChatMessageAdapter adapter;
    @Nullable private FileAttachManager fileAttachManager;
    @Nullable private AiPlatformInitializer.Result aiPlatform;

    // ─── View references (from layout_chat_content.xml via <include>) ─────────

    @Nullable private RecyclerView recyclerView;
    @Nullable private View typingIndicator;
    @Nullable private View emptyStateView;
    @Nullable private View scrollToBottomFab;
    @Nullable private TextInputEditText inputEditText;
    @Nullable private View btnSend;
    @Nullable private View btnMic;
    @Nullable private View btnAttach;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_ui);

        // ── 1. File attach manager (MUST register before onStart) ──────────
        fileAttachManager = new FileAttachManager(this);
        fileAttachManager.registerLauncher(this);
        fileAttachManager.setCallback(result -> {
            // Route attached file content through coordinator as a user message
            coordinator.sendUserMessage(result.getChatText());
        });

        // ── 2. Core coordinator + adapter ──────────────────────────────────
        coordinator = new ChatCoordinator(this);
        adapter = new ChatMessageAdapter(this);
        adapter.setListener(this);
        coordinator.setCoordinatorListener(this);

        // ── 3. Bind views ──────────────────────────────────────────────────
        bindViews();
        setupRecyclerView();
        setupInputArea();

        // ── 4. Attach coordinator to views ─────────────────────────────────
        coordinator.attach(adapter, recyclerView, typingIndicator,
                emptyStateView, scrollToBottomFab);

        // ── 5. Initialize Stage 2 AI platform ─────────────────────────────
        // Pass null as model provider → uses offline-capable no-op provider.
        // Replace null with your actual AiModelProvider implementation.
        aiPlatform = AiPlatformInitializer.initialize(
                this, coordinator, /* modelProvider = */ null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        coordinator.destroy();
        if (fileAttachManager != null) fileAttachManager.destroy();
    }

    // ─── View binding ─────────────────────────────────────────────────────────

    private void bindViews() {
        recyclerView    = findViewById(R.id.chat_recycler_view);
        typingIndicator = findViewById(R.id.chat_typing_indicator_container);
        emptyStateView  = findViewById(R.id.chat_empty_state);
        scrollToBottomFab = findViewById(R.id.chat_scroll_to_bottom_fab);
        inputEditText   = findViewById(R.id.chat_input_edit_text);
        btnSend         = findViewById(R.id.chat_btn_send);
        btnMic          = findViewById(R.id.chat_btn_mic);
        btnAttach       = findViewById(R.id.chat_btn_attach);
    }

    private void setupRecyclerView() {
        if (recyclerView == null) return;

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // Performance: pre-fetch items, fixed size
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setHasFixedSize(false);
    }

    private void setupInputArea() {
        // ── Send button ────────────────────────────────────────────────────
        if (btnSend != null) {
            btnSend.setOnClickListener(v -> submitInput());
        }

        // ── Mic button ─────────────────────────────────────────────────────
        if (btnMic != null) {
            btnMic.setOnClickListener(v -> {
                // Stage 3 placeholder — voice input handled in Stage 4
                coordinator.addSystemMessage("🎙️ Voice input coming soon.");
            });
        }

        // ── Attach button ──────────────────────────────────────────────────
        if (btnAttach != null) {
            btnAttach.setOnClickListener(v -> {
                if (fileAttachManager != null) fileAttachManager.openFilePicker();
            });
        }

        // ── IME action "Send" ──────────────────────────────────────────────
        if (inputEditText != null) {
            inputEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    submitInput();
                    return true;
                }
                return false;
            });

            // ── Mic ↔ Send toggle ──────────────────────────────────────────
            inputEditText.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    boolean hasText = s != null && s.length() > 0;
                    if (btnSend != null) btnSend.setVisibility(hasText ? View.VISIBLE : View.GONE);
                    if (btnMic  != null) btnMic.setVisibility(hasText  ? View.GONE    : View.VISIBLE);
                }
            });
        }
    }

    /** Reads input, clears field, sends via coordinator. */
    private void submitInput() {
        if (inputEditText == null) return;
        String text = inputEditText.getText() != null
                ? inputEditText.getText().toString().trim() : "";
        if (text.isEmpty()) return;

        inputEditText.setText("");
        coordinator.sendUserMessage(text);
    }

    // ─── ChatMessageAdapter.ChatMessageListener ───────────────────────────────

    @Override
    public void onCopyMessage(@NonNull ChatMessage message) {
        coordinator.onCopyMessage(message);
    }

    @Override
    public void onShareMessage(@NonNull ChatMessage message) {
        coordinator.onShareMessage(message);
    }

    /**
     * Stage 3: Long-press → open MessageActionsBottomSheet.
     * Replaces the Android default text selection toolbar.
     */
    @Override
    public void onLongPressMessage(@NonNull ChatMessage message) {
        MessageActionsBottomSheet sheet =
                MessageActionsBottomSheet.show(getSupportFragmentManager(), message);

        // Wire "Select All" to coordinator's copy (simplified for Stage 3)
        sheet.setOnSelectAllListener(messageId -> coordinator.onCopyMessage(message));
    }

    @Override
    public void onToggleExpand(@NonNull ChatMessage message, boolean isExpanded) {
        // Handled visually inside ViewHolder — no coordinator action needed
    }

    // ─── ChatCoordinator.CoordinatorListener ──────────────────────────────────

    @Override
    public void onAiStarted() {
        if (btnSend != null) btnSend.setEnabled(false);
    }

    @Override
    public void onAiFinished() {
        if (btnSend != null) btnSend.setEnabled(true);
    }

    @Override
    public void onAiError(@NonNull String errorMessage) {
        if (btnSend != null) btnSend.setEnabled(true);
    }

    @Override
    public void onMessageCountChanged(int count) {
        // Empty state is handled by coordinator → adapter
    }
}
