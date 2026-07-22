package pro.sketchware.ai.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.Conversation;
import pro.sketchware.utility.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConversationManager {

    private final File conversationsBaseDir;
    private final File messagesBaseDir;

    /**
     * Guards all read-modify-write operations on message files.
     * One lock per conversation ID; prevents concurrent saveMessage() calls
     * from the AI streaming thread and the UI thread racing and losing data.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Object> messageLocks =
            new java.util.concurrent.ConcurrentHashMap<>();

    public ConversationManager(@NonNull Context context) {
        conversationsBaseDir = new File(context.getFilesDir(), "ai_agent/conversations");
        messagesBaseDir = new File(context.getFilesDir(), "ai_agent/messages");
        if (!conversationsBaseDir.exists()) {
            conversationsBaseDir.mkdirs();
        }
        if (!messagesBaseDir.exists()) {
            messagesBaseDir.mkdirs();
        }
    }

    private Object lockFor(@NonNull String conversationId) {
        return messageLocks.computeIfAbsent(conversationId, k -> new Object());
    }

    private static void validateId(@NonNull String id) {
        if (id.contains("..") || id.contains("/") || id.contains("\\")) {
            throw new IllegalArgumentException("Invalid ID contains path separators: " + id);
        }
    }

    public void saveConversation(@NonNull Conversation conversation) {
        File workspaceDir = new File(conversationsBaseDir, conversation.getWorkspaceId());
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs();
        }
        File file = new File(workspaceDir, conversation.getId() + ".json");
        FileUtil.writeFile(file.getAbsolutePath(), conversation.toJson());
    }

    @Nullable
    public Conversation getConversation(@NonNull String id, @NonNull String workspaceId) {
        validateId(id);
        validateId(workspaceId);
        File file = new File(conversationsBaseDir, workspaceId + "/" + id + ".json");
        if (!file.exists()) {
            return null;
        }
        return Conversation.fromJson(FileUtil.readFile(file.getAbsolutePath()));
    }

    @NonNull
    public List<Conversation> getConversationsForWorkspace(@NonNull String workspaceId) {
        validateId(workspaceId);
        List<Conversation> conversations = new ArrayList<>();
        File workspaceDir = new File(conversationsBaseDir, workspaceId);
        if (!workspaceDir.exists() || !workspaceDir.isDirectory()) {
            return conversations;
        }
        File[] files = workspaceDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return conversations;
        }
        for (File file : files) {
            Conversation conversation = Conversation.fromJson(FileUtil.readFile(file.getAbsolutePath()));
            if (conversation != null) {
                conversations.add(conversation);
            }
        }
        Collections.sort(conversations, (a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()));
        return conversations;
    }

    public void deleteConversation(@NonNull String id, @NonNull String workspaceId) {
        validateId(id);
        validateId(workspaceId);
        File file = new File(conversationsBaseDir, workspaceId + "/" + id + ".json");
        if (file.exists()) {
            file.delete();
        }
        deleteMessages(id);
    }

    /**
     * Deletes every conversation stored under a "assistant_"-prefixed workspace ID — the
     * namespace {@code AiAssistantBottomSheet} (the floating, per-screen assistant opened from
     * places like Manage Sound/Image or Compile Log) uses, as opposed to the dedicated Agent
     * tab's own workspace IDs (real {@link pro.sketchware.ai.models.Workspace} entries or a
     * plain project scId). Called on app exit so these ad-hoc, page-scoped chats never
     * accumulate indefinitely — the main Agent tab's history is untouched since it never uses
     * this prefix.
     */
    public void deleteAllAssistantSheetConversations() {
        if (!conversationsBaseDir.exists() || !conversationsBaseDir.isDirectory()) return;
        File[] workspaceDirs = conversationsBaseDir.listFiles(File::isDirectory);
        if (workspaceDirs == null) return;
        for (File dir : workspaceDirs) {
            if (dir.getName().startsWith("assistant_")) {
                deleteAllConversationsForWorkspace(dir.getName());
            }
        }
    }

    public void deleteAllConversationsForWorkspace(@NonNull String workspaceId) {
        validateId(workspaceId);
        File workspaceDir = new File(conversationsBaseDir, workspaceId);
        if (workspaceDir.exists() && workspaceDir.isDirectory()) {
            File[] files = workspaceDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    if (name.endsWith(".json")) {
                        String conversationId = name.substring(0, name.length() - 5);
                        deleteMessages(conversationId);
                    }
                    file.delete();
                }
            }
            workspaceDir.delete();
        }
    }

    public void saveMessage(@NonNull String conversationId, @NonNull ChatMessage message) {
        validateId(conversationId);
        synchronized (lockFor(conversationId)) {
            List<ChatMessage> messages = getMessages(conversationId);
            messages.add(message);
            writeMessagesAtomic(conversationId, messages);
        }
    }

    @NonNull
    public List<ChatMessage> getMessages(@NonNull String conversationId) {
        validateId(conversationId);
        File messagesDir = new File(messagesBaseDir, conversationId);
        File file = new File(messagesDir, "messages.json");
        if (!file.exists()) {
            return new ArrayList<>();
        }
        String content = FileUtil.readFile(file.getAbsolutePath());
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }
        ArrayList<ChatMessage> messages = new ArrayList<>();
        try {
            JsonArray array = JsonParser.parseString(content).getAsJsonArray();
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    ChatMessage message = ChatMessage.fromJson(element.getAsJsonObject());
                    if (message != null) {
                        messages.add(message);
                    }
                }
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        Collections.sort(messages, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
        // Recovery: remove trailing empty assistant messages — they are streaming
        // artifacts from a process crash mid-generation and should not be shown.
        while (!messages.isEmpty()) {
            pro.sketchware.ai.models.ChatMessage last = messages.get(messages.size() - 1);
            boolean isEmptyAssistant = "assistant".equals(last.getRole())
                    && (last.getContent() == null || last.getContent().trim().isEmpty())
                    && (last.getToolCalls() == null || last.getToolCalls().isEmpty());
            if (isEmptyAssistant) {
                messages.remove(messages.size() - 1);
            } else {
                break;
            }
        }
        return messages;
    }

    public void updateLastMessage(@NonNull String conversationId, @NonNull ChatMessage message) {
        synchronized (lockFor(conversationId)) {
            List<ChatMessage> messages = getMessages(conversationId);
            boolean found = false;
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i).getId().equals(message.getId())) {
                    messages.set(i, message);
                    found = true;
                    break;
                }
            }
            if (!found) messages.add(message);
            writeMessagesAtomic(conversationId, messages);
        }
    }

    public void deleteMessages(@NonNull String conversationId) {
        validateId(conversationId);
        File messagesDir = new File(messagesBaseDir, conversationId);
        if (messagesDir.exists() && messagesDir.isDirectory()) {
            File[] files = messagesDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            messagesDir.delete();
        }
    }

    /**
     * Atomic write: serializes to a temp file then renames to the real file.
     * Prevents partial writes from corrupting data on crash mid-write.
     */
    private void writeMessagesAtomic(@NonNull String conversationId,
                                     @NonNull List<ChatMessage> messages) {
        File messagesDir = new File(messagesBaseDir, conversationId);
        if (!messagesDir.exists()) {
            messagesDir.mkdirs();
        }
        JsonArray array = new JsonArray();
        for (ChatMessage message : messages) {
            array.add(message.toJson());
        }
        String json = array.toString();
        File target = new File(messagesDir, "messages.json");
        File tmp    = new File(messagesDir, "messages.json.tmp");
        FileUtil.writeFile(tmp.getAbsolutePath(), json);
        if (tmp.exists()) {
            // Rename is atomic on most filesystems
            if (!tmp.renameTo(target)) {
                // Fallback: direct write if rename fails
                FileUtil.writeFile(target.getAbsolutePath(), json);
                tmp.delete();
            }
        }
    }
}
