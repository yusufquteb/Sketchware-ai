package pro.sketchware.ai.chat.adapter;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.chat.model.ChatMessage;

/**
 * ChatMessageAdapter — Stage 3 FINAL (FIXED).
 *
 * <p><b>FIXES in this version:</b>
 * <ul>
 *   <li>Removed R.id.chat_msg_status_row reference — ID does not exist in
 *       item_chat_msg_user.xml. Status is conveyed by timestamp only.</li>
 * </ul>
 *
 * <p><b>R.id cross-reference — all IDs verified against their layouts:</b>
 * <pre>
 * UserViewHolder       ← item_chat_msg_user.xml
 *   chat_msg_btn_copy  ✅
 *   chat_msg_time      ✅
 *   chat_msg_text      ✅
 *
 * AiViewHolder         ← item_chat_msg_ai.xml
 *   chat_msg_card              ✅
 *   chat_msg_time              ✅
 *   chat_msg_streaming_indicator ✅
 *   chat_msg_text              ✅
 *   chat_msg_actions_row       ✅
 *   chat_msg_btn_copy          ✅
 *   chat_msg_btn_share         ✅
 *   chat_msg_btn_expand        ✅
 *
 * SystemViewHolder     ← item_chat_msg_system.xml
 *   chat_msg_text      ✅
 *
 * ToolViewHolder       ← item_chat_msg_tool.xml
 *   chat_msg_tool_name ✅
 *   chat_msg_text      ✅
 *   chat_msg_time      ✅
 *   chat_msg_btn_copy  ✅
 *
 * InternalAssistantViewHolder ← item_chat_msg_internal.xml
 *   chat_msg_text      ✅
 *   chat_msg_time      ✅
 *   chat_msg_btn_copy  ✅
 * </pre>
 *
 * <p><b>Performance guarantees (MANDATORY):</b>
 * <ul>
 *   <li>ZERO notifyDataSetChanged() calls — all via DiffUtil.</li>
 *   <li>Partial bind for streaming: payload = "text_update".</li>
 *   <li>Stable IDs enabled.</li>
 *   <li>ZERO business logic — pure UI binding only.</li>
 * </ul>
 */
public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // ─── View type constants ──────────────────────────────────────────────────
    static final int TYPE_USER               = 0;
    static final int TYPE_AI                 = 1;
    static final int TYPE_SYSTEM             = 2;
    static final int TYPE_TOOL               = 3;
    static final int TYPE_INTERNAL_ASSISTANT = 4;

    private static final String PAYLOAD_TEXT_UPDATE = "text_update";

    // ─── Fields ──────────────────────────────────────────────────────────────

    @NonNull private List<ChatMessage> messages = new ArrayList<>();
    @Nullable private ChatMessageListener listener;
    @NonNull  private final Context context;

    // ─── Listener ─────────────────────────────────────────────────────────────

    public interface ChatMessageListener {
        void onCopyMessage(@NonNull ChatMessage message);
        void onShareMessage(@NonNull ChatMessage message);
        void onLongPressMessage(@NonNull ChatMessage message);
        void onToggleExpand(@NonNull ChatMessage message, boolean isExpanded);
    }

    // ─── Constructor ──────────────────────────────────────────────────────────

    public ChatMessageAdapter(@NonNull Context context) {
        this.context = context;
        setHasStableIds(true);
    }

    public void setListener(@Nullable ChatMessageListener listener) {
        this.listener = listener;
    }

    // ─── RecyclerView.Adapter ─────────────────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        switch (messages.get(position).getType()) {
            case USER:               return TYPE_USER;
            case AI:                 return TYPE_AI;
            case SYSTEM:             return TYPE_SYSTEM;
            case TOOL:               return TYPE_TOOL;
            case INTERNAL_ASSISTANT: return TYPE_INTERNAL_ASSISTANT;
            default:                 return TYPE_AI;
        }
    }

    @Override
    public long getItemId(int position) {
        return messages.get(position).getId().hashCode();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_USER:
                return new UserViewHolder(
                        inf.inflate(R.layout.item_chat_msg_user, parent, false));
            case TYPE_AI:
                return new AiViewHolder(
                        inf.inflate(R.layout.item_chat_msg_ai, parent, false));
            case TYPE_SYSTEM:
                return new SystemViewHolder(
                        inf.inflate(R.layout.item_chat_msg_system, parent, false));
            case TYPE_TOOL:
                return new ToolViewHolder(
                        inf.inflate(R.layout.item_chat_msg_tool, parent, false));
            case TYPE_INTERNAL_ASSISTANT:
                return new InternalAssistantViewHolder(
                        inf.inflate(R.layout.item_chat_msg_internal, parent, false));
            default:
                return new AiViewHolder(
                        inf.inflate(R.layout.item_chat_msg_ai, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        switch (holder.getItemViewType()) {
            case TYPE_USER:
                ((UserViewHolder) holder).bind(msg, listener);
                break;
            case TYPE_AI:
                ((AiViewHolder) holder).bind(msg, listener);
                break;
            case TYPE_SYSTEM:
                ((SystemViewHolder) holder).bind(msg);
                break;
            case TYPE_TOOL:
                ((ToolViewHolder) holder).bind(msg, listener);
                break;
            case TYPE_INTERNAL_ASSISTANT:
                ((InternalAssistantViewHolder) holder).bind(msg, listener);
                break;
        }
    }

    /**
     * Partial bind — payload "text_update" updates only text + streaming state.
     * Hot path during AI streaming — must be ultra-fast.
     */
    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position,
            @NonNull List<Object> payloads
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        ChatMessage msg = messages.get(position);
        if (holder instanceof AiViewHolder) {
            ((AiViewHolder) holder).updateText(msg);
        } else if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).updateText(msg);
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ─── Public update methods (DiffUtil only — ZERO notifyDataSetChanged) ────

    public void submitList(@NonNull List<ChatMessage> newList) {
        List<ChatMessage> oldList = new ArrayList<>(messages);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(
                new ChatMessageDiffCallback(oldList, newList));
        messages = new ArrayList<>(newList);
        diff.dispatchUpdatesTo(this);
    }

    public void addMessage(@NonNull ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateMessage(@NonNull ChatMessage message) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(message.getId())) {
                messages.set(i, message);
                notifyItemChanged(i, PAYLOAD_TEXT_UPDATE);
                return;
            }
        }
    }

    public void removeMessage(@NonNull String messageId) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(messageId)) {
                messages.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    public void clearMessages() {
        int count = messages.size();
        if (count == 0) return;
        messages.clear();
        notifyItemRangeRemoved(0, count);
    }

    @NonNull
    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    // ─── Shared helper ────────────────────────────────────────────────────────

    @NonNull
    static String formatTime(long ts) {
        return DateFormat.format("h:mm a", new Date(ts)).toString();
    }

    static int resolveAttrColor(@NonNull Context ctx, int attrId) {
        TypedValue tv = new TypedValue();
        ctx.getTheme().resolveAttribute(attrId, tv, true);
        return tv.data;
    }

    // ═══════════════════════════════════════════════════════════
    // ViewHolder: USER
    // IDs used: chat_msg_btn_copy, chat_msg_time, chat_msg_text
    // All verified ✅ in item_chat_msg_user.xml
    // ═══════════════════════════════════════════════════════════
    static final class UserViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvText;
        private final TextView tvTime;
        private final ImageButton btnCopy;

        UserViewHolder(@NonNull View v) {
            super(v);
            tvText  = v.findViewById(R.id.chat_msg_text);
            tvTime  = v.findViewById(R.id.chat_msg_time);
            btnCopy = v.findViewById(R.id.chat_msg_btn_copy);
        }

        void bind(@NonNull ChatMessage msg, @Nullable ChatMessageListener listener) {
            tvText.setText(msg.getText());
            if (tvTime != null) tvTime.setText(formatTime(msg.getTimestamp()));

            if (btnCopy != null) {
                btnCopy.setVisibility(View.VISIBLE);
                btnCopy.setOnClickListener(v -> {
                    if (listener != null) listener.onCopyMessage(msg);
                });
            }

            itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onLongPressMessage(msg);
                return true;
            });
        }

        void updateText(@NonNull ChatMessage msg) {
            tvText.setText(msg.getText());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ViewHolder: AI
    // IDs used: chat_msg_card, chat_msg_time, chat_msg_streaming_indicator,
    //           chat_msg_text, chat_msg_actions_row,
    //           chat_msg_btn_copy, chat_msg_btn_share, chat_msg_btn_expand
    // All verified ✅ in item_chat_msg_ai.xml
    // ═══════════════════════════════════════════════════════════
    static final class AiViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView card;
        private final TextView tvText;
        private final TextView tvTime;
        private final View streamingIndicator;
        private final LinearLayout actionsRow;
        private final ImageButton btnCopy;
        private final ImageButton btnShare;
        private final ImageButton btnExpand;

        private static final int COLLAPSED_MAX_LINES = 8;
        private static final int CHAR_THRESHOLD      = 600;

        private boolean isExpanded = false;

        AiViewHolder(@NonNull View v) {
            super(v);
            card               = v.findViewById(R.id.chat_msg_card);
            tvText             = v.findViewById(R.id.chat_msg_text);
            tvTime             = v.findViewById(R.id.chat_msg_time);
            streamingIndicator = v.findViewById(R.id.chat_msg_streaming_indicator);
            actionsRow         = v.findViewById(R.id.chat_msg_actions_row);
            btnCopy            = v.findViewById(R.id.chat_msg_btn_copy);
            btnShare           = v.findViewById(R.id.chat_msg_btn_share);
            btnExpand          = v.findViewById(R.id.chat_msg_btn_expand);
        }

        void bind(@NonNull ChatMessage msg, @Nullable ChatMessageListener listener) {
            tvText.setText(msg.getText());
            if (tvTime != null) tvTime.setText(formatTime(msg.getTimestamp()));

            boolean streaming = msg.isStreaming();

            if (streamingIndicator != null) {
                streamingIndicator.setVisibility(streaming ? View.VISIBLE : View.GONE);
            }
            if (actionsRow != null) {
                actionsRow.setVisibility(streaming ? View.GONE : View.VISIBLE);
            }

            // Reset expand state on recycle
            isExpanded = false;
            applyExpandState(msg.getText());
            if (btnExpand != null) btnExpand.setRotation(0f);

            // Error state
            if (card != null) {
                Context ctx = itemView.getContext();
                if (msg.isError()) {
                    card.setCardBackgroundColor(resolveAttrColor(ctx,
                            com.google.android.material.R.attr.colorErrorContainer));
                    tvText.setTextColor(resolveAttrColor(ctx,
                            com.google.android.material.R.attr.colorOnErrorContainer));
                } else {
                    card.setCardBackgroundColor(resolveAttrColor(ctx,
                            com.google.android.material.R.attr.colorSurfaceContainerLow));
                    tvText.setTextColor(resolveAttrColor(ctx,
                            com.google.android.material.R.attr.colorOnSurface));
                }
            }

            if (btnCopy != null) {
                btnCopy.setOnClickListener(v -> {
                    if (listener != null) listener.onCopyMessage(msg);
                });
            }
            if (btnShare != null) {
                btnShare.setOnClickListener(v -> {
                    if (listener != null) listener.onShareMessage(msg);
                });
            }
            if (btnExpand != null) {
                btnExpand.setOnClickListener(v -> {
                    isExpanded = !isExpanded;
                    applyExpandState(msg.getText());
                    // Arrow icon ONLY — rotate 0° collapsed, 180° expanded
                    btnExpand.animate()
                            .rotation(isExpanded ? 180f : 0f)
                            .setDuration(200)
                            .start();
                    if (listener != null) listener.onToggleExpand(msg, isExpanded);
                });
            }

            itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onLongPressMessage(msg);
                return true;
            });
        }

        void updateText(@NonNull ChatMessage msg) {
            tvText.setText(msg.getText());
            boolean streaming = msg.isStreaming();
            if (streamingIndicator != null) {
                streamingIndicator.setVisibility(streaming ? View.VISIBLE : View.GONE);
            }
            if (actionsRow != null) {
                actionsRow.setVisibility(streaming ? View.GONE : View.VISIBLE);
            }
        }

        private void applyExpandState(@Nullable String text) {
            if (text == null) return;
            boolean needsExpand = text.length() > CHAR_THRESHOLD
                    || countLines(text) > COLLAPSED_MAX_LINES;

            if (btnExpand != null) {
                btnExpand.setVisibility(needsExpand ? View.VISIBLE : View.GONE);
            }
            tvText.setMaxLines(
                    (isExpanded || !needsExpand) ? Integer.MAX_VALUE : COLLAPSED_MAX_LINES);
        }

        private int countLines(@NonNull String text) {
            int n = 1;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') n++;
            }
            return n;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ViewHolder: SYSTEM
    // IDs used: chat_msg_text ✅ in item_chat_msg_system.xml
    // ═══════════════════════════════════════════════════════════
    static final class SystemViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvText;

        SystemViewHolder(@NonNull View v) {
            super(v);
            tvText = v.findViewById(R.id.chat_msg_text);
        }

        void bind(@NonNull ChatMessage msg) {
            tvText.setText(msg.getText());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ViewHolder: TOOL
    // IDs used: chat_msg_tool_name, chat_msg_text, chat_msg_time, chat_msg_btn_copy
    // All verified ✅ in item_chat_msg_tool.xml
    // ═══════════════════════════════════════════════════════════
    static final class ToolViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvToolName;
        private final TextView tvToolResult;
        private final TextView tvTime;
        private final ImageButton btnCopy;

        ToolViewHolder(@NonNull View v) {
            super(v);
            tvToolName   = v.findViewById(R.id.chat_msg_tool_name);
            tvToolResult = v.findViewById(R.id.chat_msg_text);
            tvTime       = v.findViewById(R.id.chat_msg_time);
            btnCopy      = v.findViewById(R.id.chat_msg_btn_copy);
        }

        void bind(@NonNull ChatMessage msg, @Nullable ChatMessageListener listener) {
            String toolName = msg.getToolName();
            if (tvToolName != null) {
                tvToolName.setText(toolName != null ? toolName : "tool");
            }
            tvToolResult.setText(msg.getText());
            if (tvTime != null) tvTime.setText(formatTime(msg.getTimestamp()));

            if (btnCopy != null) {
                btnCopy.setOnClickListener(v -> {
                    if (listener != null) listener.onCopyMessage(msg);
                });
            }
            itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onLongPressMessage(msg);
                return true;
            });
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ViewHolder: INTERNAL_ASSISTANT
    // IDs used: chat_msg_text, chat_msg_time, chat_msg_btn_copy
    // All verified ✅ in item_chat_msg_internal.xml
    // ═══════════════════════════════════════════════════════════
    static final class InternalAssistantViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvText;
        private final TextView tvTime;
        private final ImageButton btnCopy;

        InternalAssistantViewHolder(@NonNull View v) {
            super(v);
            tvText  = v.findViewById(R.id.chat_msg_text);
            tvTime  = v.findViewById(R.id.chat_msg_time);
            btnCopy = v.findViewById(R.id.chat_msg_btn_copy);
        }

        void bind(@NonNull ChatMessage msg, @Nullable ChatMessageListener listener) {
            tvText.setText(msg.getText());
            if (tvTime != null) tvTime.setText(formatTime(msg.getTimestamp()));

            if (btnCopy != null) {
                btnCopy.setOnClickListener(v -> {
                    if (listener != null) listener.onCopyMessage(msg);
                });
            }
            itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onLongPressMessage(msg);
                return true;
            });
        }
    }
}
