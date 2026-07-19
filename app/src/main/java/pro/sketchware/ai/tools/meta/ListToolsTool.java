package pro.sketchware.ai.tools.meta;

import com.google.gson.JsonObject;

import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;
import pro.sketchware.ai.tools.ToolRegistry;

/**
 * MVP lazy-discovery tool. Returns tool names + one-line descriptions from the full
 * registry (all 106+, not just the 7 essential ones sent by default — see
 * {@link ToolRegistry#getEssentialTools()}). The model calls this when it needs a
 * capability outside the essential set, then calls the discovered tool by name
 * directly on its next turn.
 *
 * Supports an optional {@code query} argument: when present, only tools whose name
 * or description contains the query (case-insensitive substring match) are returned.
 * This keeps each list_tools call itself cheap — returning all 106+ tools in one shot
 * costs real tokens too, so a targeted call (e.g. list_tools(query="resource")) is
 * both faster for the model to scan and cheaper than the unfiltered dump. Omitting
 * {@code query} (or passing an empty string) returns the full unfiltered list, same
 * as before this was added. No fuzzy matching, no ranking, no category logic — plain
 * substring search is deliberately the simplest thing that works for MVP.
 *
 * Holds a reference to the same {@link ToolRegistry} instance {@code AgentExecutor}
 * already constructs; does not create a second registry instance.
 */
public class ListToolsTool implements AgentTool {

    private final ToolRegistry registry;

    public ListToolsTool(ToolRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String getName() {
        return "list_tools";
    }

    @Override
    public String getDescription() {
        return "Lists available tools by name with a one-line description. Call this when you "
                + "need a capability that isn't in your currently available tool set. Pass an "
                + "optional 'query' to search by keyword (e.g. query=\"resource\") and get back "
                + "only matching tools instead of the full catalog — prefer this over an "
                + "unfiltered call when you have an idea what you're looking for. After finding "
                + "the tool you need, call it directly by name on your next turn.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description",
                "Optional keyword to filter tools by (matched against name and description, "
                        + "case-insensitive). Omit to list every tool.");

        JsonObject properties = new JsonObject();
        properties.add("query", queryProp);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        // query is optional — no "required" array.
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject arguments, ToolContext context) {
        String query = null;
        if (arguments != null && arguments.has("query") && !arguments.get("query").isJsonNull()) {
            query = arguments.get("query").getAsString().trim();
        }
        boolean hasQuery = query != null && !query.isEmpty();
        String queryLower = hasQuery ? query.toLowerCase(java.util.Locale.ROOT) : null;

        StringBuilder sb = new StringBuilder();
        int matched = 0;
        for (AgentTool tool : registry.getAllTools()) {
            if (hasQuery) {
                String name = tool.getName() != null ? tool.getName().toLowerCase(java.util.Locale.ROOT) : "";
                String desc = tool.getDescription() != null
                        ? tool.getDescription().toLowerCase(java.util.Locale.ROOT) : "";
                if (!name.contains(queryLower) && !desc.contains(queryLower)) {
                    continue;
                }
            }
            sb.append(tool.getName()).append(" — ").append(tool.getDescription()).append("\n");
            matched++;
        }

        if (hasQuery && matched == 0) {
            return success("No tools matched \"" + query + "\". Try a broader keyword, or call "
                    + "list_tools with no query to see the full catalog.");
        }
        return success(sb.toString());
    }

    // Matches the private success()/error() helper convention used by every other
    // AgentTool implementation in this codebase (see e.g. ProjectTools) — toolCallId
    // is left null here and filled in later by the dispatcher, same as those tools.
    private static ToolResult success(String output) {
        return new ToolResult(null, true, output, null);
    }
}
