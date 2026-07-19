package pro.sketchware.ai.orchestrator;

import com.google.gson.JsonObject;

/**
 * One step of an {@link ExecutionPlan}: a single tool call the orchestrator
 * intends to make, plus its outcome once executed.
 *
 * Mirrors the shape of {@link pro.sketchware.ai.models.ToolCall} /
 * {@link pro.sketchware.ai.models.ToolResult} closely on purpose, since a
 * PlanStep is executed by handing its (toolName, arguments) straight to
 * the existing {@link pro.sketchware.ai.tools.ToolRegistry} — see
 * AgentOrchestrator design note in CHANGES.md for why no new execution
 * path was introduced.
 */
public class PlanStep {

    public enum Status { PENDING, RUNNING, SUCCEEDED, FAILED }

    private final int index;
    private final String toolName;
    private final JsonObject arguments;
    private final String description;

    private Status status = Status.PENDING;
    private String output;
    private String error;

    public PlanStep(int index, String toolName, JsonObject arguments, String description) {
        this.index = index;
        this.toolName = toolName;
        this.arguments = arguments != null ? arguments : new JsonObject();
        this.description = description;
    }

    public int getIndex() { return index; }
    public String getToolName() { return toolName; }
    public JsonObject getArguments() { return arguments; }
    public String getDescription() { return description; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    /** Serialises this step to the JSON shape used by {@link ExecutionPlan#toJson()}. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("index", index);
        o.addProperty("tool", toolName);
        o.add("arguments", arguments);
        if (description != null) o.addProperty("description", description);
        o.addProperty("status", status.name());
        if (output != null) o.addProperty("output", output);
        if (error != null) o.addProperty("error", error);
        return o;
    }
}
