package pro.sketchware.ai.api;

import com.google.gson.JsonObject;

/**
 * Represents a tool/function definition that can be sent to AI API providers.
 * Supports serialization to both OpenAI and Gemini tool formats.
 */
public class ToolDefinition {

    private final String name;
    private final String description;
    private final JsonObject parameters;

    /**
     * Creates a new tool definition.
     *
     * @param name        the function name (e.g., "create_project")
     * @param description a clear description of what the function does
     * @param parameters  JSON Schema object describing the function parameters
     */
    public ToolDefinition(String name, String description, JsonObject parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonObject getParameters() {
        return parameters;
    }

    /**
     * Serializes this tool definition to the OpenAI tools format.
     *
     * <pre>
     * {
     *   "type": "function",
     *   "function": {
     *     "name": "...",
     *     "description": "...",
     *     "parameters": { ... }
     *   }
     * }
     * </pre>
     */
    public JsonObject toOpenAiJson() {
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);
        if (parameters != null) {
            function.add("parameters", parameters);
        }

        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);
        return tool;
    }

    /**
     * Serializes this tool definition to the Gemini function declaration format.
     *
     * <pre>
     * {
     *   "name": "...",
     *   "description": "...",
     *   "parameters": { ... }
     * }
     * </pre>
     */
    public JsonObject toGeminiJson() {
        JsonObject declaration = new JsonObject();
        declaration.addProperty("name", name);
        declaration.addProperty("description", description);
        if (parameters != null) {
            declaration.add("parameters", parameters);
        }
        return declaration;
    }

    @Override
    public String toString() {
        return "ToolDefinition{name='" + name + "'}";
    }
}
