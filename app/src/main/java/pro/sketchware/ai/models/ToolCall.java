package pro.sketchware.ai.models;

import com.google.gson.JsonObject;

public class ToolCall {

    private final String id;
    private final String name;
    private final String arguments;
    private final String thoughtSignature;

    public ToolCall(String id, String name, String arguments) {
        this(id, name, arguments, null);
    }

    public ToolCall(String id, String name, String arguments, String thoughtSignature) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
        this.thoughtSignature = thoughtSignature;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getArguments() {
        return arguments;
    }

    public String getThoughtSignature() {
        return thoughtSignature;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("arguments", arguments);
        if (thoughtSignature != null && !thoughtSignature.isEmpty()) {
            json.addProperty("thoughtSignature", thoughtSignature);
        }
        return json;
    }

    public static ToolCall fromJson(JsonObject json) {
        if (json == null) return null;

        String id = json.has("id") && !json.get("id").isJsonNull()
                ? json.get("id").getAsString() : null;
        String name = json.has("name") && !json.get("name").isJsonNull()
                ? json.get("name").getAsString() : null;
        String arguments = json.has("arguments") && !json.get("arguments").isJsonNull()
                ? json.get("arguments").getAsString() : null;
        String thoughtSignature = null;
        if (json.has("thoughtSignature") && !json.get("thoughtSignature").isJsonNull()) {
            thoughtSignature = json.get("thoughtSignature").getAsString();
        } else if (json.has("thought_signature") && !json.get("thought_signature").isJsonNull()) {
            thoughtSignature = json.get("thought_signature").getAsString();
        }

        return new ToolCall(id, name, arguments, thoughtSignature);
    }

    @Override
    public String toString() {
        return "ToolCall{id='" + id + "', name='" + name + "'}";
    }
}
