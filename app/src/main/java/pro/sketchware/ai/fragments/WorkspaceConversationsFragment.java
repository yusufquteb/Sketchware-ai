package pro.sketchware.ai.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.ai.activities.ChatActivity;
import pro.sketchware.ai.adapters.ConversationsAdapter;
import pro.sketchware.ai.models.Conversation;
import pro.sketchware.ai.storage.ConversationManager;
import pro.sketchware.databinding.FragmentWorkspaceConversationsBinding;

public class WorkspaceConversationsFragment extends Fragment
        implements ConversationsAdapter.OnConversationClickListener {

    private FragmentWorkspaceConversationsBinding binding;
    private ConversationManager conversationManager;
    private ConversationsAdapter adapter;
    private String workspaceId;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWorkspaceConversationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            workspaceId = args.getString("workspace_id");
        }
        if (workspaceId == null) return;

        conversationManager = new ConversationManager(requireContext());

        adapter = new ConversationsAdapter(this);
        binding.conversationsList.setAdapter(adapter);
        refreshConversations();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && workspaceId != null) {
            refreshConversations();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void refreshConversations() {
        if (binding == null || workspaceId == null) return;

        backgroundExecutor.execute(() -> {
            List<Conversation> conversations =
                    conversationManager.getConversationsForWorkspace(workspaceId);
            if (binding == null) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                adapter.setConversations(conversations);
                if (conversations.isEmpty()) {
                    binding.emptyState.setVisibility(View.VISIBLE);
                    binding.conversationsList.setVisibility(View.GONE);
                } else {
                    binding.emptyState.setVisibility(View.GONE);
                    binding.conversationsList.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    @Override
    public void onConversationClick(Conversation conversation) {
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversation.getId());
        intent.putExtra(ChatActivity.EXTRA_WORKSPACE_ID, workspaceId);
        startActivity(intent);
    }

    @Override
    public void onConversationLongClick(Conversation conversation) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ai_conversation_delete_title)
                .setMessage(getString(R.string.ai_conversation_delete_message, conversation.getTitle()))
                .setPositiveButton(R.string.common_word_delete, (dialog, which) -> {
                    conversationManager.deleteConversation(conversation.getId(), workspaceId);
                    refreshConversations();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }
}
