package pro.sketchware.ai.chat.coordinator;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import pro.sketchware.ai.chat.adapter.ChatMessageAdapter;
import pro.sketchware.ai.chat.model.ChatMessage;

/**
 * ChatCoordinator — The ONLY bridge between the Chat UI and the AI/logic layers.
 *
 * <p><b>Architecture (MANDATORY — DO NOT BYPASS):</b>
 * <pre>
 * UI ──► ChatCoordinator ──► AIOrchestrator ──► ToolManager ──► Tool.execute()
 *  ▲               ▲               │                                  │
 *  │               └───────────────┘◄─────────────── callbacks ───────┘
 *  └── OfflineModeController (direct tool path when AI unavailable)
 * </pre>
 *
 * <p><b>Stage 2 additions:</b>
 * <ul>
 *   <li>{@link #addToolResultMessage} — inserts a TOOL-type message from tool execution.</li>
 *   <li>{@link #addInternalAssistantMessage} — inserts an INTERNAL_ASSISTANT message.</li>
 *   <li>{@link #addSystemMessage} — public version for offline mode controller.</li>
 *   <li>{@link #setOfflineMessageHandler} — hooks in the OfflineModeController.</li>
 * </ul>
 *
 * <p><b>What this class owns:</b>
 * <ul>
 *   <li>The canonical in-memory message list (single source of truth).</li>
 *   <li>All RecyclerView / adapter update calls.</li>
 *   <li>Typing indicator show/hide logic.</li>
 *   <li>Auto-scroll behavior.</li>
 *   <li>Batching of rapid UI updates.</li>
 *   <li>Copy / Share action routing.</li>
 * </ul>
 *
 * <p><b>What this class does NOT own:</b>
 * <ul>
 *   <li>AI model calls — AIOrchestrator.</li>
 *   <li>Tool execution — ToolManager / Tool.</li>
 *   <li>Conversation persistence — ConversationManager (Stage 3).</li>
 * </ul>
 */
public class ChatCoordinator implements ChatMessageAdapter.ChatMessageListener {

    private static final String TAG = "ChatCoordinator";

    private static final long STREAMING_BATCH_INTERVAL_MS = 80L;

    // ─── Threading ────────────────────────────────────────────────────────────

    private final ExecutorService backgroundExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ChatCoordinator-BG");
                t.setDaemon(true);
                return t;
            });

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ─── State ────────────────────────────────────────────────────────────────

    private final AtomicBoolean isAiResponding = new AtomicBoolean(false);

    @NonNull
    private final List<ChatMessage> messages = new ArrayList<>();

    @Nullable
    private ChatMessage streamingMessage;

    @Nullable
    private Runnable pendingBatchUpdate;

    // Tokens from BG thread are enqueued here; main thread drains at batch interval.
    private final ConcurrentLinkedQueue<String> pendingTokens = new ConcurrentLinkedQueue<>();
    private volatile long lastStreamingUpdateMs = 0L;

    // ─── Attached views ───────────────────────────────────────────────────────

    @Nullable private ChatMessageAdapter adapter;
    @Nullable private RecyclerView recyclerView;
    @Nullable private android.view.View typingIndicator;
    @Nullable private android.view.View emptyStateView;
    @Nullable private android.view.View scrollToBottomFab;

    // ─── Context ──────────────────────────────────────────────────────────────

    @NonNull
    private final Context applicationContext;

    // ─── Delegates ────────────────────────────────────────────────────────────

    @Nullable private AiDelegate aiDelegate;
    @Nullable private CoordinatorListener coordinatorListener;

    /**
     * Stage 2: Optional offline message handler.
     * When set, user messages are first offered to this handler in offline mode.
     */
    @Nullable
    private OfflineMessageHandler offlineMessageHandler;

    // ─── Interfaces ───────────────────────────────────────────────────────────

    /**
     * AI response callback — implemented by AIOrchestrator (Stage 2).
     * All methods are safe to call from ANY thread.
     */
    public interface AiDelegate {
        void onUserMessageReady(
                @NonNull ChatMessage userMessage,
                @NonNull List<ChatMessage> history,
                @NonNull AiResponseCallback callback
        );
        void onCancelRequested();
    }

    /**
     * Callback passed to the AI delegate to deliver responses back.
     * All methods are thread-safe.
     */
    public interface AiResponseCallback {
        void onStreamingStarted();
        void onTokenReceived(@NonNull String token);
        void onStreamingComplete(@NonNull String fullResponse);
        void onError(@NonNull String errorMessage);
    }

    /**
     * High-level state listener for the hosting Activity/Fragment/BottomSheet.
     */
    public interface CoordinatorListener {
        void onAiStarted();
        void onAiFinished();
        void onAiError(@NonNull String errorMessage);
        void onMessageCountChanged(int count);
    }

    /**
     * Stage 2: Hook for OfflineModeController.
     * Called when a user message arrives and offline mode may handle it.
     */
    public interface OfflineMessageHandler {
        /**
         * @param message the user's message
         * @return true if handled offline (AI should NOT be called), false otherwise
         */
        boolean handleOfflineMessage(@NonNull ChatMessage message);
    }

    // ─── Constructor ──────────────────────────────────────────────────────────

    public ChatCoordinator(@NonNull Context context) {
        this.applicationContext = context.getApplicationContext();
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @MainThread
    public void attach(
            @NonNull ChatMessageAdapter adapter,
            @NonNull RecyclerView recyclerView,
            @Nullable android.view.View typingIndicator,
            @Nullable android.view.View emptyStateView,
            @Nullable android.view.View scrollToBottomFab
    ) {
        this.adapter         = adapter;
        this.recyclerView    = recyclerView;
        this.typingIndicator = typingIndicator;
        this.emptyStateView  = emptyStateView;
        this.scrollToBottomFab = scrollToBottomFab;

        adapter.setListener(this);

        if (!messages.isEmpty()) {
            adapter.submitList(new ArrayList<>(messages));
            updateEmptyState();
        }

        if (scrollToBottomFab != null) {
            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                    scrollToBottomFab.setVisibility(
                            isAtBottom(rv) ? android.view.View.GONE : android.view.View.VISIBLE);
                }
            });
            scrollToBottomFab.setOnClickListener(v -> scrollToBottom(true));
        }
    }

    @MainThread
    public void detach() {
        if (adapter != null) adapter.setListener(null);
        adapter = null;
        recyclerView = null;
        typingIndicator = null;
        emptyStateView = null;
        scrollToBottomFab = null;

        if (pendingBatchUpdate != null) {
            mainHandler.removeCallbacks(pendingBatchUpdate);
            pendingBatchUpdate = null;
        }
    }

    public void destroy() {
        detach();
        backgroundExecutor.shutdownNow();
        if (aiDelegate != null) aiDelegate.onCancelRequested();
    }

    // ─── Configuration ────────────────────────────────────────────────────────

    public void setAiDelegate(@Nullable AiDelegate aiDelegate) {
        this.aiDelegate = aiDelegate;
    }

    public void setCoordinatorListener(@Nullable CoordinatorListener listener) {
        this.coordinatorListener = listener;
    }

    /** Stage 2: Register the offline mode handler. */
    public void setOfflineMessageHandler(@Nullable OfflineMessageHandler handler) {
        this.offlineMessageHandler = handler;
    }

    // ─── User actions ─────────────────────────────────────────────────────────

    /**
     * Primary entry point: user submits a message.
     *
     * <p>Stage 2 flow:
     * <ol>
     *   <li>Validate input.</li>
     *   <li>Add USER message to the list → adapter.</li>
     *   <li>Check offline handler (OfflineModeController) — if handled, stop here.</li>
     *   <li>Show typing indicator.</li>
     *   <li>Delegate to AIOrchestrator (via AiDelegate).</li>
     * </ol>
     */
    public void sendUserMessage(@NonNull String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return;
        if (isAiResponding.get()) {
            Log.w(TAG, "sendUserMessage: AI is still responding, ignoring.");
            return;
        }

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> sendUserMessage(trimmed));
            return;
        }

        // 1. Create and add user message
        ChatMessage userMessage = ChatMessage.user(trimmed);
        addMessageInternal(userMessage);

        // 2. Stage 2: Check offline handler FIRST
        if (offlineMessageHandler != null
                && offlineMessageHandler.handleOfflineMessage(userMessage)) {
            Log.d(TAG, "Message handled offline — skipping AI.");
            return;
        }

        // 3. Show typing indicator
        showTypingIndicator(true);

        // 4. Mark as responding
        isAiResponding.set(true);

        // 5. Notify listener
        if (coordinatorListener != null) coordinatorListener.onAiStarted();

        // 6. Snapshot history for AI delegate
        List<ChatMessage> historyCopy =
                Collections.unmodifiableList(new ArrayList<>(messages));

        // 7. Delegate to AI (runs on background thread)
        backgroundExecutor.submit(() -> {
            if (aiDelegate != null) {
                aiDelegate.onUserMessageReady(userMessage, historyCopy,
                        buildAiResponseCallback());
            } else {
                buildAiResponseCallback().onError(
                        "AI is not configured. Please add an API key in AI Settings.");
            }
        });
    }

    public void cancelCurrentResponse() {
        if (!isAiResponding.get()) return;
        if (aiDelegate != null) aiDelegate.onCancelRequested();
        mainHandler.post(() -> {
            pendingTokens.clear();
            isAiResponding.set(false);
            showTypingIndicator(false);

            if (streamingMessage != null) {
                streamingMessage.setStreaming(false);
                streamingMessage.setStatus(ChatMessage.MessageStatus.SENT);
                if (adapter != null) adapter.updateMessage(streamingMessage);
                streamingMessage = null;
            }

            if (coordinatorListener != null) coordinatorListener.onAiFinished();
        });
    }

    @MainThread
    public void clearConversation() {
        messages.clear();
        streamingMessage = null;
        isAiResponding.set(false);

        if (adapter != null) adapter.clearMessages();
        updateEmptyState();

        addMessageInternal(ChatMessage.system("Conversation cleared."));
    }

    @MainThread
    public void loadMessages(@NonNull List<ChatMessage> existingMessages) {
        messages.clear();
        messages.addAll(existingMessages);

        if (adapter != null) adapter.submitList(new ArrayList<>(messages));
        updateEmptyState();
        scrollToBottom(false);
    }

    // ─── Stage 2: Public message injection methods ────────────────────────────

    /**
     * Adds a TOOL-type message with the tool result.
     * Called by OfflineModeController after a tool executes successfully.
     * Safe to call from any thread.
     *
     * @param toolName the name of the tool that produced the result
     * @param content  the tool's output content
     */
    public void addToolResultMessage(@NonNull String toolName, @NonNull String content) {
        // Generate a simple toolCallId for linking
        String toolCallId = toolName + "_" + System.currentTimeMillis();
        ChatMessage toolMsg = ChatMessage.tool(toolName, toolCallId, content);
        runOnMain(() -> addMessageInternal(toolMsg));
    }

    /**
     * Adds an INTERNAL_ASSISTANT message.
     * Called by OfflineModeController for status updates and hints.
     * Safe to call from any thread.
     *
     * @param content the internal assistant message content (supports Markdown)
     */
    public void addInternalAssistantMessage(@NonNull String content) {
        ChatMessage msg = ChatMessage.internalAssistant(content);
        runOnMain(() -> addMessageInternal(msg));
    }

    /**
     * Adds a SYSTEM message.
     * Used for notifications (offline mode toggled, conversation cleared, etc.)
     * Safe to call from any thread.
     *
     * @param content the system notification text
     */
    public void addSystemMessage(@NonNull String content) {
        ChatMessage msg = ChatMessage.system(content);
        runOnMain(() -> addMessageInternal(msg));
    }

    // ─── ChatMessageAdapter.ChatMessageListener ───────────────────────────────

    @Override
    public void onCopyMessage(@NonNull ChatMessage message) {
        String text = message.getText();
        if (text == null || text.isEmpty()) return;

        ClipboardManager clipboard =
                (ClipboardManager) applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("ChatMessage", text));
        }
        mainHandler.post(() -> android.widget.Toast.makeText(
                applicationContext, "Copied", android.widget.Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onShareMessage(@NonNull ChatMessage message) {
        String text = message.getText();
        if (text == null || text.isEmpty()) return;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent chooser = Intent.createChooser(shareIntent, "Share message");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        applicationContext.startActivity(chooser);
    }

    @Override
    public void onLongPressMessage(@NonNull ChatMessage message) {
        // Stage 3: custom BottomSheetDialog with Copy/Share/Select All/Cancel.
        onCopyMessage(message);
    }

    @Override
    public void onToggleExpand(@NonNull ChatMessage message, boolean isExpanded) {
        Log.d(TAG, "Message expanded=" + isExpanded + " id=" + message.getId());
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    @MainThread
    private void addMessageInternal(@NonNull ChatMessage message) {
        messages.add(message);
        if (adapter != null) adapter.addMessage(message);
        updateEmptyState();
        scrollToBottom(true);
    }

    @MainThread
    private void showTypingIndicator(boolean show) {
        if (typingIndicator == null) return;
        typingIndicator.setVisibility(
                show ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    @MainThread
    private void updateEmptyState() {
        if (emptyStateView == null) return;
        emptyStateView.setVisibility(
                messages.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);

        if (coordinatorListener != null) {
            coordinatorListener.onMessageCountChanged(messages.size());
        }
    }

    @MainThread
    private void scrollToBottom(boolean smooth) {
        if (recyclerView == null || messages.isEmpty()) return;
        int last = messages.size() - 1;
        if (smooth) recyclerView.smoothScrollToPosition(last);
        else        recyclerView.scrollToPosition(last);
    }

    private boolean isAtBottom(@NonNull RecyclerView rv) {
        LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
        if (lm == null) return true;
        return lm.findLastCompletelyVisibleItemPosition() >= lm.getItemCount() - 1;
    }

    private void runOnMain(@NonNull Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else mainHandler.post(r);
    }

    // ─── AI response callback factory ────────────────────────────────────────

    @NonNull
    private AiResponseCallback buildAiResponseCallback() {
        return new AiResponseCallback() {

            @Override
            public void onStreamingStarted() {
                mainHandler.post(() -> {
                    showTypingIndicator(false);
                    streamingMessage = ChatMessage.aiPlaceholder();
                    streamingMessage.setStreaming(true);
                    addMessageInternal(streamingMessage);
                });
            }

            @Override
            public void onTokenReceived(@NonNull String token) {
                // BG-thread safe: only enqueue; main thread drains via scheduleBatchUpdate.
                pendingTokens.offer(token);
                long now = System.currentTimeMillis();
                if (now - lastStreamingUpdateMs >= STREAMING_BATCH_INTERVAL_MS) {
                    lastStreamingUpdateMs = now;
                    scheduleBatchUpdate();
                }
            }

            @Override
            public void onStreamingComplete(@NonNull String fullResponse) {
                mainHandler.post(() -> {
                    cancelPendingBatchUpdate();

                    // Drain any tokens that arrived after the last batch flush.
                    String t;
                    while ((t = pendingTokens.poll()) != null) {
                        if (streamingMessage != null) streamingMessage.appendText(t);
                    }

                    if (streamingMessage != null) {
                        streamingMessage.setText(fullResponse);
                        streamingMessage.setStreaming(false);
                        streamingMessage.setStatus(ChatMessage.MessageStatus.SENT);
                        if (adapter != null) adapter.updateMessage(streamingMessage);
                        streamingMessage = null;
                    }

                    isAiResponding.set(false);
                    showTypingIndicator(false);
                    scrollToBottom(true);

                    if (coordinatorListener != null) coordinatorListener.onAiFinished();
                });
            }

            @Override
            public void onError(@NonNull String errorMessage) {
                mainHandler.post(() -> {
                    cancelPendingBatchUpdate();
                    pendingTokens.clear();

                    if (streamingMessage != null) {
                        messages.remove(streamingMessage);
                        if (adapter != null) adapter.removeMessage(streamingMessage.getId());
                        streamingMessage = null;
                    }

                    addMessageInternal(ChatMessage.error(null, errorMessage));
                    isAiResponding.set(false);
                    showTypingIndicator(false);

                    if (coordinatorListener != null) coordinatorListener.onAiError(errorMessage);
                });
            }
        };
    }

    private void scheduleBatchUpdate() {
        if (pendingBatchUpdate != null) mainHandler.removeCallbacks(pendingBatchUpdate);
        pendingBatchUpdate = () -> {
            pendingBatchUpdate = null;
            // Drain token queue onto streamingMessage — both happen on main thread.
            String t;
            while ((t = pendingTokens.poll()) != null) {
                if (streamingMessage != null) streamingMessage.appendText(t);
            }
            if (streamingMessage != null && adapter != null) {
                adapter.updateMessage(streamingMessage);
                if (recyclerView != null && isAtBottom(recyclerView)) {
                    scrollToBottom(false);
                }
            }
        };
        mainHandler.post(pendingBatchUpdate);
    }

    private void cancelPendingBatchUpdate() {
        if (pendingBatchUpdate != null) {
            mainHandler.removeCallbacks(pendingBatchUpdate);
            pendingBatchUpdate = null;
        }
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    @NonNull
    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public boolean isAiResponding() { return isAiResponding.get(); }

    public int getMessageCount() { return messages.size(); }
}
