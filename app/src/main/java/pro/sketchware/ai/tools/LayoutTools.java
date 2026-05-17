package pro.sketchware.ai.tools;

import android.content.Context;
import android.content.Intent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.util.SketchwareFileDecryptor;
import pro.sketchware.util.SketchwareFileEncryptor;

/**
 * LayoutTools — AI tools for reading and editing Sketchware view layouts.
 *
 * FIXED: All read/write operations now use SketchwareFileDecryptor / SketchwareFileEncryptor
 * to handle the encrypted AES @section view file format, then flush jC's in-memory cache
 * and broadcast ACTION_LAYOUT_CHANGED so DesignActivity reloads live.
 *
 * The view file uses Sketchware's @section flat ViewBean format:
 *   @main.xml
 *   {flat ViewBean JSON}
 *   {flat ViewBean JSON}
 *   @main.xml_fab
 *   {FAB ViewBean JSON}
 *
 * Tools provided:
 *   get_layout     — read current flat ViewBeans for an activity
 *   edit_layout    — add / remove / set-property on flat ViewBeans
 */
public final class LayoutTools {

    public static final String ACTION_LAYOUT_CHANGED  = "pro.sketchware.ai.ACTION_LAYOUT_CHANGED";
    public static final String EXTRA_SC_ID            = "sc_id";
    public static final String EXTRA_ACTIVITY_NAME    = "activity_name";

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping().serializeNulls().create();

    private LayoutTools() {}

    // ── Encrypted file helpers ─────────────────────────────────────────────────

    /** Reads and decrypts the view file; returns the raw @section text. */
    private static String readView(String scId) {
        String content = SketchwareFileDecryptor.decryptFile(scId, "view");
        return content != null ? content : "";
    }

    /** Encrypts and saves the @section text; also flushes jC in-memory cache. */
    private static boolean writeView(String scId, String content) {
        boolean saved = SketchwareFileEncryptor.encryptAndSaveFile(scId, "view", content);
        if (saved) {
            try { a.a.a.jC.b(); a.a.a.jC.a(scId, true); } catch (Throwable ignored) {}
        }
        return saved;
    }

    // ── @section format helpers ────────────────────────────────────────────────

    private static Map<String, List<String>> parseSections(String raw) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return sections;
        String cur = null;
        for (String line : raw.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("@")) {
                cur = t.substring(1).trim();
                sections.putIfAbsent(cur, new ArrayList<>());
            } else if (cur != null && !t.isEmpty()) {
                sections.get(cur).add(t);
            }
        }
        return sections;
    }

    private static String serialise(Map<String, List<String>> sections) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : sections.entrySet()) {
            sb.append('@').append(e.getKey()).append('\n');
            for (String line : e.getValue()) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String toXmlKey(String name) {
        if (name == null) return null;
        name = name.trim();
        return name.endsWith(".xml") ? name : name + ".xml";
    }

    private static void notifyLayoutChanged(Context ctx, String scId, String activityName) {
        notifyLayoutChangedWithXml(ctx, scId, activityName, null);
    }

    /**
     * Broadcasts ACTION_LAYOUT_CHANGED with all keys the receiver expects.
     * When layoutXml is non-null, DesignActivity will run the full ViewBeanParser path.
     * When null, it falls back to a simple canvas refresh (jC already updated by caller).
     */
    private static void notifyLayoutChangedWithXml(Context ctx, String scId,
                                                   String activityName, String layoutXml) {
        try {
            String xmlKey = activityName.endsWith(".xml") ? activityName : activityName + ".xml";
            Intent intent = new Intent(ACTION_LAYOUT_CHANGED);
            intent.putExtra(EXTRA_SC_ID, scId);
            intent.putExtra(EXTRA_ACTIVITY_NAME, activityName);
            intent.putExtra("activity_xml", xmlKey);   // key the receiver actually reads
            if (layoutXml != null && !layoutXml.isEmpty()) {
                intent.putExtra("layout_xml", layoutXml);
            }
            ctx.sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    private static ToolResult ok(String s)  { return ToolResult.success(null, s); }
    private static ToolResult err(String s) { return ToolResult.failure(null, s); }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString().trim() : null;
    }

    // ── Tool: get_layout ──────────────────────────────────────────────────────

    public static class GetLayoutTool implements AgentTool {

        @Override public String getName() { return "get_layout"; }

        @Override
        public String getDescription() {
            return "Gets the ViewBean list of an activity in a Sketchware Pro project. "
                    + "Reads the encrypted view file and returns the flat list of ViewBeans "
                    + "for the specified activity. Each bean shows id, type, parent, and properties.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scP = new JsonObject(); scP.addProperty("type","string");
            scP.addProperty("description","The project SC ID"); props.add("sc_id", scP);
            JsonObject nP = new JsonObject(); nP.addProperty("type","string");
            nP.addProperty("description","Activity name (e.g., \"main\")"); props.add("activity_name", nP);
            JsonArray req = new JsonArray(); req.add("sc_id"); req.add("activity_name");
            schema.add("properties", props); schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            if (!args.has("sc_id") || args.get("sc_id").isJsonNull())
                return err("Missing required parameter: sc_id");
            if (!args.has("activity_name") || args.get("activity_name").isJsonNull())
                return err("Missing required parameter: activity_name");

            String scId    = args.get("sc_id").getAsString();
            String xmlKey  = toXmlKey(args.get("activity_name").getAsString());
            if (!ctx.isProjectAllowed(scId))
                return err("Access denied: project " + scId + " is not in the current workspace");

            String raw = readView(scId);
            Map<String, List<String>> sections = parseSections(raw);
            List<String> lines = sections.get(xmlKey);

            if (lines == null)
                return err("Layout not found for activity: " + xmlKey
                        + "\nAvailable sections: " + sections.keySet());

            StringBuilder sb = new StringBuilder("=== Layout: " + xmlKey + " ===\n");
            sb.append("ViewBeans: ").append(lines.size()).append("\n\n");
            for (String line : lines) {
                try {
                    JsonObject b = JsonParser.parseString(line).getAsJsonObject();
                    sb.append("  id=").append(b.has("id") ? b.get("id").getAsString() : "?")
                      .append(" type=").append(b.has("type") ? b.get("type").getAsInt() : -1)
                      .append(" parent=").append(b.has("parent") ? b.get("parent").getAsString() : "?")
                      .append(" index=").append(b.has("index") ? b.get("index").getAsInt() : -1);
                    if (b.has("text") && b.get("text").isJsonObject()) {
                        String txt = b.getAsJsonObject("text").has("text")
                                ? b.getAsJsonObject("text").get("text").getAsString() : "";
                        if (!txt.isEmpty()) sb.append(" text=\"").append(txt).append("\"");
                    }
                    sb.append("\n");
                } catch (Exception e) {
                    sb.append("  [parse error]: ").append(line, 0, Math.min(80, line.length())).append("\n");
                }
            }
            sb.append("\n[Raw JSON array]\n[").append(String.join(",", lines)).append("]");
            return ok(sb.toString());
        }
    }

    // ── Tool: edit_layout ─────────────────────────────────────────────────────

    public static class EditLayoutTool implements AgentTool {

        @Override public String getName() { return "edit_layout"; }

        @Override
        public String getDescription() {
            return "Edits the view layout of an activity by performing operations on the flat "
                    + "ViewBean list. Changes appear IMMEDIATELY in the Design Editor. "
                    + "Operations: "
                    + "'add_view' — adds a new ViewBean (requires 'view' JSON object with id/type/parent/parentType/index); "
                    + "'remove_view' — removes by 'view_id'; "
                    + "'set_property' — updates a property by 'view_id', 'property' (supports dot-paths like 'layout.width', 'text.text'), 'value'.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            JsonObject scP = new JsonObject(); scP.addProperty("type","string");
            scP.addProperty("description","The project SC ID"); props.add("sc_id", scP);
            JsonObject nP = new JsonObject(); nP.addProperty("type","string");
            nP.addProperty("description","Activity name"); props.add("activity_name", nP);
            JsonObject opsP = new JsonObject(); opsP.addProperty("type","array");
            opsP.addProperty("description",
                    "Array of operations. Each has a 'type' field: "
                    + "'add_view' needs a 'view' object (ViewBean JSON); "
                    + "'remove_view' needs 'view_id'; "
                    + "'set_property' needs 'view_id', 'property' (dot-path ok), 'value'.");
            JsonObject itemSch = new JsonObject(); itemSch.addProperty("type","object");
            opsP.add("items", itemSch); props.add("view_operations", opsP);
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("activity_name"); req.add("view_operations");
            JsonObject schema = new JsonObject(); schema.addProperty("type","object");
            schema.add("properties", props); schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            if (!args.has("sc_id") || args.get("sc_id").isJsonNull())
                return err("Missing required parameter: sc_id");
            if (!args.has("activity_name") || args.get("activity_name").isJsonNull())
                return err("Missing required parameter: activity_name");
            if (!args.has("view_operations") || !args.get("view_operations").isJsonArray())
                return err("Missing required parameter: view_operations (must be an array)");

            String scId   = args.get("sc_id").getAsString();
            String xmlKey = toXmlKey(args.get("activity_name").getAsString());
            JsonArray ops = args.getAsJsonArray("view_operations");

            if (!ctx.isProjectAllowed(scId))
                return err("Access denied: project " + scId + " is not in the current workspace");

            // Read current encrypted view file
            String raw = readView(scId);
            Map<String, List<String>> sections = parseSections(raw);

            List<String> lines = sections.get(xmlKey);
            if (lines == null) {
                lines = new ArrayList<>();
                sections.put(xmlKey, lines);
            }
            // Ensure _fab section
            String fabKey = xmlKey + "_fab";
            sections.putIfAbsent(fabKey,
                    new ArrayList<>(Arrays.asList("{\"adSize\":\"\",\"adUnitId\":\"\",\"alpha\":1.0,\"checked\":0,\"choiceMode\":0,\"clickable\":1,\"convert\":\"\",\"customView\":\"\","+"\"dividerHeight\":1,\"enabled\":1,\"firstDayOfWeek\":1,\"id\":\"_fab\","+"\"image\":{\"resName\":\"default_image\",\"rotate\":0,\"scaleType\":\"CENTER\"},"+"\"indeterminate\":\"false\",\"index\":0,\"inject\":\"\","+"\"layout\":{\"backgroundColor\":-13730510,\"borderColor\":-3617307,"+"\"gravity\":0,\"height\":-1,\"layoutGravity\":0,"+"\"marginBottom\":0,\"marginLeft\":0,\"marginRight\":0,\"marginTop\":0,"+"\"orientation\":-1,\"paddingBottom\":0,\"paddingLeft\":0,"+"\"paddingRight\":0,\"paddingTop\":0,\"weight\":0,\"weightSum\":0,\"width\":-1},"+"\"max\":100,\"parent\":\"root\",\"parentType\":0,"+"\"preId\":\"_fab\",\"preIndex\":-1,\"preParent\":\"\",\"preParentType\":-1,"+"\"progress\":0,\"progressStyle\":\"?android:progressBarStyle\","+"\"scaleX\":1.0,\"scaleY\":1.0,\"spinnerMode\":1,"+"\"text\":{\"hint\":\"\",\"hintColor\":-10453621,\"imeOption\":0,"+"\"inputType\":1,\"line\":0,\"singleLine\":0,\"text\":\"\","+"\"textColor\":-16777216,\"textFont\":\"default_font\","+"\"textSize\":12,\"textType\":0},"+"\"translationX\":0.0,\"translationY\":0.0,\"type\":16}")));

            JsonArray results = new JsonArray();
            for (JsonElement opEl : ops) {
                if (!opEl.isJsonObject()) {
                    JsonObject r = new JsonObject();
                    r.addProperty("success", false);
                    r.addProperty("error", "Operation must be a JSON object");
                    results.add(r); continue;
                }
                JsonObject op = opEl.getAsJsonObject();
                if (!op.has("type")) {
                    JsonObject r = new JsonObject();
                    r.addProperty("success", false);
                    r.addProperty("error", "Operation missing 'type' field");
                    results.add(r); continue;
                }
                String opType = op.get("type").getAsString();
                JsonObject r;
                switch (opType) {
                    case "add_view":    r = handleAdd(lines, op);          break;
                    case "remove_view": r = handleRemove(lines, op);       break;
                    case "set_property":r = handleSetProperty(lines, op);  break;
                    default:
                        r = new JsonObject();
                        r.addProperty("success", false);
                        r.addProperty("error", "Unknown operation type: " + opType);
                }
                results.add(r);
            }

            // Save encrypted
            sections.put(xmlKey, lines);
            boolean saved = writeView(scId, serialise(sections));
            if (!saved) return err("Failed to save view file (encryption error).");

            // Broadcast live reload
            notifyLayoutChanged(ctx.getAppContext(), scId, xmlKey);

            JsonObject result = new JsonObject();
            result.addProperty("activity_name", xmlKey);
            result.add("operation_results", results);
            result.addProperty("message",
                    "Layout updated. Design Editor refreshed automatically.");
            return ok(result.toString());
        }

        /** Appends a new ViewBean to the flat list. */
        private JsonObject handleAdd(List<String> lines, JsonObject op) {
            JsonObject r = new JsonObject();
            if (!op.has("view") || !op.get("view").isJsonObject()) {
                r.addProperty("success", false);
                r.addProperty("error", "add_view requires a 'view' object (ViewBean JSON)");
                return r;
            }
            JsonObject newView = op.getAsJsonObject("view");

            // Assign index if missing
            if (!newView.has("index")) newView.addProperty("index", lines.size());

            // Validate required fields
            if (!newView.has("id") || newView.get("id").getAsString().isEmpty()) {
                r.addProperty("success", false);
                r.addProperty("error", "view must have an 'id' field");
                return r;
            }
            if (!newView.has("type")) newView.addProperty("type", 4); // default TextView

            // Check duplicate id
            String newId = newView.get("id").getAsString();
            for (String line : lines) {
                try {
                    JsonObject b = JsonParser.parseString(line).getAsJsonObject();
                    if (newId.equals(b.has("id") ? b.get("id").getAsString() : "")) {
                        r.addProperty("success", false);
                        r.addProperty("error", "View with id '" + newId + "' already exists");
                        return r;
                    }
                } catch (Exception ignored) {}
            }

            lines.add(GSON.toJson(newView));
            r.addProperty("success", true);
            r.addProperty("message", "ViewBean added: " + newId
                    + " (type=" + newView.get("type").getAsInt() + ")");
            return r;
        }

        /** Removes a ViewBean (and its children) by id from the flat list. */
        private JsonObject handleRemove(List<String> lines, JsonObject op) {
            JsonObject r = new JsonObject();
            if (!op.has("view_id")) {
                r.addProperty("success", false);
                r.addProperty("error", "remove_view requires 'view_id'");
                return r;
            }
            String viewId = op.get("view_id").getAsString();

            // Collect ids to remove (target + all descendants)
            java.util.Set<String> toRemove = new java.util.HashSet<>();
            java.util.Queue<String> queue = new java.util.LinkedList<>();
            for (String line : lines) {
                try {
                    JsonObject b = JsonParser.parseString(line).getAsJsonObject();
                    if (viewId.equals(b.has("id") ? b.get("id").getAsString() : "")) {
                        toRemove.add(viewId); queue.add(viewId); break;
                    }
                } catch (Exception ignored) {}
            }
            if (toRemove.isEmpty()) {
                r.addProperty("success", false);
                r.addProperty("error", "View '" + viewId + "' not found");
                return r;
            }
            while (!queue.isEmpty()) {
                String pid = queue.poll();
                for (String line : lines) {
                    try {
                        JsonObject b = JsonParser.parseString(line).getAsJsonObject();
                        String bid = b.has("id") ? b.get("id").getAsString() : "";
                        String bpar = b.has("parent") ? b.get("parent").getAsString() : "";
                        if (pid.equals(bpar) && !toRemove.contains(bid)) {
                            toRemove.add(bid); queue.add(bid);
                        }
                    } catch (Exception ignored) {}
                }
            }

            lines.removeIf(line -> {
                try {
                    JsonObject b = JsonParser.parseString(line).getAsJsonObject();
                    return toRemove.contains(b.has("id") ? b.get("id").getAsString() : "");
                } catch (Exception e) { return false; }
            });

            r.addProperty("success", true);
            r.addProperty("message", "Removed '" + viewId + "' and "
                    + (toRemove.size() - 1) + " descendant(s).");
            return r;
        }

        /**
         * Updates a property in a flat ViewBean.
         * Supports dot-path notation: "layout.width", "text.text", "text.textColor", etc.
         */
        private JsonObject handleSetProperty(List<String> lines, JsonObject op) {
            JsonObject r = new JsonObject();
            if (!op.has("view_id") || !op.has("property") || !op.has("value")) {
                r.addProperty("success", false);
                r.addProperty("error", "set_property requires 'view_id', 'property', and 'value'");
                return r;
            }
            String viewId   = op.get("view_id").getAsString();
            String property = op.get("property").getAsString();
            JsonElement value = op.get("value");

            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                try {
                    JsonObject b = JsonParser.parseString(lines.get(i)).getAsJsonObject();
                    if (viewId.equals(b.has("id") ? b.get("id").getAsString() : "")) {
                        // Apply property (supports dot-path)
                        if (property.contains(".")) {
                            String[] parts = property.split("\\.", 2);
                            if (!b.has(parts[0])) b.add(parts[0], new JsonObject());
                            b.getAsJsonObject(parts[0]).add(parts[1], value);
                        } else {
                            b.add(property, value);
                        }
                        lines.set(i, GSON.toJson(b));
                        found = true;
                        break;
                    }
                } catch (Exception e) {
                    r.addProperty("success", false);
                    r.addProperty("error", "Parse error on bean: " + e.getMessage());
                    return r;
                }
            }

            if (!found) {
                r.addProperty("success", false);
                r.addProperty("error", "View '" + viewId + "' not found");
                return r;
            }
            r.addProperty("success", true);
            r.addProperty("message", "Property '" + property + "' updated on '" + viewId + "'");
            return r;
        }
    }
}
