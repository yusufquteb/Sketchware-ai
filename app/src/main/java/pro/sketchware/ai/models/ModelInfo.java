package pro.sketchware.ai.models;

import com.google.gson.JsonObject;

import java.util.Objects;

public class ModelInfo implements Comparable<ModelInfo> {

    private final String id;
    private final String name;
    private final AiProvider provider;
    private final long contextLength;
    private final String description;

    // Optional metadata fields populated by providers that expose them
    private final int maxTokens;
    private final boolean supportsStreaming;
    private final boolean supportsNonStreaming;
    private final String status;

    public ModelInfo(String id, String name, AiProvider provider, long contextLength, String description) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.contextLength = contextLength;
        this.description = description;
        this.maxTokens = 0;
        this.supportsStreaming = true;
        this.supportsNonStreaming = true;
        this.status = null;
    }

    private ModelInfo(String id, String name, AiProvider provider, long contextLength,
                      String description, int maxTokens, boolean supportsStreaming,
                      boolean supportsNonStreaming, String status) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.contextLength = contextLength;
        this.description = description;
        this.maxTokens = maxTokens;
        this.supportsStreaming = supportsStreaming;
        this.supportsNonStreaming = supportsNonStreaming;
        this.status = status;
    }

    /** Returns a new ModelInfo with additional metadata fields set. */
    public ModelInfo withMetadata(int maxTokens, boolean supportsStreaming,
                                  boolean supportsNonStreaming, String status) {
        return new ModelInfo(id, name, provider, contextLength, description,
                maxTokens, supportsStreaming, supportsNonStreaming, status);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public long getContextLength() {
        return contextLength;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public boolean supportsStreaming() {
        return supportsStreaming;
    }

    public boolean supportsNonStreaming() {
        return supportsNonStreaming;
    }

    public String getStatus() {
        return status;
    }

    /** Returns true if the model status is operational or stable. */
    public boolean isOperational() {
        if (status == null || status.isEmpty()) return true; // unknown = assume operational
        String s = status.toLowerCase();
        return "operational".equals(s) || "stable".equals(s);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelInfo modelInfo = (ModelInfo) o;
        return Objects.equals(id, modelInfo.id) && provider == modelInfo.provider;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, provider);
    }

    @Override
    public int compareTo(ModelInfo other) {
        if (other == null) return 1;
        String thisName = name != null ? name : "";
        String otherName = other.name != null ? other.name : "";
        return thisName.compareToIgnoreCase(otherName);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("provider", provider != null ? provider.name() : null);
        json.addProperty("contextLength", contextLength);
        json.addProperty("description", description);
        if (maxTokens > 0) json.addProperty("maxTokens", maxTokens);
        if (!supportsStreaming) json.addProperty("supportsStreaming", false);
        if (!supportsNonStreaming) json.addProperty("supportsNonStreaming", false);
        if (status != null) json.addProperty("status", status);
        return json;
    }

    public static ModelInfo fromJson(JsonObject json) {
        if (json == null) return null;

        String id = json.has("id") && !json.get("id").isJsonNull()
                ? json.get("id").getAsString() : null;
        String name = json.has("name") && !json.get("name").isJsonNull()
                ? json.get("name").getAsString() : null;
        AiProvider provider = json.has("provider") && !json.get("provider").isJsonNull()
                ? AiProvider.fromName(json.get("provider").getAsString()) : null;
        long contextLength = json.has("contextLength") && !json.get("contextLength").isJsonNull()
                ? json.get("contextLength").getAsLong() : 0L;
        String description = json.has("description") && !json.get("description").isJsonNull()
                ? json.get("description").getAsString() : null;

        int maxTokens = json.has("maxTokens") && !json.get("maxTokens").isJsonNull()
                ? json.get("maxTokens").getAsInt() : 0;
        boolean supportsStreaming = !json.has("supportsStreaming") || json.get("supportsStreaming").getAsBoolean();
        boolean supportsNonStreaming = !json.has("supportsNonStreaming") || json.get("supportsNonStreaming").getAsBoolean();
        String status = json.has("status") && !json.get("status").isJsonNull()
                ? json.get("status").getAsString() : null;

        return new ModelInfo(id, name, provider, contextLength, description,
                maxTokens, supportsStreaming, supportsNonStreaming, status);
    }

    @Override
    public String toString() {
        return "ModelInfo{id='" + id + "', name='" + name + "', provider=" + provider + "}";
    }
}
