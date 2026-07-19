package pro.sketchware.ai.tools;

import com.google.gson.JsonObject;

import pro.sketchware.ai.engine.risk.RiskLevel;
import pro.sketchware.ai.models.ToolResult;

/**
 * Interface that all AI agent tools must implement.
 * Each tool represents an action the AI agent can perform on a Sketchware Pro project.
 */
public interface AgentTool {

    /** Broad functional category for UI grouping and filtering. */
    enum Category {
        PROJECT,
        FILES,
        ACTIVITIES,
        LAYOUT,
        RESOURCES,
        LIBRARIES,
        BUILD,
        BLOCKS,
        SNAPSHOTS,
        ANALYSIS,
        EXPORT,
        DEV_UTILS,
    }

    /** Returns the unique name of this tool, used as the function name in API calls. */
    String getName();

    /** Returns a human-readable description of what this tool does. */
    String getDescription();

    /**
     * Returns the JSON Schema describing the parameters this tool accepts.
     *
     * @return a JsonObject with "type", "properties", and "required" fields
     */
    JsonObject getParametersSchema();

    /**
     * Returns the risk level for this tool.
     * LOW      → read-only, no side effects.
     * MEDIUM   → modifies project files or state.
     * CRITICAL → destructive, irreversible, or executes code.
     */
    default RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }

    /**
     * Returns the functional category for this tool.
     * Used for grouping in the UI tool browser and for RuntimeToolValidator scope checks.
     */
    default Category getCategory() {
        return Category.DEV_UTILS;
    }

    /**
     * Returns true when this tool requires an active project in the session scope.
     * RuntimeToolValidator will reject calls to project-scoped tools when no project is open.
     */
    default boolean requiresProject() {
        return false;
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
