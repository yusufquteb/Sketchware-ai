package pro.sketchware.ai.tools.project;

import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.ai.models.ToolResult;

import static pro.sketchware.util.SketchwareFileDecryptor.decryptFile;

/**
 * index_project — Lightweight Project Indexer (Phase 3)
 *
 * Builds a compact symbol index of a Sketchware project so the AI can
 * understand its structure without reading every raw data file.
 *
 * Output sections (all optional via parameters):
 *   activities    — list of activities and their types
 *   views         — per-activity view IDs with widget types
 *   variables     — per-activity variables (name + type)
 *   events        — per-activity event handlers (target + event name)
 *   components    — per-activity components (id + component type)
 *   moreblocks    — custom functions defined in each activity
 */
public final class ProjectIndexerTool implements AgentTool {

    @Override public String getName() { return "index_project"; }

    @Override
    public String getDescription() {
        return "Builds a compact symbol index of a Sketchware project. "
             + "Returns activities, view IDs, variables, events, components, and custom functions "
             + "in a structured format — much faster than reading every data file separately. "
             + "Use this first when asked to audit, modify, or understand a project. "
             + "Parameters: sc_id (required), sections (optional array: "
             + "\"activities\",\"views\",\"variables\",\"events\",\"components\",\"moreblocks\"). "
             + "Omit sections to get all.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject scId = new JsonObject();
        scId.addProperty("type", "string");
        scId.addProperty("description", "Project SC ID");
        props.add("sc_id", scId);

        JsonObject sections = new JsonObject();
        sections.addProperty("type", "array");
        sections.addProperty("description",
                "Subset of index sections to include. Omit for all sections.");
        JsonObject items = new JsonObject();
        items.addProperty("type", "string");
        sections.add("items", items);
        props.add("sections", sections);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("sc_id");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject args, ToolContext ctx) {
        String scId = args.has("sc_id") ? args.get("sc_id").getAsString() : null;
        if (scId == null || scId.isEmpty())
            return error("sc_id is required");
        if (!ctx.isProjectAllowed(scId))
            return error("Access denied: project " + scId);

        List<String> want = new ArrayList<>();
        if (args.has("sections") && args.get("sections").isJsonArray()) {
            for (JsonElement el : args.getAsJsonArray("sections")) {
                want.add(el.getAsString().toLowerCase());
            }
        }
        boolean all = want.isEmpty();

        ctx.reportProgress("Indexing project " + scId + "…", -1, true);

        StringBuilder sb = new StringBuilder();
        sb.append("# Project Index: ").append(scId).append("\n\n");

        // ── Activities ────────────────────────────────────────────────────────
        List<Activity> activities = parseActivities(scId);
        if (all || want.contains("activities")) {
            sb.append("## Activities (").append(activities.size()).append(")\n");
            for (Activity a : activities) {
                sb.append("  • ").append(a.javaName)
                  .append(" [").append(a.type).append("]")
                  .append(a.hasToolbar ? " +toolbar" : "")
                  .append(a.hasDrawer  ? " +drawer"  : "")
                  .append("\n");
            }
            sb.append("\n");
        }

        // ── Views ─────────────────────────────────────────────────────────────
        if (all || want.contains("views")) {
            sb.append("## Views\n");
            Map<String, List<String>> views = parseViews(scId);
            if (views.isEmpty()) {
                sb.append("  (none)\n");
            } else {
                for (Map.Entry<String, List<String>> e : views.entrySet()) {
                    sb.append("  ").append(e.getKey()).append(":\n");
                    for (String v : e.getValue()) sb.append("    - ").append(v).append("\n");
                }
            }
            sb.append("\n");
        }

        // ── Variables ─────────────────────────────────────────────────────────
        if (all || want.contains("variables")) {
            sb.append("## Variables\n");
            Map<String, List<String>> vars = parseNameTypeSections(scId, "variable", "name", "type");
            if (vars.isEmpty()) {
                sb.append("  (none)\n");
            } else {
                for (Map.Entry<String, List<String>> e : vars.entrySet()) {
                    sb.append("  ").append(e.getKey()).append(":\n");
                    for (String v : e.getValue()) sb.append("    - ").append(v).append("\n");
                }
            }
            sb.append("\n");
        }

        // ── Events ────────────────────────────────────────────────────────────
        if (all || want.contains("events")) {
            sb.append("## Events\n");
            Map<String, List<String>> events = parseEvents(scId);
            if (events.isEmpty()) {
                sb.append("  (none)\n");
            } else {
                for (Map.Entry<String, List<String>> e : events.entrySet()) {
                    sb.append("  ").append(e.getKey()).append(":\n");
                    for (String v : e.getValue()) sb.append("    - ").append(v).append("\n");
                }
            }
            sb.append("\n");
        }

        // ── Components ────────────────────────────────────────────────────────
        if (all || want.contains("components")) {
            sb.append("## Components\n");
            Map<String, List<String>> comps = parseNameTypeSections(scId, "component", "id", "componentId");
            if (comps.isEmpty()) {
                sb.append("  (none)\n");
            } else {
                for (Map.Entry<String, List<String>> e : comps.entrySet()) {
                    sb.append("  ").append(e.getKey()).append(":\n");
                    for (String v : e.getValue()) sb.append("    - ").append(v).append("\n");
                }
            }
            sb.append("\n");
        }

        // ── More Blocks (custom functions) ────────────────────────────────────
        if (all || want.contains("moreblocks")) {
            sb.append("## Custom Functions (moreblocks)\n");
            Map<String, List<String>> mb = parseMoreblocks(scId);
            if (mb.isEmpty()) {
                sb.append("  (none)\n");
            } else {
                for (Map.Entry<String, List<String>> e : mb.entrySet()) {
                    sb.append("  ").append(e.getKey()).append(":\n");
                    for (String v : e.getValue()) sb.append("    - ").append(v).append("\n");
                }
            }
            sb.append("\n");
        }

        return success(sb.toString().trim());
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private static class Activity {
        String javaName;
        String type;
        boolean hasToolbar;
        boolean hasDrawer;
    }

    private List<Activity> parseActivities(String scId) {
        List<Activity> result = new ArrayList<>();
        JsonArray arr = readSection(scId, "file");
        if (arr == null) return result;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            Activity a = new Activity();
            a.javaName   = str(obj, "java_name", "?");
            a.type        = str(obj, "type", "activity");
            String options = str(obj, "options", "");
            a.hasToolbar  = options.contains("T");
            a.hasDrawer   = options.contains("D");
            result.add(a);
        }
        return result;
    }

    private Map<String, List<String>> parseViews(String scId) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        JsonArray arr = readSection(scId, "view");
        if (arr == null) return result;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            String actId = str(obj, "id", null);
            if (actId == null || !obj.has("data")) continue;
            String actName = actId.replace(".xml", "");
            List<String> views = new ArrayList<>();
            collectViewLabels(obj.getAsJsonArray("data"), views, 0);
            if (!views.isEmpty()) result.put(actName, views);
        }
        return result;
    }

    private void collectViewLabels(JsonArray data, List<String> out, int depth) {
        if (data == null || depth > 10) return;
        for (JsonElement el : data) {
            if (!el.isJsonObject()) continue;
            JsonObject v = el.getAsJsonObject();
            String id   = str(v, "id", null);
            int    type = v.has("type") ? v.get("type").getAsInt() : -1;
            if (id != null) out.add(id + " [" + viewTypeName(type) + "]");
            if (v.has("children") && v.get("children").isJsonArray()) {
                collectViewLabels(v.getAsJsonArray("children"), out, depth + 1);
            }
        }
    }

    private Map<String, List<String>> parseEvents(String scId) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        JsonArray arr = readSection(scId, "event");
        if (arr == null) return result;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            String actName = str(obj, "java_name", null);
            if (actName == null) continue;
            String eventName = str(obj, "event_name", "?");
            String targetId  = str(obj, "target_id", "");
            String label = targetId.isEmpty() ? eventName : targetId + "." + eventName;
            result.computeIfAbsent(actName, k -> new ArrayList<>()).add(label);
        }
        return result;
    }

    private Map<String, List<String>> parseMoreblocks(String scId) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        JsonArray arr = readSection(scId, "moreblock");
        if (arr == null) return result;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            String actName = str(obj, "java_name", null);
            String name    = str(obj, "name", str(obj, "spec", null));
            if (actName == null || name == null) continue;
            result.computeIfAbsent(actName, k -> new ArrayList<>()).add(name);
        }
        return result;
    }

    private Map<String, List<String>> parseNameTypeSections(
            String scId, String sectionFile, String nameKey, String typeKey) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        JsonArray arr = readSection(scId, sectionFile);
        if (arr == null) return result;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            String actName = str(obj, "java_name", null);
            String name    = str(obj, nameKey, null);
            String type    = str(obj, typeKey, "");
            if (actName == null || name == null) continue;
            String label = type.isEmpty() ? name : name + " : " + type;
            result.computeIfAbsent(actName, k -> new ArrayList<>()).add(label);
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JsonArray readSection(String scId, String fileName) {
        try {
            String raw = decryptFile(scId, fileName);
            if (raw == null || raw.trim().isEmpty()) return null;
            raw = raw.trim();
            if (raw.startsWith("[")) {
                return JsonParser.parseString(raw).getAsJsonArray();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String str(JsonObject obj, String key, String def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
        try { return obj.get(key).getAsString(); }
        catch (Exception e) { return def; }
    }

    private static String viewTypeName(int type) {
        switch (type) {
            case 0:  return "LinearLayout";
            case 1:  return "HorizontalScrollView";
            case 2:  return "TextView";
            case 3:  return "Button";
            case 4:  return "EditText";
            case 6:  return "ImageView";
            case 7:  return "ImageButton";
            case 8:  return "CheckBox";
            case 9:  return "RadioButton";
            case 10: return "RadioGroup";
            case 11: return "Spinner";
            case 12: return "ScrollView";
            case 13: return "Switch";
            case 14: return "SeekBar";
            case 15: return "CalendarView";
            case 16: return "Fab";
            case 17: return "Divider";
            case 18: return "TimePicker";
            case 19: return "DatePicker";
            case 20: return "ProgressBar";
            case 24: return "ListView";
            case 25: return "RecyclerView";
            case 26: return "ViewPager";
            case 27: return "WebView";
            case 28: return "VideoView";
            case 29: return "CardView";
            case 30: return "Toolbar";
            case 31: return "SearchView";
            case 32: return "TabLayout";
            case 33: return "CollapsingToolbarLayout";
            case 34: return "AppBarLayout";
            case 35: return "CoordinatorLayout";
            case 36: return "DrawerLayout";
            case 37: return "NavigationView";
            case 38: return "NestedScrollView";
            default: return "View#" + type;
        }
    }

    private static ToolResult success(String output) {
        return new ToolResult(null, true, output, null);
    }

    private static ToolResult error(String message) {
        return new ToolResult(null, false, null, message);
    }
}
