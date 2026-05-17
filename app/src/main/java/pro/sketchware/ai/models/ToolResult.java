package pro.sketchware.ai.models;

import com.google.gson.JsonObject;

public class ToolResult {

    private final String toolCallId;
    private final boolean success;
    private final String output;
    private final String error;

    public ToolResult(String toolCallId, boolean success, String output, String error) {
        this.toolCallId = toolCallId;
        this.success = success;
        this.output = output;
        this.error = error;
    }

    public static ToolResult success(String toolCallId, String output) {
        return new ToolResult(toolCallId, true, output, null);
    }

    public static ToolResult failure(String toolCallId, String error) {
        return new ToolResult(toolCallId, false, null, error);
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("toolCallId", toolCallId);
        json.addProperty("success", success);
        if (output != null) {
            json.addProperty("output", output);
        }
        if (error != null) {
            json.addProperty("error", error);
        }
        return json;
    }

    @Override
    public String toString() {
        return "ToolResult{toolCallId='" + toolCallId + "', success=" + success + "}";
    }
}
