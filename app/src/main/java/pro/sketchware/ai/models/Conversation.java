package pro.sketchware.ai.models;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.UUID;

public class Conversation {

    private String id;
    private String workspaceId;
    private String title;
    private long createdAt;
    private long updatedAt;
    private String modelId;
    private String providerName;

    public Conversation(String workspaceId, String title, String modelId, String providerName) {
        this.id = UUID.randomUUID().toString();
        this.workspaceId = workspaceId;
        this.title = title;
        this.modelId = modelId;
        this.providerName = providerName;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    private Conversation(String id, String workspaceId, String title, long createdAt, long updatedAt,
                         String modelId, String providerName) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.modelId = modelId;
        this.providerName = providerName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
        this.updatedAt = System.currentTimeMillis();
    }

    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("workspaceId", workspaceId);
        json.addProperty("title", title);
        json.addProperty("createdAt", createdAt);
        json.addProperty("updatedAt", updatedAt);
        json.addProperty("modelId", modelId);
        json.addProperty("providerName", providerName);
        return json;
    }

    public String toJson() {
        return toJsonObject().toString();
    }

    public static Conversation fromJsonObject(JsonObject json) {
        if (json == null) {
            return null;
        }
        String id = getString(json, "id", UUID.randomUUID().toString());
        String workspaceId = getString(json, "workspaceId", null);
        String title = getString(json, "title", "New Conversation");
        long createdAt = getLong(json, "createdAt", System.currentTimeMillis());
        long updatedAt = getLong(json, "updatedAt", createdAt);
        String modelId = getString(json, "modelId", null);
        String providerName = getString(json, "providerName", null);
        return new Conversation(id, workspaceId, title, createdAt, updatedAt, modelId, providerName);
    }

    public static Conversation fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return fromJsonObject(new JsonParser().parse(json).getAsJsonObject());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getString(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    private static long getLong(JsonObject json, String key, long fallback) {
        try {
            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsLong() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    public String toString() {
        return "Conversation{id='" + id + "', title='" + title + "', workspaceId='" + workspaceId + "'}";
    }
}
