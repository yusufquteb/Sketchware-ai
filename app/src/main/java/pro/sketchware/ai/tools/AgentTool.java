package pro.sketchware.ai.tools;

import com.google.gson.JsonObject;

import pro.sketchware.ai.engine.risk.RiskLevel;
import pro.sketchware.ai.models.ToolResult;

/**
 * Interface that all AI agent tools must implement.
 * Each tool represents an action the AI agent can perform on a Sketchware Pro project.
 */
public interface AgentTool {

    /**
     * Returns the unique name of this tool, used as the function name in API calls.
     */
    String getName();

    /**
     * Returns a human-readable description of what this tool does.
     * This description is sent to the AI model to help it decide when to use the tool.
     */
    String getDescription();

    /**
     * Returns the JSON Schema describing the parameters this tool accepts.
     * The schema follows the JSON Schema specification and is used by the AI model
     * to generate valid tool call arguments.
     *
     * @return a JsonObject representing the parameters schema with "type", "properties",
     *         and "required" fields
     */
    JsonObject getParametersSchema();

    /**
     * Returns the risk level for this tool.
     * LOW      → read-only, no side effects.
     * MEDIUM   → modifies project files or state.
     * CRITICAL → destructive, irreversible, or executes code.
     *
     * Default is LOW — override in tools that modify or delete data.
     */
    default RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }

    /**
     * Executes the tool with the given arguments and context.
     *
     * @param arguments the parsed JSON arguments provided by the AI model
     * @param context   the execution context containing app context, workspace info,
     *                  and allowed project IDs
     * @return a ToolResult indicating success or failure with output or error message
     */
    ToolResult execute(JsonObject arguments, ToolContext context);
}
