package pro.sketchware.ai.chat.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;

import pro.sketchware.R;
import pro.sketchware.ai.chat.adapter.ChatMessageAdapter;
import pro.sketchware.ai.chat.coordinator.ChatCoordinator;
import pro.sketchware.ai.chat.model.ChatMessage;
import pro.sketchware.ai.file.FileAttachManager;

/**
 * ChatBottomSheetFragment — Stage 3 FINAL.
 *
 * <p>Embedded AI Assistant panel sharing the SAME logic + UI as ChatActivity.
 * Uses {@code fragment_chat_bottom_sheet.xml} which {@code <include>}s
 * {@code layout_chat_content.xml} — IDENTICAL to ChatActivity.
 *
 * <p><b>Stage 3 additions:</b>
 * <ul>
 *   <li>{@link FileAttachManager} wired to attach button.</li>
 *   <li>Long-press → {@link MessageActionsBottomSheet}.</li>
 *   <li>Mic ↔ Send toggle.</li>
 *   <li>Full-expand behavior (EXPANDED state on launch).</li>
 * </ul>
 *
 * <p><b>Architecture rule:</b> ZERO business logic here.
 * All handling delegated to {@link ChatCoordinator}.
 */
public class ChatBottomSheetFragment extends BottomSheetDialogFragment
        implements ChatCoordinator.CoordinatorListener,
                   ChatMessageAdapter.ChatMessageListener {

    public static final String TAG = "ChatBottomSheetFragment";

    // ─── Factory ──────────────────────────────────────────────────────────────

    public static ChatBottomSheetFragment newInstance() {
        return new ChatBottomSheetFragment();
    }

    // ─── Optional shared coordinator injection ────────────────────────────────

    @Nullable private ChatCoordinator sharedCoordinator;

    public void setSharedCoordinator(@NonNull ChatCoordinator coordinator) {
        this.sharedCoordinator = coordinator;
    }

    // ─── State ────────────────────────────────────────────────────────────────

    @Nullable private ChatCoordinator coordinator;
    @Nullable private ChatMessageAdapter adapter;
    @Nullable private FileAttachManager fileAttachManager;

    @Nullable private ActivityResultLauncher<Intent> speechLauncher;

    // ─── Views ────────────────────────────────────────────────────────────────

    @Nullable private RecyclerView recyclerView;
    @Nullable private View typingIndicator;
    @Nullable private View emptyStateView;
    @Nullable private View scrollToBottomFab;
    @Nullable private TextInputEditText inputEditText;
    @Nullable private View btnSend;
    @Nullable private View btnMic;
    @Nullable private View btnAttach;

    // ─── BottomSheetDialogFragment ────────────────────────────────────────────

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        speechLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onSpeechResult);
    }

    @NonNull
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_chat_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── File attach manager ────────────────────────────────────────────
        fileAttachManager = new FileAttachManager(requireContext());
        fileAttachManager.registerLauncher(this);
        fileAttachManager.setCallback(result -> {
            if (coordinator != null) {
                coordinator.sendUserMessage(result.getChatText());
            }
        });

        // ── Coordinator setup ──────────────────────────────────────────────
        if (sharedCoordinator != null) {
            coordinator = sharedCoordinator;
        } else {
            coordinator = new ChatCoordinator(requireContext());
        }
        coordinator.setCoordinatorListener(this);

        // ── Adapter ────────────────────────────────────────────────────────
        adapter = new ChatMessageAdapter(requireContext());
        adapter.setListener(this);

        // ── Bind views ─────────────────────────────────────────────────────
        bindViews(view);
        setupRecyclerView();
        setupInputArea();

        // ── Attach coordinator ─────────────────────────────────────────────
        coordinator.attach(adapter, recyclerView, typingIndicator,
                emptyStateView, scrollToBottomFab);

        // ── Expand bottom sheet to full height ─────────────────────────────
        expandBottomSheet();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (coordinator != null) coordinator.detach();
        if (fileAttachManager != null) fileAttachManager.destroy();

        recyclerView = null;
        typingIndicator = null;
        emptyStateView = null;
        scrollToBottomFab = null;
        inputEditText = null;
        btnSend = null;
        btnMic = null;
        btnAttach = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Only destroy coordinator if it's not shared (shared one is owned by Activity)
        if (coordinator != null && coordinator != sharedCoordinator) {
            coordinator.destroy();
        }
    }

    // ─── View setup ───────────────────────────────────────────────────────────

    private void bindViews(@NonNull View root) {
        recyclerView      = root.findViewById(R.id.chat_recycler_view);
        typingIndicator   = root.findViewById(R.id.chat_typing_indicator_container);
        emptyStateView    = root.findViewById(R.id.chat_empty_state);
        scrollToBottomFab = root.findViewById(R.id.chat_scroll_to_bottom_fab);
        inputEditText     = root.findViewById(R.id.chat_input_edit_text);
        btnSend           = root.findViewById(R.id.chat_btn_send);
        btnMic            = root.findViewById(R.id.chat_btn_mic);
        btnAttach         = root.findViewById(R.id.chat_btn_attach);
    }

    private void setupRecyclerView() {
        if (recyclerView == null) return;
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        recyclerView.setLayoutManager(lm);
        recyclerView.setAdapter(adapter);
        recyclerView.setItemViewCacheSize(20);
    }

    private void setupInputArea() {
        if (btnSend != null) {
            btnSend.setOnClickListener(v -> submitInput());
        }

        if (btnMic != null) {
            btnMic.setOnClickListener(v -> startSpeechInput());
        }

        if (btnAttach != null) {
            btnAttach.setOnClickListener(v -> {
                if (fileAttachManager != null) fileAttachManager.openFilePicker();
            });
        }

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
                    if (btnMic  != null) btnMic.setVisibility(hasText  ? View.GONE    : View.VISIBLE);
                }
            });
        }
    }

    private void submitInput() {
        if (inputEditText == null || coordinator == null) return;
        String text = inputEditText.getText() != null
                ? inputEditText.getText().toString().trim() : "";
        if (text.isEmpty()) return;
        inputEditText.setText("");
        coordinator.sendUserMessage(text);
    }

    private void startSpeechInput() {
        if (speechLauncher == null) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message…");
        try {
            speechLauncher.launch(intent);
        } catch (ActivityNotFoundException ignored) {}
    }

    private void onSpeechResult(@NonNull ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        List<String> results =
                result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty() || inputEditText == null) return;
        String spoken  = results.get(0);
        String current = inputEditText.getText() != null
                ? inputEditText.getText().toString() : "";
        inputEditText.setText(current.isEmpty() ? spoken : current + " " + spoken);
        inputEditText.setSelection(inputEditText.getText() != null
                ? inputEditText.getText().length() : 0);
    }

    private void expandBottomSheet() {
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
            FrameLayout bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior =
                        BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        }
    }

    // ─── ChatMessageAdapter.ChatMessageListener ───────────────────────────────

    @Override
    public void onCopyMessage(@NonNull ChatMessage message) {
        if (coordinator != null) coordinator.onCopyMessage(message);
    }

    @Override
    public void onShareMessage(@NonNull ChatMessage message) {
        if (coordinator != null) coordinator.onShareMessage(message);
    }

    @Override
    public void onLongPressMessage(@NonNull ChatMessage message) {
        // Stage 3: open custom action sheet instead of default toolbar
        MessageActionsBottomSheet sheet =
                MessageActionsBottomSheet.show(getChildFragmentManager(), message);

        sheet.setOnSelectAllListener(messageId -> {
            if (coordinator != null) coordinator.onCopyMessage(message);
        });
    }

    @Override
    public void onToggleExpand(@NonNull ChatMessage message, boolean isExpanded) {
        // Handled by ViewHolder rotation animation
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
        // Empty state managed by coordinator → adapter
    }
}
