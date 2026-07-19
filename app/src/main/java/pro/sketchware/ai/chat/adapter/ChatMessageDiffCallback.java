package pro.sketchware.ai.chat.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

import pro.sketchware.ai.chat.model.ChatMessage;

/**
 * ChatMessageDiffCallback — DiffUtil implementation for the chat message list.
 *
 * <p>Performance rules (MANDATORY):
 * <ul>
 *   <li>NEVER call {@code notifyDataSetChanged()} anywhere in the adapter.</li>
 *   <li>ALL list updates MUST go through
 *       {@link androidx.recyclerview.widget.ListAdapter} or a manual
 *       {@link DiffUtil#calculateDiff} + {@code dispatchUpdatesTo()} call.</li>
 *   <li>This class is stateless and can be instantiated per diff operation.</li>
 * </ul>
 *
 * <p>Identity vs Content:
 * <ul>
 *   <li>{@link #areItemsTheSame} — uses stable {@link ChatMessage#getId()} for identity.</li>
 *   <li>{@link #areContentsTheSame} — uses {@link ChatMessage#contentEquals} for payload diff.</li>
 * </ul>
 */
public final class ChatMessageDiffCallback extends DiffUtil.Callback {

    @NonNull
    private final java.util.List<ChatMessage> oldList;

    @NonNull
    private final java.util.List<ChatMessage> newList;

    public ChatMessageDiffCallback(
            @NonNull java.util.List<ChatMessage> oldList,
            @NonNull java.util.List<ChatMessage> newList
    ) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    /**
     * Two messages are the "same item" if they share the same stable ID.
     * This allows DiffUtil to detect moves and avoid full rebinds.
     */
    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        ChatMessage oldItem = oldList.get(oldItemPosition);
        ChatMessage newItem = newList.get(newItemPosition);
        return oldItem.getId().equals(newItem.getId());
    }

    /**
     * Two messages have the "same content" if all visible payload fields match.
     * When false, the adapter will call {@link ChatMessageAdapter#onBindViewHolder}
     * on the affected position with a change payload.
     */
    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        ChatMessage oldItem = oldList.get(oldItemPosition);
        ChatMessage newItem = newList.get(newItemPosition);
        return oldItem.contentEquals(newItem);
    }

    /**
     * Returns a non-null payload so {@link ChatMessageAdapter#onBindViewHolder}
     * can perform partial rebind (e.g. only update text during streaming)
     * instead of a full rebind.
     *
     * <p>Currently returns a simple Boolean change marker.
     * Future: return an enum or bitmask for finer-grained partial updates.
     */
    @Nullable
    @Override
    public Object getChangePayload(int oldItemPosition, int newItemPosition) {
        // Non-null payload triggers the partial-bind path in the adapter.
        return Boolean.TRUE;
    }
}
