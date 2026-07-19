package pro.sketchware.ai.orchestrator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ordered sequence of {@link PlanStep}s produced by the planning LLM call
 * in {@link AgentOrchestrator}, and executed in order via the existing
 * {@link pro.sketchware.ai.tools.ToolRegistry}.
 *
 * JSON wire shape (what the planning prompt asks the LLM to produce):
 * <pre>
 * {
 *   "steps": [
 *     { "tool": "create_from_template", "arguments": { ... }, "description": "..." },
 *     { "tool": "add_block",            "arguments": { ... }, "description": "..." }
 *   ]
 * }
 * </pre>
 * "arguments" must be a JSON object matching the target tool's
 * {@code getParametersSchema()}. The orchestrator does not rely on
 * {@code AgentExecutor.executeTool()}'s validation (that loop is not reused
 * here — see the design note in {@link AgentOrchestrator}'s class javadoc);
 * schema and runtime validation for each step is applied explicitly in
 * {@code AgentOrchestrator.executeStep()} instead.
 */
public class ExecutionPlan {

    private final List<PlanStep> steps = new ArrayList<>();
    /** Raw user request this plan was generated for — kept for logging/debugging. */
    private final String userRequest;

    public ExecutionPlan(String userRequest) {
        this.userRequest = userRequest;
    }

    public String getUserRequest() { return userRequest; }

    public List<PlanStep> getSteps() { return Collections.unmodifiableList(steps); }

    public void addStep(PlanStep step) { steps.add(step); }

    public boolean isEmpty() { return steps.isEmpty(); }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        if (userRequest != null) root.addProperty("user_request", userRequest);
        JsonArray arr = new JsonArray();
        for (PlanStep s : steps) arr.add(s.toJson());
        root.add("steps", arr);
        return root;
    }

    /**
     * Parses a plan out of raw LLM output. Tolerant of the model wrapping the
     * JSON in prose or a ```json fence — extracts the first {...} block found.
     *
     * @throws PlanParseException if no valid steps array could be extracted.
     */
    public static ExecutionPlan fromLlmOutput(String rawOutput, String userRequest) throws PlanParseException {
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            throw new PlanParseException("Empty planning response from model.");
        }
        String jsonText = extractJsonObject(rawOutput);
        if (jsonText == null) {
            throw new PlanParseException("No JSON object found in planning response.");
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(jsonText).getAsJsonObject();
        } catch (Exception e) {
            throw new PlanParseException("Malformed plan JSON: " + e.getMessage());
        }
        if (!root.has("steps") || !root.get("steps").isJsonArray()) {
            throw new PlanParseException("Plan JSON missing 'steps' array.");
        }
        ExecutionPlan plan = new ExecutionPlan(userRequest);
        JsonArray arr = root.getAsJsonArray("steps");
        int i = 0;
        for (com.google.gson.JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject stepObj = el.getAsJsonObject();
            String tool = stepObj.has("tool") ? stepObj.get("tool").getAsString() : null;
            if (tool == null || tool.trim().isEmpty()) {
                throw new PlanParseException("Step " + i + " missing 'tool' name.");
            }
            JsonObject args = stepObj.has("arguments") && stepObj.get("arguments").isJsonObject()
                    ? stepObj.getAsJsonObject("arguments") : new JsonObject();
            String desc = stepObj.has("description") ? stepObj.get("description").getAsString() : null;
            plan.addStep(new PlanStep(i, tool, args, desc));
            i++;
        }
        if (plan.isEmpty()) {
            throw new PlanParseException("Plan JSON parsed but contained zero valid steps.");
        }
        return plan;
    }

    /** Finds the first balanced {...} block in text, tolerant of ```json fences and surrounding prose. */
    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) { escape = false; }
                else if (c == '\\') { escape = true; }
                else if (c == '"') { inString = false; }
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return text.substring(start, i + 1);
            }
        }
        return null; // unbalanced
    }

    /** Thrown when the planning LLM's output cannot be parsed into a valid plan. */
    public static class PlanParseException extends Exception {
        public PlanParseException(String message) { super(message); }
    }
}
