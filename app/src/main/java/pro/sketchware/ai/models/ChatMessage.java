package pro.sketchware.ai.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatMessage {

    private final String id;
    private final String conversationId;
    private final String role;
    private String content;
    private List<ToolCall> toolCalls;
    private String toolCallId;
    private String toolName;
    private final long timestamp;
    private transient boolean isStreaming;
    /** Pinned messages are never pruned by TokenOptimizer — use for critical context. */
    private boolean pinned = false;

    public ChatMessage(String conversationId, String content) {
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.role = "user";
        this.content = content;
        this.toolCalls = null;
        this.toolCallId = null;
        this.toolName = null;
        this.timestamp = System.currentTimeMillis();
        this.isStreaming = false;
    }

    public ChatMessage(String conversationId, String content, List<ToolCall> toolCalls) {
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.role = "assistant";
        this.content = content;
        this.toolCalls = toolCalls != null ? new ArrayList<>(toolCalls) : null;
        this.toolCallId = null;
        this.toolName = null;
        this.timestamp = System.currentTimeMillis();
        this.isStreaming = false;
    }

    public ChatMessage(String conversationId, String toolCallId, String toolName, String content, boolean isToolResult) {
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.role = "tool";
        this.content = content;
        this.toolCalls = null;
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.timestamp = System.currentTimeMillis();
        this.isStreaming = false;
    }

    private ChatMessage(String id, String conversationId, String role, String content,
                        List<ToolCall> toolCalls, String toolCallId, String toolName, long timestamp) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.timestamp = timestamp;
        this.isStreaming = false;
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls != null ? new ArrayList<>(toolCalls) : null;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public void setStreaming(boolean streaming) {
        this.isStreaming = streaming;
    }

    public boolean isPinned() { return pinned; }

    /** Marks this message as pinned so TokenOptimizer never prunes it. */
    public ChatMessage setPinned(boolean pinned) {
        this.pinned = pinned;
        return this;
    }

    public static ChatMessage userMessage(String conversationId, String content) {
        return new ChatMessage(conversationId, content);
    }

    public static ChatMessage assistantMessage(String content, List<ToolCall> toolCalls) {
        return new ChatMessage(null, content, toolCalls);
    }

    public static ChatMessage toolResultMessage(String toolCallId, String content) {
        return new ChatMessage(null, toolCallId, null, content, true);
    }

    public static ChatMessage toolResultMessage(String toolCallId, String toolName, String content) {
        return new ChatMessage(null, toolCallId, toolName, content, true);
    }

    /**
     * Creates a system-role message used for automatic feedback injection
     * (e.g. auto-fix loop after a build failure).
     */
    public static ChatMessage systemMessage(String content) {
        return new ChatMessage(UUID.randomUUID().toString(), null, "system", content,
                null, null, null, System.currentTimeMillis());
    }

    public void appendContent(String chunk) {
        if (chunk == null) return;
        if (this.content == null) {
            this.content = chunk;
        } else {
            this.content += chunk;
        }
    }

    public boolean hasVisibleAssistantContent() {
        return content != null && !content.trim().isEmpty();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("conversationId", conversationId);
        json.addProperty("role", role);
        json.addProperty("content", content);
        json.addProperty("toolCallId", toolCallId);
        json.addProperty("toolName", toolName);
        json.addProperty("timestamp", timestamp);
        if (pinned) json.addProperty("pinned", true);

        if (toolCalls != null && !toolCalls.isEmpty()) {
            JsonArray callsArray = new JsonArray();
            for (ToolCall call : toolCalls) {
                callsArray.add(call.toJson());
            }
            json.add("toolCalls", callsArray);
        }

        return json;
    }

    public static ChatMessage fromJson(JsonObject json) {
        if (json == null) return null;

        String id = json.has("id") && !json.get("id").isJsonNull()
                ? json.get("id").getAsString() : UUID.randomUUID().toString();
        String conversationId = json.has("conversationId") && !json.get("conversationId").isJsonNull()
                ? json.get("conversationId").getAsString() : null;
        String role = json.has("role") && !json.get("role").isJsonNull()
                ? json.get("role").getAsString() : "user";
        String content = json.has("content") && !json.get("content").isJsonNull()
                ? json.get("content").getAsString() : null;
        String toolCallId = json.has("toolCallId") && !json.get("toolCallId").isJsonNull()
                ? json.get("toolCallId").getAsString() : null;
        String toolName = json.has("toolName") && !json.get("toolName").isJsonNull()
                ? json.get("toolName").getAsString() : null;
        long timestamp = json.has("timestamp") && !json.get("timestamp").isJsonNull()
                ? json.get("timestamp").getAsLong() : System.currentTimeMillis();

        List<ToolCall> toolCalls = null;
        if (json.has("toolCalls") && json.get("toolCalls").isJsonArray()) {
            toolCalls = new ArrayList<>();
            JsonArray callsArray = json.getAsJsonArray("toolCalls");
            for (JsonElement element : callsArray) {
                if (element.isJsonObject()) {
                    ToolCall call = ToolCall.fromJson(element.getAsJsonObject());
                    if (call != null) {
                        toolCalls.add(call);
                    }
                }
            }
            if (toolCalls.isEmpty()) {
                toolCalls = null;
            }
        }

        ChatMessage msg = new ChatMessage(id, conversationId, role, content,
                toolCalls, toolCallId, toolName, timestamp);
        if (json.has("pinned") && json.get("pinned").getAsBoolean()) {
            msg.pinned = true;
        }
        return msg;
    }

    @Override
    public String toString() {
        return "ChatMessage{id='" + id + "', role='" + role + "', conversationId='" + conversationId + "'}";
    }
}
