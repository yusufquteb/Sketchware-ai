package pro.sketchware.ai.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Workspace {

    private String id;
    private String name;
    private String description;
    private List<String> projectIds;
    private long createdAt;
    private long updatedAt;

    public Workspace(String name, String description) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.projectIds = new ArrayList<>();
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Workspace(String id, String name, String description, List<String> projectIds,
                     long createdAt, long updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.projectIds = projectIds != null ? new ArrayList<>(projectIds) : new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = System.currentTimeMillis();
    }

    public List<String> getProjectIds() {
        return projectIds;
    }

    public void setProjectIds(List<String> projectIds) {
        this.projectIds = projectIds != null ? new ArrayList<>(projectIds) : new ArrayList<>();
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

    public void addProject(String scId) {
        if (scId != null && !projectIds.contains(scId)) {
            projectIds.add(scId);
            this.updatedAt = System.currentTimeMillis();
        }
    }

    public void removeProject(String scId) {
        if (projectIds.remove(scId)) {
            this.updatedAt = System.currentTimeMillis();
        }
    }

    public boolean hasProject(String scId) {
        return projectIds.contains(scId);
    }

    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("description", description);
        json.addProperty("createdAt", createdAt);
        json.addProperty("updatedAt", updatedAt);
        JsonArray projectIdsArray = new JsonArray();
        for (String projectId : projectIds) {
            projectIdsArray.add(projectId);
        }
        json.add("projectIds", projectIdsArray);
        return json;
    }

    public String toJson() {
        return toJsonObject().toString();
    }

    public static Workspace fromJsonObject(JsonObject json) {
        if (json == null) {
            return null;
        }
        String id = getString(json, "id", UUID.randomUUID().toString());
        String name = getString(json, "name", "Workspace");
        String description = getString(json, "description", "");
        long createdAt = getLong(json, "createdAt", System.currentTimeMillis());
        long updatedAt = getLong(json, "updatedAt", createdAt);
        ArrayList<String> projectIds = new ArrayList<>();
        if (json.has("projectIds") && json.get("projectIds").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("projectIds")) {
                if (!element.isJsonNull()) {
                    projectIds.add(element.getAsString());
                }
            }
        }
        return new Workspace(id, name, description, projectIds, createdAt, updatedAt);
    }

    public static Workspace fromJson(String json) {
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
        return "Workspace{id='" + id + "', name='" + name + "'}";
    }
}
