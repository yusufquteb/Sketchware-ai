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
        File file = new File(conversationsBaseDir, workspaceId + "/" + id + ".json");
        if (!file.exists()) {
            return null;
        }
        return Conversation.fromJson(FileUtil.readFile(file.getAbsolutePath()));
    }

    @NonNull
    public List<Conversation> getConversationsForWorkspace(@NonNull String workspaceId) {
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
        File file = new File(conversationsBaseDir, workspaceId + "/" + id + ".json");
        if (file.exists()) {
            file.delete();
        }
        deleteMessages(id);
    }

    public void deleteAllConversationsForWorkspace(@NonNull String workspaceId) {
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
        List<ChatMessage> messages = getMessages(conversationId);
        messages.add(message);
        writeMessages(conversationId, messages);
    }

    @NonNull
    public List<ChatMessage> getMessages(@NonNull String conversationId) {
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
            JsonArray array = new JsonParser().parse(content).getAsJsonArray();
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
        return messages;
    }

    public void updateLastMessage(@NonNull String conversationId, @NonNull ChatMessage message) {
        List<ChatMessage> messages = getMessages(conversationId);
        if (!messages.isEmpty()) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i).getId().equals(message.getId())) {
                    messages.set(i, message);
                    writeMessages(conversationId, messages);
                    return;
                }
            }
        }
        messages.add(message);
        writeMessages(conversationId, messages);
    }

    public void deleteMessages(@NonNull String conversationId) {
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

    private void writeMessages(@NonNull String conversationId, @NonNull List<ChatMessage> messages) {
        File messagesDir = new File(messagesBaseDir, conversationId);
        if (!messagesDir.exists()) {
            messagesDir.mkdirs();
        }
        JsonArray array = new JsonArray();
        for (ChatMessage message : messages) {
            array.add(message.toJson());
        }
        FileUtil.writeFile(new File(messagesDir, "messages.json").getAbsolutePath(), array.toString());
    }
}
