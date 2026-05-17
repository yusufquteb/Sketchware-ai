package pro.sketchware.ai.tools.impl.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import pro.sketchware.ai.tools.Tool;

/**
 * ParseJsonTool — Parses, validates, and pretty-prints JSON content.
 *
 * <p>Accepts raw JSON text and returns a structured analysis including:
 * type detection, key listing (for objects), length (for arrays),
 * and a pretty-printed version.
 *
 * <p><b>Expected JSON input:</b>
 * <pre>
 * {
 *   "json_text": "{\"key\": \"value\", ...}",
 *   "query":     "$.key.subkey"   // optional JSONPath-like query
 * }
 * </pre>
 *
 * <p>The {@code query} field supports simple dot-notation key access
 * (e.g., {@code "user.name"}) without a full JSONPath library dependency.
 */
public class ParseJsonTool implements Tool {

    public static final String NAME = "parse_json";

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Parses and validates JSON text. Returns structure analysis, "
                + "key listing, and pretty-printed output. "
                + "Supports optional dot-notation key queries (e.g., 'user.name').";
    }

    @Nullable
    @Override
    public String getInputSchema() {
        return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "  \"json_text\":{\"type\":\"string\",\"description\":\"The raw JSON string to parse\"},"
                + "  \"query\":{\"type\":\"string\",\"description\":\"Optional dot-notation query (e.g., 'user.name')\"}"
                + "},"
                + "\"required\":[\"json_text\"]"
                + "}";
    }

    @NonNull
    @Override
    public ToolResult execute(@Nullable String jsonInput) {
        // ── 1. Parse tool input ────────────────────────────────────────────
        if (jsonInput == null || jsonInput.trim().isEmpty()) {
            return ToolResult.failure(NAME,
                    "Input required. Provide {\"json_text\": \"...\"}");
        }

        String jsonText;
        String query;
        try {
            JSONObject input = new JSONObject(jsonInput);
            jsonText = input.optString("json_text", "").trim();
            query    = input.optString("query", "").trim();
        } catch (JSONException e) {
            return ToolResult.failure(NAME, "Invalid tool input JSON: " + e.getMessage());
        }

        if (jsonText.isEmpty()) {
            return ToolResult.failure(NAME, "'json_text' field is required.");
        }

        // ── 2. Parse the target JSON ───────────────────────────────────────
        Object parsed;
        String type;
        String prettyPrinted;
        String analysis;

        try {
            if (jsonText.trim().startsWith("[")) {
                JSONArray arr = new JSONArray(jsonText);
                parsed       = arr;
                type         = "array";
                prettyPrinted = arr.toString(2);
                analysis = buildArrayAnalysis(arr);
            } else {
                JSONObject obj = new JSONObject(jsonText);
                parsed       = obj;
                type         = "object";
                prettyPrinted = obj.toString(2);
                analysis = buildObjectAnalysis(obj);
            }
        } catch (JSONException e) {
            return ToolResult.failure(NAME,
                    "Invalid JSON: " + e.getMessage()
                    + "\n\nThe provided text is not valid JSON.");
        }

        // ── 3. Optional query ──────────────────────────────────────────────
        String queryResult = "";
        if (!query.isEmpty() && parsed instanceof JSONObject) {
            queryResult = "\n─── Query: '" + query + "' ───\n"
                    + resolveQuery((JSONObject) parsed, query);
        }

        // ── 4. Build result ────────────────────────────────────────────────
        String result = "JSON Type: " + type + "\n"
                + analysis
                + queryResult
                + "\n─── Pretty-printed ───\n"
                + prettyPrinted;

        return ToolResult.successJson(result);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    @NonNull
    private String buildObjectAnalysis(@NonNull JSONObject obj) {
        StringBuilder sb = new StringBuilder();
        sb.append("Keys (").append(obj.length()).append("): ");
        boolean first = true;
        java.util.Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            if (!first) sb.append(", ");
            sb.append(keys.next());
            first = false;
        }
        sb.append('\n');
        return sb.toString();
    }

    @NonNull
    private String buildArrayAnalysis(@NonNull JSONArray arr) {
        return "Length: " + arr.length() + " items\n";
    }

    @NonNull
    private String resolveQuery(@NonNull JSONObject root, @NonNull String query) {
        String[] parts = query.split("\\.");
        Object current = root;

        for (String part : parts) {
            if (current instanceof JSONObject) {
                current = ((JSONObject) current).opt(part);
                if (current == null) {
                    return "Key '" + part + "' not found.";
                }
            } else {
                return "Cannot navigate into non-object at key '" + part + "'.";
            }
        }

        if (current instanceof JSONObject) {
            try { return ((JSONObject) current).toString(2); }
            catch (JSONException e) { return current.toString(); }
        } else if (current instanceof JSONArray) {
            try { return ((JSONArray) current).toString(2); }
            catch (JSONException e) { return current.toString(); }
        }
        return String.valueOf(current);
    }
}
