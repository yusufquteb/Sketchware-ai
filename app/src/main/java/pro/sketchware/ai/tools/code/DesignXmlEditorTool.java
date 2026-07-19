package pro.sketchware.ai.tools.code;

import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;

import android.content.Context;
import android.content.Intent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pro.sketchware.util.SketchwareFileDecryptor;
import pro.sketchware.util.SketchwareFileEncryptor;

import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.GsonUtils;
import com.besome.sketch.beans.ViewBean;

/**
 * DesignXmlEditorTool — 🎨 DesignActivity AI Layout Editor (Phase 4)
 *
 * Provides 4 tools for AI-driven UI layout editing in Sketchware Pro's DesignActivity:
 *
 *   1. describe_layout      — converts a view/*.xml entry into a human-readable description
 *   2. add_view             — adds a new view widget to an activity layout
 *   3. modify_view          — modifies properties of an existing view widget
 *   4. remove_view          — removes a view widget from a layout
 *
 * After every write operation, sends ACTION_LAYOUT_CHANGED broadcast so DesignActivity
 * live-reloads the canvas without requiring a project restart.
 *
 * Sketchware view file format  (.sketchware/data/{scId}/view):
 *   JSON array, each entry:
 *   {
 *     "id": "main.xml",                  ← activityName + ".xml"
 *     "data": [                           ← list of view objects
 *       {
 *         "id": "linear1",               ← view id
 *         "type": 0,                     ← 0=LinearLayout, 2=TextView, 3=Button, …
 *         "layout": { width, height, … },
 *         "children": [ … ]
 *       }
 *     ]
 *   }
 */
public final class DesignXmlEditorTool {

    public static final String ACTION_LAYOUT_CHANGED =
            "pro.sketchware.ai.ACTION_LAYOUT_CHANGED";
    public static final String EXTRA_SC_ID        = "sc_id";
    public static final String EXTRA_ACTIVITY_NAME = "activity_name";

    private DesignXmlEditorTool() {}

    // ── View type map ─────────────────────────────────────────────────────

    static final java.util.Map<String, Integer> VIEW_TYPES = new java.util.LinkedHashMap<>();
    static {
        VIEW_TYPES.put("LinearLayout",          0);
        VIEW_TYPES.put("HorizontalScrollView",  1);
        VIEW_TYPES.put("TextView",              2);
        VIEW_TYPES.put("Button",                3);
        VIEW_TYPES.put("EditText",              4);
        VIEW_TYPES.put("ImageView",             6);
        VIEW_TYPES.put("ImageButton",           7);
        VIEW_TYPES.put("CheckBox",              8);
        VIEW_TYPES.put("RadioButton",           9);
        VIEW_TYPES.put("RadioGroup",           10);
        VIEW_TYPES.put("Spinner",              11);
        VIEW_TYPES.put("ScrollView",           12);
        VIEW_TYPES.put("Switch",               13);
        VIEW_TYPES.put("SeekBar",              14);
        VIEW_TYPES.put("ProgressBar",          15);
        VIEW_TYPES.put("ListView",             16);
        VIEW_TYPES.put("MapView",              17);
        VIEW_TYPES.put("WebView",              18);
        VIEW_TYPES.put("CalendarView",         19);
        VIEW_TYPES.put("FloatingActionButton", 20);
        VIEW_TYPES.put("AdView",               21);
        VIEW_TYPES.put("CardView",             22);
    }

    static String typeToName(int type) {
        for (java.util.Map.Entry<String, Integer> e : VIEW_TYPES.entrySet()) {
            if (e.getValue() == type) return e.getKey();
        }
        return "View(" + type + ")";
    }

    // ── Shared helpers ────────────────────────────────────────────────────

    private static ToolResult success(String output) { return ToolResult.success(null, output); }
    private static ToolResult error(String msg)      { return ToolResult.failure(null, msg); }

    private static String requireString(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) return null;
        return args.get(key).getAsString().trim();
    }

    private static String readFile(File f) throws IOException {
        if (!f.exists() || f.length() == 0) return "";
        // Extract scId from absolute path: .../.sketchware/data/{scId}/{filename}
        String abs = f.getAbsolutePath().replace("\\", "/");
        String[] parts = abs.split("/");
        String scId = null, relPath = null;
        for (int i = 0; i < parts.length - 1; i++) {
            if ("data".equals(parts[i]) && i + 1 < parts.length) {
                scId = parts[i + 1];
                relPath = parts[parts.length - 1];
                break;
            }
        }
        if (scId != null && relPath != null) {
            String dec = SketchwareFileDecryptor.decryptFile(scId, relPath);
            if (dec != null) return dec;
        }
        // Fallback: plain text read
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            char[] buf = new char[4096]; int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private static void writeFile(File f, String content) throws IOException {
        String abs = f.getAbsolutePath().replace("\\", "/");
        String[] parts = abs.split("/");
        String scId = null, relPath = null;
        for (int i = 0; i < parts.length - 1; i++) {
            if ("data".equals(parts[i]) && i + 1 < parts.length) {
                scId = parts[i + 1];
                relPath = parts[parts.length - 1];
                break;
            }
        }
        if (scId != null && relPath != null) {
            boolean saved = SketchwareFileEncryptor.encryptAndSaveFile(scId, relPath, content);
            if (saved) {
                try { a.a.a.jC.b(); a.a.a.jC.a(scId, true); } catch (Throwable ignored) {}
                return;
            }
        }
        // Fallback: plain text write
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (Writer fw = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) { fw.write(content); }
    }

    // ── @section ↔ JSON-array bridge helpers ─────────────────────────────────

    /**
     * Converts Sketchware's @section text format to an internal JSON array.
     * The view file uses "@main.xml
{bean1}
{bean2}
@main.xml_fab
{fab}" on disk.
     * Internally, DesignXmlEditorTool works with JsonArray [{id:"main.xml",view:[...]}, ...]
     * This method converts between the two.
     */
    private static JsonArray sectionsToJsonArray(String raw) {
        if (raw == null || raw.isEmpty()) return new JsonArray();
        if (raw.trim().startsWith("[")) {
            // Already JSON array
            try { return JsonParser.parseString(raw.trim()).getAsJsonArray(); }
            catch (Exception e) { return new JsonArray(); }
        }
        if (!raw.trim().startsWith("@")) return new JsonArray();

        JsonArray result = new JsonArray();
        String curSection = null;
        JsonArray curViews = null;
        boolean isFab = false;

        for (String line : raw.split("\r?\n")) {
            String t = line.trim();
            if (t.startsWith("@")) {
                if (curSection != null && curViews != null && !isFab) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", curSection);
                    entry.add("view", curViews);
                    result.add(entry);
                }
                curSection = t.substring(1).trim();
                isFab = curSection.endsWith("_fab");
                curViews = new JsonArray();
            } else if (curSection != null && !t.isEmpty() && !isFab) {
                try { curViews.add(JsonParser.parseString(t)); }
                catch (Exception ignored) {}
            }
        }
        if (curSection != null && curViews != null && !isFab) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", curSection);
            entry.add("view", curViews);
            result.add(entry);
        }
        return result;
    }

    /** Default FAB placeholder required per SK.txt Rule 8. */
    private static final String DEFAULT_FAB =
    "{\"adSize\":\"\",\"adUnitId\":\"\",\"alpha\":1.0,\"checked\":0,\"choiceMode\":0,"
    + "\"clickable\":1,\"convert\":\"\",\"customView\":\"\",\"dividerHeight\":1,"
    + "\"enabled\":1,\"firstDayOfWeek\":1,\"id\":\"_fab\","
    + "\"image\":{\"resName\":\"default_image\",\"rotate\":0,\"scaleType\":\"CENTER\"},"
    + "\"indeterminate\":\"false\",\"index\":0,\"inject\":\"\","
    + "\"layout\":{\"backgroundColor\":-13730510,\"borderColor\":-3617307,"
    + "\"gravity\":0,\"height\":-1,\"layoutGravity\":0,"
    + "\"marginBottom\":0,\"marginLeft\":0,\"marginRight\":0,\"marginTop\":0,"
    + "\"orientation\":-1,\"paddingBottom\":0,\"paddingLeft\":0,"
    + "\"paddingRight\":0,\"paddingTop\":0,\"weight\":0,\"weightSum\":0,\"width\":-1},"
    + "\"max\":100,\"parent\":\"root\",\"parentType\":0,"
    + "\"preId\":\"_fab\",\"preIndex\":-1,\"preParent\":\"\",\"preParentType\":-1,"
    + "\"progress\":0,\"progressStyle\":\"?android:progressBarStyle\","
    + "\"scaleX\":1.0,\"scaleY\":1.0,\"spinnerMode\":1,"
    + "\"text\":{\"hint\":\"\",\"hintColor\":-10453621,\"imeOption\":0,"
    + "\"inputType\":1,\"line\":0,\"singleLine\":0,\"text\":\"\","
    + "\"textColor\":-16777216,\"textFont\":\"default_font\","
    + "\"textSize\":12,\"textType\":0},"
    + "\"translationX\":0.0,\"translationY\":0.0,\"type\":16}";


    /**
     * Converts the internal JSON array back to Sketchware's @section text format.
     * Ensures every activity has a corresponding _fab section (SK.txt Rule 8).
     */
    private static String jsonArrayToSections(JsonArray fileArray) {
    Map<String, List<String>> sectionMap = new LinkedHashMap<>();
    for (JsonElement el : fileArray) {
        if (!el.isJsonObject()) continue;
        JsonObject entry = el.getAsJsonObject();
        String id = entry.has("id") ? entry.get("id").getAsString() : "";
        JsonArray views = entry.has("view") ? entry.get("view").getAsJsonArray()
                        : entry.has("data") ? entry.get("data").getAsJsonArray()
                        : new JsonArray();
        List<String> lines = new ArrayList<>();
        for (JsonElement v : views) lines.add(v.toString());
        sectionMap.put(id, lines);
    }

    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, List<String>> entry : sectionMap.entrySet()) {
        String sid = entry.getKey();
        if (sid.endsWith("_fab")) continue; // written after its main section

        // Fixed: Use '\n' for new lines
        sb.append('@').append(sid).append('\n');
        for (String line : entry.getValue()) {
            sb.append(line).append('\n');
        }

        // _fab section (required per SK.txt Rule 8)
        String fabKey = sid + "_fab";
        sb.append('@').append(fabKey).append('\n');

        if (sectionMap.containsKey(fabKey)) {
            for (String fab : sectionMap.get(fabKey)) {
                sb.append(fab).append('\n');
            }
        } else {
            sb.append(DEFAULT_FAB).append('\n');
        }
    }
    return sb.toString();
}

    private static void notifyChange(Context ctx, String scId, String activityName) {
        // For old JSON-bean tools (add_view, modify_view, remove_view):
        // writeFile already encrypted and reloaded jC. Send broadcast via LIVE_LAYOUT_RELOAD
        // so liveLayoutReceiver can do a guaranteed refresh even if jC timing is off.
        notifyChangeWithXml(ctx, scId, activityName, null);
        // Also send on LIVE_LAYOUT_RELOAD channel for belt+suspenders refresh
        try {
            String xmlKey = activityName.endsWith(".xml") ? activityName : activityName + ".xml";
            android.content.Intent i2 =
                    new android.content.Intent("pro.sketchware.ai.ACTION_LIVE_LAYOUT_RELOAD");
            i2.putExtra("sc_id", scId);
            i2.putExtra("activity_xml", xmlKey);
            // No layout_xml → receiver will do simple refresh from already-updated jC
            ctx.sendBroadcast(i2);
        } catch (Exception ignored) {}
    }

    /**
     * Broadcasts ACTION_LAYOUT_CHANGED with optional layout_xml so DesignActivity
     * uses the proven IA path: ViewBeanParser → jC.c.put → redraw.
     */
    private static void notifyChangeWithXml(Context ctx, String scId,
                                            String activityName, String layoutXml) {
        try {
            Intent i = new Intent(ACTION_LAYOUT_CHANGED);
            i.putExtra(EXTRA_SC_ID, scId);
            i.putExtra(EXTRA_ACTIVITY_NAME, activityName);
            String xmlKey = activityName.endsWith(".xml") ? activityName : activityName + ".xml";
            i.putExtra("activity_xml", xmlKey);
            if (layoutXml != null && !layoutXml.isEmpty()) {
                i.putExtra("layout_xml", layoutXml);
            }
            ctx.sendBroadcast(i);
        } catch (Exception ignored) {}
    }

    private static JsonArray readViewArray(File viewFile) throws IOException {
        if (!viewFile.exists()) return new JsonArray();
        String raw = readFile(viewFile).trim();
        if (raw.isEmpty()) return new JsonArray();
        // Handle both @section format (real files) and JSON array (legacy/new)
        return sectionsToJsonArray(raw);
    }

    /**
     * Converts in-memory ViewBeans back to Android XML for AI editing reference.
     * This is the XML that was most recently applied to the canvas.
     */
    private static String viewBeansToXml(java.util.ArrayList<com.besome.sketch.beans.ViewBean> beans) {
        if (beans == null || beans.isEmpty()) return null;
        try {
            // Use ViewBeanParser in reverse via InvokeUtil if available, otherwise basic XML
            StringBuilder xml = new StringBuilder();
            xml.append("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
            xml.append("    android:layout_width=\"match_parent\"\n");
            xml.append("    android:layout_height=\"match_parent\"\n");
            xml.append("    android:orientation=\"vertical\">\n");
            for (com.besome.sketch.beans.ViewBean bean : beans) {
                if (bean.parent != null && !bean.parent.equals("root")) continue; // root level only
                xml.append("    <").append(getTagForType(bean.type));
                xml.append(" android:id=\"@+id/").append(bean.id).append("\"");
                xml.append(" android:layout_width=\"").append(dimenStr(bean.layout.width)).append("\"");
                xml.append(" android:layout_height=\"").append(dimenStr(bean.layout.height)).append("\"");
                if (bean.text != null && !bean.text.text.isEmpty())
                    xml.append("\n        android:text=\"").append(bean.text.text).append("\"");
                xml.append("/>");
                xml.append("\n");
            }
            xml.append("</LinearLayout>");
            return xml.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String dimenStr(int val) {
        if (val == -1) return "match_parent";
        if (val == -2) return "wrap_content";
        return val + "dp";
    }

    private static String getTagForType(int type) {
        switch (type) {
            case 0: return "LinearLayout";
            case 1: return "TextView";
            case 2: return "EditText";
            case 3: return "Button";
            case 4: return "ImageView";
            case 5: return "ImageButton";
            case 6: return "CheckBox";
            case 7: return "RadioButton";
            case 8: return "RadioGroup";
            case 9: return "ListView";
            case 10: return "Spinner";
            case 11: return "ScrollView";
            case 12: return "Switch";
            case 13: return "SeekBar";
            case 14: return "ProgressBar";
            case 15: return "WebView";
            case 16: return "FloatingActionButton";
            case 17: return "CardView";
            case 18: return "RecyclerView";
            default: return "View";
        }
    }

    private static JsonObject findEntry(JsonArray arr, String activityName) {
        String id = activityName + ".xml";
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("id") && obj.get("id").getAsString().equals(id)) return obj;
            }
        }
        return null;
    }

    private static void collectIds(JsonArray views, java.util.List<String> ids) {
        if (views == null) return;
        for (JsonElement el : views) {
            if (!el.isJsonObject()) continue;
            JsonObject v = el.getAsJsonObject();
            if (v.has("id")) ids.add(v.get("id").getAsString());
            if (v.has("children") && v.get("children").isJsonArray()) {
                collectIds(v.getAsJsonArray("children"), ids);
            }
        }
    }

    /** Recursively describes the view tree as indented text. */
    private static void describeTree(JsonArray views, String indent, StringBuilder sb) {
        if (views == null) return;
        for (JsonElement el : views) {
            if (!el.isJsonObject()) continue;
            JsonObject v = el.getAsJsonObject();
            String id      = v.has("id")   ? v.get("id").getAsString()   : "?";
            int    type    = v.has("type") ? v.get("type").getAsInt()     : -1;
            String name    = typeToName(type);
            sb.append(indent).append("• ").append(id).append(" [").append(name).append("]");

            // Show key layout properties
            if (v.has("layout") && v.get("layout").isJsonObject()) {
                JsonObject layout = v.getAsJsonObject("layout");
                if (layout.has("width"))  sb.append(" w=").append(layout.get("width"));
                if (layout.has("height")) sb.append(" h=").append(layout.get("height"));
                if (layout.has("gravity") && layout.get("gravity").getAsInt() != 0)
                    sb.append(" gravity=").append(layout.get("gravity"));
            }
            if (v.has("text"))            sb.append(" text=\"").append(v.get("text").getAsString()).append("\"");
            if (v.has("textColor"))       sb.append(" color=").append(v.get("textColor"));
            sb.append("\n");

            // Recurse into children
            if (v.has("children") && v.get("children").isJsonArray()) {
                describeTree(v.getAsJsonArray("children"), indent + "  ", sb);
            }
        }
    }

    /** Recursively finds a view by id within a tree. Returns [parent_array, index] or null. */
    private static Object[] findViewById(JsonArray arr, String viewId) {
        for (int i = 0; i < arr.size(); i++) {
            JsonElement el = arr.get(i);
            if (!el.isJsonObject()) continue;
            JsonObject v = el.getAsJsonObject();
            if (v.has("id") && v.get("id").getAsString().equals(viewId))
                return new Object[]{arr, i, v};
            if (v.has("children") && v.get("children").isJsonArray()) {
                Object[] found = findViewById(v.getAsJsonArray("children"), viewId);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Generates a simple default view object for a given widget type. */
    private static JsonObject defaultView(String viewId, int type, JsonObject extraProps) {
        JsonObject v = new JsonObject();
        v.addProperty("id",       viewId);
        v.addProperty("type",     type);
        v.addProperty("text",     "");
        v.addProperty("textColor", 0xFF212121);
        v.addProperty("textSize",  12);
        v.addProperty("padding",   8);

        JsonObject layout = new JsonObject();
        layout.addProperty("width",  -1);   // MATCH_PARENT
        layout.addProperty("height", -2);   // WRAP_CONTENT
        layout.addProperty("gravity", 0);
        layout.addProperty("layoutGravity", 0);
        layout.addProperty("marginTop",    0);
        layout.addProperty("marginBottom", 0);
        layout.addProperty("marginLeft",   0);
        layout.addProperty("marginRight",  0);
        v.add("layout", layout);
        v.add("children", new JsonArray());

        // Apply extra properties from caller
        if (extraProps != null) {
            for (String key : new ArrayList<>(extraProps.keySet())) {
                if ("layout".equals(key) && extraProps.get(key).isJsonObject()) {
                    JsonObject extraLayout = extraProps.getAsJsonObject("layout");
                    for (String lk : new ArrayList<>(extraLayout.keySet())) {
                        layout.add(lk, extraLayout.get(lk));
                    }
                } else {
                    v.add(key, extraProps.get(key));
                }
            }
        }
        return v;
    }

    // ── Tool 1: describe_layout ───────────────────────────────────────────

    // ═══════════════════════════════════════════════════════════════════════════
    // ViewBeanParser helpers — correct flat ViewBean format (matches Sketchware-IA)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parses an Android XML string into a flat ArrayList<ViewBean> using ViewBeanParser.
     * skipRoot=true: the outermost layout tag is treated as the container; its children
     * are added to the screen (same behaviour as Sketchware-IA's generateAndApplyLayoutAsync).
     */
    private static ArrayList<ViewBean> xmlToViewBeans(String xml, boolean skipRoot,
                                                       String[] errHolder) {
        try {
            ViewBeanParser parser = new ViewBeanParser(xml);
            parser.setSkipRoot(skipRoot);
            return parser.parse();
        } catch (Exception e) {
            if (errHolder != null) errHolder[0] = e.getMessage();
            return null;
        }
    }

    /**
     * Validates a ViewBean list before writing to disk.
     * Returns null if valid, or an error message describing the first problem found.
     */
    static String validateViewBeans(ArrayList<ViewBean> beans) {
        if (beans == null || beans.isEmpty()) return "View list is empty";
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        for (ViewBean b : beans) {
            if (b == null) return "Null ViewBean in list";
            if (b.id == null || b.id.trim().isEmpty()) return "ViewBean has null or empty id";
            // Valid Android ID characters: letters, digits, underscore
            if (!b.id.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
                return "Invalid view id '" + b.id + "': must start with a letter and contain only letters, digits, underscores";
            }
            if (!seenIds.add(b.id)) return "Duplicate view id: " + b.id;
            // type must be a known non-negative value (0–999)
            if (b.type < 0 || b.type > 999) return "Invalid view type " + b.type + " for id " + b.id;
        }
        return null;
    }

    /**
     * Saves a flat ViewBean list to the view file using Gson — the exact format that
     * eC (Sketchware's view data manager) reads from disk.
     * Format: [{id:"main.xml", data:[...flat ViewBeans serialized by Gson...]}]
     */
    private static void saveViewBeans(File viewFile, String activityName,
                                      ArrayList<ViewBean> beans) throws IOException {
        String validationError = validateViewBeans(beans);
        if (validationError != null) throw new IOException("XML Validator: " + validationError);
        com.google.gson.Gson gson = GsonUtils.getGson();
        JsonArray fileArray = readViewArray(viewFile); // reuse existing helper

        // Find or create the entry for this activity
        String xmlId = activityName + ".xml";
        JsonObject entry = null;
        for (int i = 0; i < fileArray.size(); i++) {
            if (!fileArray.get(i).isJsonObject()) continue;
            JsonObject obj = fileArray.get(i).getAsJsonObject();
            if (obj.has("id") && xmlId.equals(obj.get("id").getAsString())) {
                entry = obj;
                break;
            }
        }
        if (entry == null) {
            entry = new JsonObject();
            entry.addProperty("id", xmlId);
            fileArray.add(entry);
        }

        // Serialize beans using Gson (matches eC internal format exactly)
        JsonArray dataArr = JsonParser.parseString(gson.toJson(beans)).getAsJsonArray();
        entry.add("data", dataArr);
        // Write back in Sketchware's @section format (SK.txt compliant)
        writeFile(viewFile, jsonArrayToSections(fileArray));
    }

        public static class DescribeLayoutTool implements AgentTool {
        @Override public String getName() { return "describe_layout"; }

        @Override public String getDescription() {
            return "Returns a human-readable description of the view hierarchy (layout) "
                 + "of an activity screen in Sketchware Pro. Shows each widget's id, type, "
                 + "text, size, and nesting. Use this before add_view or modify_view to "
                 + "understand the current screen structure. "
                 + "WARNING: This tool ONLY works with Sketchware JSON layouts (stored in the 'view' data file). "
                 + "It does NOT work with raw XML files like res/layout/design.xml. "
                 + "For raw XML files, use read_file instead.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject sc = new JsonObject(); sc.addProperty("type","string");
            sc.addProperty("description","Project ID"); props.add("sc_id", sc);
            JsonObject act = new JsonObject(); act.addProperty("type","string");
            act.addProperty("description","Activity name without .java, e.g. 'MainActivity'");
            props.add("activity_name", act);
            schema.add("properties", props);
            JsonArray req = new JsonArray(); req.add("sc_id"); req.add("activity_name");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = requireString(args, "sc_id");
            String actName = requireString(args, "activity_name");
            if (scId == null || actName == null)
                return error("sc_id and activity_name are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            ctx.reportProgress("Reading layout from memory…", -1, true);

            // PRIMARY: Read from jC in-memory (most accurate — includes unsaved AI changes)
            String xmlName = actName.endsWith(".xml") ? actName : actName + ".xml";
            try {
                java.util.ArrayList<com.besome.sketch.beans.ViewBean> liveBeans =
                        a.a.a.jC.a(scId).d(xmlName);
                if (liveBeans != null && !liveBeans.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Layout of '").append(actName).append("' — ")
                      .append(liveBeans.size()).append(" view(s) [live from canvas]:\n\n");
                    for (com.besome.sketch.beans.ViewBean bean : liveBeans) {
                        sb.append("  id=").append(bean.id)
                          .append(" type=").append(bean.type)
                          .append(" parent=").append(bean.parent != null ? bean.parent : "root")
                          .append(" index=").append(bean.index);
                        if (bean.text != null && !bean.text.text.isEmpty())
                            sb.append(" text=\"").append(bean.text.text).append("\"");
                        sb.append("\n");
                    }
                    // Also return as compact XML for AI editing reference
                    String liveXml = viewBeansToXml(liveBeans);
                    if (liveXml != null && !liveXml.isEmpty()) {
                        sb.append("\n[Current XML — pass as current_layout when editing]\n");
                        sb.append(liveXml);
                    }
                    return success(sb.toString());
                }
            } catch (Exception ignored) {}

            // FALLBACK: Read from encrypted disk file
            File viewFile = new File(ctx.getProjectDataDir(scId), "view");
            try {
                JsonArray arr = readViewArray(viewFile);
                JsonObject entry = findEntry(arr, actName);
                if (entry == null)
                    return success("No layout found for '" + actName + "'. Screen may be empty.");

                JsonArray data = entry.has("view") ? entry.getAsJsonArray("view")
                        : entry.has("data") ? entry.getAsJsonArray("data") : new JsonArray();

                StringBuilder sb = new StringBuilder();
                sb.append("Layout of '").append(actName).append("' — ")
                  .append(data.size()).append(" root view(s) [from disk]:\n\n");
                describeTree(data, "", sb);
                sb.append("\n[Raw JSON for AI reference]\n").append(data.toString());
                return success(sb.toString());
            } catch (IOException | JsonSyntaxException e) {
                return error("Failed to read layout: " + e.getMessage());
            }
        }
    }

    // ── Tool 2: add_view ─────────────────────────────────────────────────

    public static class AddViewTool implements AgentTool {
        @Override public String getName() { return "add_view"; }

        @Override public String getDescription() {
            return "Adds a new view widget to an activity layout in Sketchware Pro. "
                 + "Supported widget_type values: LinearLayout, TextView, Button, EditText, "
                 + "ImageView, ImageButton, CheckBox, RadioButton, RadioGroup, Spinner, "
                 + "ScrollView, Switch, SeekBar, ProgressBar, ListView, CardView, "
                 + "FloatingActionButton, WebView. "
                 + "Specify parent_view_id to nest inside a container, or omit to add at root. "
                 + "After adding, DesignActivity reloads the canvas automatically.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addP(props, "sc_id",           "string",  "Project ID");
            addP(props, "activity_name",   "string",  "Activity name without .java");
            addP(props, "widget_type",     "string",  "Widget type, e.g. 'Button', 'TextView'");
            addP(props, "view_id",         "string",  "Unique id for the new view, e.g. 'myButton'");
            addP(props, "parent_view_id",  "string",  "Id of parent container to add into (optional — root if omitted)");
            addP(props, "text",            "string",  "Initial text for the widget (optional)");
            addP(props, "width",           "integer", "Layout width: -1=MATCH_PARENT, -2=WRAP_CONTENT, or dp value");
            addP(props, "height",          "integer", "Layout height: -1=MATCH_PARENT, -2=WRAP_CONTENT, or dp value");
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("activity_name"); req.add("widget_type"); req.add("view_id");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId       = requireString(args, "sc_id");
            String actName    = requireString(args, "activity_name");
            String widgetType = requireString(args, "widget_type");
            String viewId     = requireString(args, "view_id");
            if (scId == null || actName == null || widgetType == null || viewId == null)
                return error("sc_id, activity_name, widget_type and view_id are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            Integer typeCode = VIEW_TYPES.get(widgetType);
            if (typeCode == null) return error("Unknown widget_type: " + widgetType
                    + ". Supported: " + VIEW_TYPES.keySet());

            ctx.reportProgress("Adding " + widgetType + "…", -1, true);
            File viewFile = new File(ctx.getProjectDataDir(scId), "view");
            try {
                JsonArray arr   = readViewArray(viewFile);
                JsonObject entry = findEntry(arr, actName);
                if (entry == null) {
                    // Create new entry
                    entry = new JsonObject();
                    entry.addProperty("id", actName + ".xml");
                    entry.add("data", new JsonArray());
                    arr.add(entry);
                }
                JsonArray data = entry.has("data") && entry.get("data").isJsonArray()
                        ? entry.getAsJsonArray("data") : new JsonArray();
                if (!entry.has("data")) entry.add("data", data);

                // Check for duplicate id
                if (findViewById(data, viewId) != null)
                    return error("A view with id '" + viewId + "' already exists.");

                JsonObject extra = new JsonObject();
                if (args.has("text"))   extra.addProperty("text",   args.get("text").getAsString());
                if (args.has("width") || args.has("height")) {
                    JsonObject layout = new JsonObject();
                    if (args.has("width"))  layout.addProperty("width",  args.get("width").getAsInt());
                    if (args.has("height")) layout.addProperty("height", args.get("height").getAsInt());
                    extra.add("layout", layout);
                }

                JsonObject newView = defaultView(viewId, typeCode, extra);

                String parentId = requireString(args, "parent_view_id");
                if (parentId != null && !parentId.isEmpty()) {
                    Object[] found = findViewById(data, parentId);
                    if (found == null) return error("parent_view_id not found: " + parentId);
                    JsonObject parent = (JsonObject) found[2];
                    if (!parent.has("children")) parent.add("children", new JsonArray());
                    parent.getAsJsonArray("children").add(newView);
                } else {
                    data.add(newView);
                }

                // Write back
                writeFile(viewFile, jsonArrayToSections(arr));
                notifyChange(ctx.getAppContext(), scId, actName);
                return success(widgetType + " '" + viewId + "' added to '"
                        + actName + "'. DesignActivity canvas reloaded.");
            } catch (IOException | JsonSyntaxException e) {
                return error("Failed to add view: " + e.getMessage());
            }
        }
    }

    // ── Tool 3: modify_view ───────────────────────────────────────────────

    public static class ModifyViewTool implements AgentTool {
        @Override public String getName() { return "modify_view"; }

        @Override public String getDescription() {
            return "Modifies properties of an existing view widget in a Sketchware Pro layout. "
                 + "Specify the view_id and any properties to change: text, textColor, textSize, "
                 + "textStyle, padding, layout.width, layout.height, layout.gravity, "
                 + "layout.marginTop/Bottom/Left/Right, backgroundColor, visibility, etc. "
                 + "After modification, DesignActivity reloads the canvas automatically.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addP(props, "sc_id",          "string", "Project ID");
            addP(props, "activity_name",  "string", "Activity name without .java");
            addP(props, "view_id",        "string", "Id of the view to modify");
            addP(props, "text",           "string", "New text value");
            addP(props, "textColor",      "integer","Text color as ARGB integer");
            addP(props, "textSize",       "integer","Text size in sp");
            addP(props, "padding",        "integer","Padding in dp");
            addP(props, "backgroundColor","integer","Background color as ARGB integer");
            JsonObject layoutP = new JsonObject();
            layoutP.addProperty("type", "object");
            layoutP.addProperty("description", "Layout properties: width, height, gravity, layoutGravity, marginTop/Bottom/Left/Right");
            props.add("layout", layoutP);
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("activity_name"); req.add("view_id");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = requireString(args, "sc_id");
            String actName = requireString(args, "activity_name");
            String viewId  = requireString(args, "view_id");
            if (scId == null || actName == null || viewId == null)
                return error("sc_id, activity_name and view_id are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            ctx.reportProgress("Modifying view " + viewId + "…", -1, true);
            File viewFile = new File(ctx.getProjectDataDir(scId), "view");
            try {
                JsonArray arr   = readViewArray(viewFile);
                JsonObject entry = findEntry(arr, actName);
                if (entry == null) return error("Activity layout not found: " + actName);

                JsonArray data = entry.getAsJsonArray("data");
                Object[] found = findViewById(data, viewId);
                if (found == null) return error("View not found: " + viewId);

                JsonObject view = (JsonObject) found[2];

                // Apply scalar props
                String[] scalarProps = {"text","textColor","textSize","textStyle","padding",
                                        "backgroundColor","visibility","enabled","clickable"};
                for (String prop : scalarProps) {
                    if (args.has(prop)) view.add(prop, args.get(prop));
                }

                // Apply layout sub-object
                if (args.has("layout") && args.get("layout").isJsonObject()) {
                    JsonObject layoutPatch = args.getAsJsonObject("layout");
                    if (!view.has("layout")) view.add("layout", new JsonObject());
                    JsonObject layout = view.getAsJsonObject("layout");
                    for (String key : new ArrayList<>(layoutPatch.keySet())) {
                        layout.add(key, layoutPatch.get(key));
                    }
                }

                writeFile(viewFile, jsonArrayToSections(arr));
                notifyChange(ctx.getAppContext(), scId, actName);
                return success("View '" + viewId + "' modified in '" + actName
                        + "'. DesignActivity canvas reloaded.");
            } catch (IOException | JsonSyntaxException e) {
                return error("Failed to modify view: " + e.getMessage());
            }
        }
    }

    // ── Tool 4: remove_view ───────────────────────────────────────────────

    public static class RemoveViewTool implements AgentTool {
        @Override public String getName() { return "remove_view"; }

        @Override public String getDescription() {
            return "Removes a view widget and all its children from a Sketchware Pro activity layout. "
                 + "Use describe_layout first to confirm the view id. "
                 + "After removal, DesignActivity reloads the canvas automatically.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addP(props, "sc_id",         "string", "Project ID");
            addP(props, "activity_name", "string", "Activity name without .java");
            addP(props, "view_id",       "string", "Id of the view to remove");
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("activity_name"); req.add("view_id");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = requireString(args, "sc_id");
            String actName = requireString(args, "activity_name");
            String viewId  = requireString(args, "view_id");
            if (scId == null || actName == null || viewId == null)
                return error("sc_id, activity_name and view_id are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            ctx.reportProgress("Removing view " + viewId + "…", -1, true);
            File viewFile = new File(ctx.getProjectDataDir(scId), "view");
            try {
                JsonArray arr   = readViewArray(viewFile);
                JsonObject entry = findEntry(arr, actName);
                if (entry == null) return error("Activity layout not found: " + actName);

                JsonArray data = entry.getAsJsonArray("data");
                Object[] found = findViewById(data, viewId);
                if (found == null) return error("View not found: " + viewId);

                JsonArray parentArr = (JsonArray) found[0];
                int idx = (Integer) found[1];
                parentArr.remove(idx);

                writeFile(viewFile, jsonArrayToSections(arr));
                notifyChange(ctx.getAppContext(), scId, actName);
                return success("View '" + viewId + "' removed from '" + actName
                        + "'. DesignActivity canvas reloaded.");
            } catch (IOException | JsonSyntaxException e) {
                return error("Failed to remove view: " + e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tool: add_view_xml  (PREFERRED over add_view)
    // ══════════════════════════════════════════════════════════════════════════
    /**
     * Adds views to an activity by providing raw Android XML.
     * Uses ViewBeanParser — the same engine as Sketchware-IA — to guarantee
     * the result appears on the design canvas.
     *
     * Wrap all widgets in a root ViewGroup (e.g. LinearLayout). The root tag
     * acts as the container; its children become the new screen views.
     */
    public static class AddViewXmlTool implements AgentTool {
        @Override public String getName() { return "add_view_xml"; }

        @Override public String getDescription() {
            return "PREFERRED method to add views. Provide raw Android XML wrapped in a root "
                 + "ViewGroup. Uses ViewBeanParser to guarantee views appear on the canvas. "
                 + "Example: <LinearLayout android:orientation=\"vertical\"...>"
                 + "<Button android:id=\"@+id/btn1\" android:text=\"Click me\"/>"
                 + "</LinearLayout>. "
                 + "Set replace=true to replace the entire layout (default: false = append).";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "sc_id",          "string",  "Project ID (sc_id)");
            addP(p, "activity_name",  "string",  "Activity name without .java, e.g. MainActivity");
            addP(p, "xml",            "string",  "Android XML snippet with a root ViewGroup");
            addP(p, "replace",        "boolean", "true = replace entire layout, false = append (default)");
            s.add("properties", p);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("activity_name"); r.add("xml");
            s.add("required", r);
            return s;
        }

        @Override public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = requireString(args, "sc_id");
            String actName = requireString(args, "activity_name");
            String xml     = requireString(args, "xml");
            boolean replace = args.has("replace") && args.get("replace").getAsBoolean();

            if (scId == null || actName == null || xml == null)
                return error("sc_id, activity_name and xml are required");
            if (!ctx.isProjectAllowed(scId))
                return error("Access denied: project " + scId);

            String[] errHolder = {null};
            ArrayList<ViewBean> newBeans = xmlToViewBeans(xml, true, errHolder);
            if (newBeans == null || newBeans.isEmpty())
                return error("XML parse failed: "
                        + (errHolder[0] != null ? errHolder[0] : "no views found — check XML syntax"));

            try {
                File viewFile = new File(ctx.getProjectDataDir(scId), "view");
                ArrayList<ViewBean> finalBeans;

                if (replace) {
                    finalBeans = newBeans;
                } else {
                    // Merge: read existing, then append new beans (deduplicate by id)
                    ArrayList<ViewBean> existing = new ArrayList<>();
                    try {
                        JsonArray fileArr = readViewArray(viewFile);
                        for (int i = 0; i < fileArr.size(); i++) {
                            if (!fileArr.get(i).isJsonObject()) continue;
                            JsonObject entry = fileArr.get(i).getAsJsonObject();
                            if (entry.has("id") && (actName + ".xml")
                                    .equals(entry.get("id").getAsString())
                                    && entry.has("data")) {
                                for (JsonElement el : entry.getAsJsonArray("data")) {
                                    if (el.isJsonObject()) {
                                        try {
                                            ViewBean b = GsonUtils.getGson()
                                                    .fromJson(el, ViewBean.class);
                                            if (b != null) existing.add(b);
                                        } catch (Exception ignored) {}
                                    }
                                }
                                break;
                            }
                        }
                    } catch (Exception ignored) {}

                    // Merge: existing first, then new (new overrides same id)
                    java.util.Map<String, ViewBean> map = new java.util.LinkedHashMap<>();
                    for (ViewBean b : existing) if (b.id != null) map.put(b.id, b);
                    for (ViewBean b : newBeans)  if (b.id != null) map.put(b.id, b);
                    finalBeans = new ArrayList<>(map.values());
                }

                saveViewBeans(viewFile, actName, finalBeans);
                // Pass xml so DesignActivity runs full ViewBeanParser path:
                // ViewBeanParser → InjectRootLayoutManager → HistoryViewBean → jC.c.put → redraw
                notifyChangeWithXml(ctx.getAppContext(), scId, actName, xml);

                return success((replace ? "Replaced" : "Added") + " " + newBeans.size()
                        + " view(s) to '" + actName + "'. "
                        + "Total views on canvas: " + finalBeans.size() + ". "
                        + "Design canvas reloaded automatically.");
            } catch (Exception e) {
                return error("Failed to save views: " + e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tool: generate_layout
    // ══════════════════════════════════════════════════════════════════════════
    /**
     * Generates a complete Android layout from a natural-language description
     * and applies it directly to the design canvas via ViewBeanParser.
     */
    public static class GenerateLayoutTool implements AgentTool {
        @Override public String getName() { return "generate_layout"; }

        @Override public String getDescription() {
            return "Generates a COMPLETE Android layout from description — REPLACES the full screen.\n"
                 + "Use for: creating new screens, full redesigns, complete layout replacement.\n"
                 + "For PARTIAL edits (add/change specific views): use add_view_xml with replace=false instead.\n"
                 + "For EDITING existing layout: call describe_layout first to get current XML,\n"
                 + "then call this with current_layout=<xml> so AI preserves existing structure.\n"
                 + "Optionally refines output with Morph if enabled in AI Settings.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "sc_id",           "string",  "Project ID");
            addP(p, "activity_name",   "string",  "Activity name without .java");
            addP(p, "description",     "string",
                    "What to create or change. For new layouts: full description. "
                    + "For edits: describe only the change (e.g. 'change button color to blue').");
            addP(p, "current_layout",  "string",
                    "OPTIONAL. Current layout XML from describe_layout. "
                    + "Include this when EDITING so AI preserves existing views. "
                    + "Omit when creating a new layout from scratch.");
            addP(p, "replace",         "boolean", "Replace entire layout (default true)");
            s.add("properties", p);
            JsonArray r = new JsonArray();
            r.add("sc_id"); r.add("activity_name"); r.add("description");
            s.add("required", r);
            return s;
        }

        @Override public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = requireString(args, "sc_id");
            String actName = requireString(args, "activity_name");
            String desc    = requireString(args, "description");
            boolean replace = !args.has("replace") || args.get("replace").getAsBoolean();

            if (scId == null || actName == null || desc == null)
                return error("sc_id, activity_name and description are required");
            if (!ctx.isProjectAllowed(scId))
                return error("Access denied: project " + scId);

            // Ask for clarification if description is too vague
            if (desc == null || desc.trim().length() < 10) {
                return error("Please describe the layout in more detail. " +
                        "Example: 'A calculator with a display and a 4x4 button grid " +
                        "for digits 0-9, operators, clear and equals.' " +
                        "What elements should appear on the screen?");
            }

            // Read optional current_layout (for edit mode — preserves existing views)
            String currentLayout = (args.has("current_layout") && !args.get("current_layout").isJsonNull())
                    ? args.get("current_layout").getAsString().trim() : null;

            // XML ID Lock Layer: if current_layout is not provided, check for existing views.
            // If the activity already has views, refuse to replace without acknowledgement to
            // prevent AI from accidentally destroying IDs referenced by existing logic blocks.
            if (currentLayout == null || currentLayout.isEmpty()) {
                try {
                    File viewFile = new File(ctx.getProjectDataDir(scId), "view");
                    if (viewFile.exists()) {
                        JsonArray existingArr = readViewArray(viewFile);
                        JsonObject existingEntry = findEntry(existingArr, actName);
                        if (existingEntry != null && existingEntry.has("data")) {
                            JsonArray existingData = existingEntry.getAsJsonArray("data");
                            if (existingData.size() > 1) { // >1 because root LinearLayout always exists
                                java.util.List<String> existingIds = new java.util.ArrayList<>();
                                collectIds(existingData, existingIds);
                                return error("XML ID Lock: activity '" + actName + "' already has "
                                        + existingData.size() + " view(s) with IDs: "
                                        + String.join(", ", existingIds) + ". "
                                        + "These IDs may be referenced in logic blocks. "
                                        + "Call describe_layout first to get current XML, "
                                        + "then pass it as current_layout so the AI preserves existing IDs.");
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // GeradorDeLayoutPro: edit mode if currentLayout provided, generate mode if null
            String xml;
            try {
                pro.sketchware.ai.legacy.GeradorDeLayoutPro gerador =
                        (currentLayout != null && !currentLayout.isEmpty())
                        ? new pro.sketchware.ai.legacy.GeradorDeLayoutPro(ctx.getAppContext(), desc, currentLayout)
                        : new pro.sketchware.ai.legacy.GeradorDeLayoutPro(ctx.getAppContext(), desc);
                xml = gerador.generateLayout();
            } catch (Exception aiEx) {
                android.util.Log.w("GenerateLayoutTool",
                        "AI call failed, using template fallback: " + aiEx.getMessage());
                xml = buildXmlFromDescription(desc != null ? desc : "");
            }

            if (xml == null || xml.trim().isEmpty())
                return error("Layout generation returned empty result. Try a more specific description.");

            String[] errHolder = {null};
            ArrayList<ViewBean> beans = xmlToViewBeans(xml, true, errHolder);
            if (beans == null || beans.isEmpty())
                return error("Layout generation failed: "
                        + (errHolder[0] != null ? errHolder[0] : "empty result"));

            try {
                File viewFile = new File(ctx.getProjectDataDir(scId), "view");
                saveViewBeans(viewFile, actName, beans);
                // Pass xml so DesignActivity uses ViewBeanParser → jC.c.put → redraw (IA path)
                notifyChangeWithXml(ctx.getAppContext(), scId, actName, xml);

                return success("Layout generated and applied to '" + actName + "'. "
                        + beans.size() + " view(s) created from description: \"" + desc + "\"\n"
                        + "Design canvas reloaded live.");
            } catch (Exception e) {
                return error("Failed to apply layout: " + e.getMessage());
            }
        }

        private String buildXmlFromDescription(String d) {
            String dl = d.toLowerCase();
            if (dl.contains("login") || dl.contains("sign in"))        return loginXml();
            if (dl.contains("register") || dl.contains("sign up"))     return registerXml();
            if (dl.contains("profile"))                                 return profileXml();
            if (dl.contains("settings") || dl.contains("preference"))  return settingsXml();
            if (dl.contains("dashboard") || dl.contains("home"))       return dashboardXml();
            if (dl.contains("chat") || dl.contains("message"))         return chatXml();
            if (dl.contains("list"))                                    return listXml();
            return genericXml(d);
        }

        private String wrap(String content) {
            return "<LinearLayout android:layout_width=\"match_parent\" "
                 + "android:layout_height=\"match_parent\" "
                 + "android:orientation=\"vertical\" "
                 + "android:padding=\"16dp\">"
                 + content
                 + "</LinearLayout>";
        }

        private String tv(String id, String text, String size, String style) {
            return "<TextView android:id=\"@+id/" + id + "\" "
                 + "android:layout_width=\"match_parent\" "
                 + "android:layout_height=\"wrap_content\" "
                 + "android:text=\"" + text + "\" "
                 + "android:textSize=\"" + size + "sp\" "
                 + (style.isEmpty() ? "" : "android:textStyle=\"" + style + "\" ")
                 + "android:layout_marginBottom=\"8dp\"/>";
        }

        private String et(String id, String hint, String inputType) {
            return "<EditText android:id=\"@+id/" + id + "\" "
                 + "android:layout_width=\"match_parent\" "
                 + "android:layout_height=\"wrap_content\" "
                 + "android:hint=\"" + hint + "\" "
                 + "android:inputType=\"" + inputType + "\" "
                 + "android:padding=\"12dp\" "
                 + "android:layout_marginBottom=\"12dp\"/>";
        }

        private String btn(String id, String text) {
            return "<Button android:id=\"@+id/" + id + "\" "
                 + "android:layout_width=\"match_parent\" "
                 + "android:layout_height=\"wrap_content\" "
                 + "android:text=\"" + text + "\" "
                 + "android:layout_marginBottom=\"8dp\"/>";
        }

        private String loginXml() {
            return wrap(
                tv("tv_title", "Welcome Back", "24", "bold")
                + tv("tv_subtitle", "Sign in to continue", "14", "")
                + et("edt_email",    "Email address", "textEmailAddress")
                + et("edt_password", "Password",      "textPassword")
                + btn("btn_login",    "Login")
                + tv("tv_register", "Don't have an account? Register", "13", "")
            );
        }

        private String registerXml() {
            return wrap(
                tv("tv_title", "Create Account", "24", "bold")
                + et("edt_name",     "Full Name",       "textPersonName")
                + et("edt_email",    "Email",           "textEmailAddress")
                + et("edt_password", "Password",        "textPassword")
                + et("edt_confirm",  "Confirm Password","textPassword")
                + btn("btn_register", "Register")
            );
        }

        private String profileXml() {
            return wrap(
                "<ImageView android:id=\"@+id/img_avatar\" "
                + "android:layout_width=\"80dp\" android:layout_height=\"80dp\" "
                + "android:layout_gravity=\"center_horizontal\" "
                + "android:layout_marginBottom=\"12dp\"/>"
                + tv("tv_name",     "User Name", "20", "bold")
                + tv("tv_bio",      "Bio goes here", "14", "")
                + btn("btn_follow", "Follow")
                + btn("btn_message","Message")
            );
        }

        private String settingsXml() {
            return wrap(
                tv("tv_account",          "Account",                      "12", "bold")
                + tv("tv_edit_profile",   "Edit Profile",                  "15", "")
                + tv("tv_notifications",  "Notifications",                 "15", "")
                + tv("tv_general",        "General",                       "12", "bold")
                + tv("tv_language",       "Language",                      "15", "")
                + tv("tv_about",          "About",                         "15", "")
                + btn("btn_logout",       "Logout")
            );
        }

        private String dashboardXml() {
            return wrap(
                tv("tv_greeting", "Hello!", "22", "bold")
                + tv("tv_stat1",  "1,234 Users",   "18", "bold")
                + tv("tv_stat2",  "$5.6K Revenue",  "18", "bold")
                + tv("tv_recent", "Recent Activity","16", "bold")
                + "<ListView android:id=\"@+id/list_activity\" "
                + "android:layout_width=\"match_parent\" "
                + "android:layout_height=\"0dp\" android:layout_weight=\"1\"/>"
            );
        }

        private String chatXml() {
            return "<LinearLayout android:layout_width=\"match_parent\" "
                + "android:layout_height=\"match_parent\" "
                + "android:orientation=\"vertical\">"
                + "<ListView android:id=\"@+id/list_messages\" "
                + "android:layout_width=\"match_parent\" "
                + "android:layout_height=\"0dp\" android:layout_weight=\"1\"/>"
                + "<LinearLayout android:layout_width=\"match_parent\" "
                + "android:layout_height=\"wrap_content\" "
                + "android:orientation=\"horizontal\" android:padding=\"8dp\">"
                + "<EditText android:id=\"@+id/edt_message\" "
                + "android:layout_width=\"0dp\" android:layout_height=\"wrap_content\" "
                + "android:layout_weight=\"1\" android:hint=\"Type a message...\"/>"
                + "<Button android:id=\"@+id/btn_send\" "
                + "android:layout_width=\"wrap_content\" "
                + "android:layout_height=\"wrap_content\" android:text=\"Send\"/>"
                + "</LinearLayout>"
                + "</LinearLayout>";
        }

        private String listXml() {
            return wrap(
                "<EditText android:id=\"@+id/edt_search\" "
                + "android:layout_width=\"match_parent\" "
                + "android:layout_height=\"wrap_content\" "
                + "android:hint=\"Search...\" android:layout_marginBottom=\"8dp\"/>"
                + "<ListView android:id=\"@+id/listview1\" "
                + "android:layout_width=\"match_parent\" "
                + "android:layout_height=\"0dp\" android:layout_weight=\"1\"/>"
                + btn("btn_add", "Add New Item")
            );
        }

        private String genericXml(String description) {
            String dl = description.toLowerCase();
            StringBuilder sb = new StringBuilder(
                "<LinearLayout android:layout_width=\"match_parent\" "
                + "android:layout_height=\"match_parent\" "
                + "android:orientation=\"vertical\" android:padding=\"16dp\">"
            );
            sb.append("<TextView android:id=\"@+id/tv_title\" "
                + "android:layout_width=\"match_parent\" "
                + "android:layout_height=\"wrap_content\" "
                + "android:text=\"" + description.substring(0, Math.min(40, description.length())) + "\" "
                + "android:textSize=\"20sp\" android:textStyle=\"bold\" "
                + "android:layout_marginBottom=\"16dp\"/>");
            if (dl.contains("button") || dl.contains("btn"))
                sb.append("<Button android:id=\"@+id/btn_action\" "
                    + "android:layout_width=\"match_parent\" "
                    + "android:layout_height=\"wrap_content\" "
                    + "android:text=\"Action\" android:layout_marginBottom=\"12dp\"/>");
            if (dl.contains("input") || dl.contains("text field") || dl.contains("edit"))
                sb.append("<EditText android:id=\"@+id/edt_input\" "
                    + "android:layout_width=\"match_parent\" "
                    + "android:layout_height=\"wrap_content\" "
                    + "android:hint=\"Enter text\" android:layout_marginBottom=\"12dp\"/>");
            if (dl.contains("image"))
                sb.append("<ImageView android:id=\"@+id/img_main\" "
                    + "android:layout_width=\"match_parent\" "
                    + "android:layout_height=\"200dp\" "
                    + "android:scaleType=\"centerCrop\" android:layout_marginBottom=\"12dp\"/>");
            if (dl.contains("list"))
                sb.append("<ListView android:id=\"@+id/listview1\" "
                    + "android:layout_width=\"match_parent\" "
                    + "android:layout_height=\"0dp\" android:layout_weight=\"1\"/>");
            sb.append("</LinearLayout>");
            return sb.toString();
        }
    }


    private static void addP(JsonObject props, String key, String type, String desc) {
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        p.addProperty("description", desc);
        props.add(key, p);
    }

    // ── Phase 3: batch_patch_views ────────────────────────────────────────────

    /**
     * Applies surgical property patches to multiple views in a single read-write cycle.
     * Much more efficient than calling modify_view N times for the same layout.
     *
     * Input:  sc_id, activity_name, patches=[{view_id, props:{key:value,...}}, ...]
     * Output: summary of patched views
     */
    public static class BatchPatchViewsTool implements AgentTool {
        @Override public String getName() { return "batch_patch_views"; }

        @Override public String getDescription() {
            return "Applies property patches to multiple views in one operation. "
                 + "Faster than calling modify_view repeatedly. "
                 + "Each patch entry is {view_id, props:{key:value,...}}. "
                 + "Supported props: text, textColor, textSize, textStyle, padding, "
                 + "backgroundColor, visibility, enabled, clickable, layout:{width,height,gravity,"
                 + "layoutGravity,marginTop,marginBottom,marginLeft,marginRight}. "
                 + "Only the specified keys are updated; other properties are preserved. "
                 + "After patching, DesignActivity canvas reloads automatically.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addP(props, "sc_id",         "string", "Project ID");
            addP(props, "activity_name", "string", "Activity name without .java");
            JsonObject patches = new JsonObject();
            patches.addProperty("type", "array");
            patches.addProperty("description",
                "Array of patch ops: [{view_id:\"id\", props:{key:value,...}}, ...]");
            JsonObject item = new JsonObject();
            item.addProperty("type", "object");
            patches.add("items", item);
            props.add("patches", patches);
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("activity_name"); req.add("patches");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = requireString(args, "sc_id");
            String actName = requireString(args, "activity_name");
            if (scId == null || actName == null)
                return error("sc_id and activity_name are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);
            if (!args.has("patches") || !args.get("patches").isJsonArray())
                return error("patches must be a JSON array");

            JsonArray patches = args.getAsJsonArray("patches");
            if (patches.size() == 0) return error("patches array is empty");

            ctx.reportProgress("Batch-patching " + patches.size() + " view(s) in " + actName + "…", -1, true);
            File viewFile = new File(ctx.getProjectDataDir(scId), "view");
            try {
                JsonArray arr    = readViewArray(viewFile);
                JsonObject entry = findEntry(arr, actName);
                if (entry == null) return error("Activity layout not found: " + actName);

                JsonArray data = entry.getAsJsonArray("data");
                List<String> applied  = new ArrayList<>();
                List<String> missing  = new ArrayList<>();

                for (JsonElement patchEl : patches) {
                    if (!patchEl.isJsonObject()) continue;
                    JsonObject patch  = patchEl.getAsJsonObject();
                    String viewId     = patch.has("view_id") ? patch.get("view_id").getAsString() : null;
                    JsonObject pProps = patch.has("props") && patch.get("props").isJsonObject()
                                       ? patch.getAsJsonObject("props") : null;
                    if (viewId == null || pProps == null) continue;

                    Object[] found = findViewById(data, viewId);
                    if (found == null) { missing.add(viewId); continue; }

                    JsonObject view = (JsonObject) found[2];
                    String[] scalars = {"text","textColor","textSize","textStyle","padding",
                                        "backgroundColor","visibility","enabled","clickable"};
                    for (String k : scalars) {
                        if (pProps.has(k)) view.add(k, pProps.get(k));
                    }
                    if (pProps.has("layout") && pProps.get("layout").isJsonObject()) {
                        JsonObject lp = pProps.getAsJsonObject("layout");
                        if (!view.has("layout")) view.add("layout", new JsonObject());
                        JsonObject layout = view.getAsJsonObject("layout");
                        for (String lk : new ArrayList<>(lp.keySet())) {
                            layout.add(lk, lp.get(lk));
                        }
                    }
                    applied.add(viewId);
                }

                if (applied.isEmpty()) return error("No views were patched. IDs not found: " + missing);

                writeFile(viewFile, jsonArrayToSections(arr));
                notifyChange(ctx.getAppContext(), scId, actName);

                StringBuilder sb = new StringBuilder();
                sb.append("Patched ").append(applied.size()).append(" view(s): ").append(applied);
                if (!missing.isEmpty()) sb.append("\nNot found: ").append(missing);
                return success(sb.toString());
            } catch (IOException | JsonSyntaxException e) {
                return error("Patch failed: " + e.getMessage());
            }
        }
    }

    // ── Phase 3: replace_subtree ──────────────────────────────────────────────

    /**
     * Replaces the children of a container view with a new XML subtree.
     * Preserves the container itself (its ID, layout props, type) and only swaps its children.
     * Safer than generate_layout for editing sections of an existing layout.
     *
     * Input:  sc_id, activity_name, container_id, xml (children XML fragment)
     * Output: summary of replaced subtree
     */
    public static class ReplaceSubtreeTool implements AgentTool {
        @Override public String getName() { return "replace_subtree"; }

        @Override public String getDescription() {
            return "Replaces the children of a specific container (by id) with a new XML subtree. "
                 + "The container itself (LinearLayout, CardView, etc.) is preserved with its "
                 + "existing id and properties — only its children are swapped. "
                 + "xml parameter: provide the children as an XML fragment (without a wrapping root). "
                 + "Use this for targeted section rewrites instead of regenerating the whole layout. "
                 + "After replacing, DesignActivity canvas reloads automatically.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addP(props, "sc_id",          "string", "Project ID");
            addP(props, "activity_name",  "string", "Activity name without .java");
            addP(props, "container_id",   "string", "ID of the container whose children will be replaced");
            addP(props, "xml",            "string",
                 "XML fragment for the new children (wrap in a temporary LinearLayout root). "
               + "Example: <LinearLayout xmlns:android=...><TextView .../><Button .../></LinearLayout>");
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("activity_name"); req.add("container_id"); req.add("xml");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId       = requireString(args, "sc_id");
            String actName    = requireString(args, "activity_name");
            String containerId = requireString(args, "container_id");
            String xml        = requireString(args, "xml");
            if (scId == null || actName == null || containerId == null || xml == null)
                return error("sc_id, activity_name, container_id and xml are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            ctx.reportProgress("Replacing subtree of '" + containerId + "' in " + actName + "…", -1, true);
            File viewFile = new File(ctx.getProjectDataDir(scId), "view");
            try {
                JsonArray arr    = readViewArray(viewFile);
                JsonObject entry = findEntry(arr, actName);
                if (entry == null) return error("Activity layout not found: " + actName);

                JsonArray data = entry.getAsJsonArray("data");
                Object[] found = findViewById(data, containerId);
                if (found == null) return error("Container view not found: " + containerId);

                JsonObject container = (JsonObject) found[2];

                // Parse the XML fragment — treat the outermost tag as a wrapper, use its children
                String[] errHolder = {null};
                ArrayList<ViewBean> newBeans = xmlToViewBeans(xml, true, errHolder);
                if (newBeans == null)
                    return error("XML parse failed: " + (errHolder[0] != null ? errHolder[0] : "unknown"));

                // Convert parsed ViewBeans back to JsonObject children
                // We use the Gson-based flat list but need hierarchical children here.
                // Strategy: serialise flat beans back to JSON array via Gson and re-attach as children.
                com.google.gson.Gson gson = GsonUtils.getGson();
                JsonArray newChildren = new JsonArray();
                for (ViewBean b : newBeans) {
                    newChildren.add(gson.toJsonTree(b));
                }
                container.add("children", newChildren);

                writeFile(viewFile, jsonArrayToSections(arr));
                notifyChange(ctx.getAppContext(), scId, actName);
                return success("Replaced children of '" + containerId + "' with "
                        + newBeans.size() + " new view(s) in '" + actName + "'.");
            } catch (IOException | JsonSyntaxException e) {
                return error("Replace subtree failed: " + e.getMessage());
            }
        }
    }
}
