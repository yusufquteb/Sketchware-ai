package pro.sketchware.ai.adapters;

import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.models.Conversation;
import pro.sketchware.databinding.ItemConversationBinding;

public class ConversationsAdapter extends ListAdapter<Conversation, ConversationsAdapter.ViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
        void onConversationLongClick(Conversation conversation);
    }

    private static final DiffUtil.ItemCallback<Conversation> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Conversation>() {
                @Override
                public boolean areItemsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
                    return safe(oldItem.getTitle()).equals(safe(newItem.getTitle()))
                            && safe(oldItem.getModelId()).equals(safe(newItem.getModelId()))
                            && safe(oldItem.getProviderName()).equals(safe(newItem.getProviderName()))
                            && oldItem.getUpdatedAt() == newItem.getUpdatedAt()
                            && oldItem.getCreatedAt() == newItem.getCreatedAt();
                }

                private String safe(String value) {
                    return value == null ? "" : value;
                }
            };

    private final OnConversationClickListener listener;

    public ConversationsAdapter(@NonNull OnConversationClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemConversationBinding binding = ItemConversationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation conversation = getItem(position);
        holder.bind(conversation);
    }

    @Override
    public long getItemId(int position) {
        Conversation conversation = getItem(position);
        if (conversation == null || conversation.getId() == null) {
            return RecyclerView.NO_ID;
        }
        return conversation.getId().hashCode();
    }

    public void setConversations(@NonNull List<Conversation> newConversations) {
        submitList(new ArrayList<>(newConversations));
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemConversationBinding binding;

        ViewHolder(@NonNull ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Conversation conversation) {
            binding.conversationTitle.setText(
                    TextUtils.isEmpty(conversation.getTitle())
                            ? "New Conversation"
                            : conversation.getTitle());

            String modelId = conversation.getModelId();
            binding.conversationModel.setText(
                    TextUtils.isEmpty(modelId) ? "No model selected" : modelId);

            long updatedAt = conversation.getUpdatedAt();
            if (updatedAt > 0) {
                CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                        updatedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE);
                binding.conversationTime.setText(relativeTime);
            } else {
                binding.conversationTime.setText("");
            }

            binding.getRoot().setOnClickListener(v -> listener.onConversationClick(conversation));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onConversationLongClick(conversation);
                return true;
            });
        }
    }
}
