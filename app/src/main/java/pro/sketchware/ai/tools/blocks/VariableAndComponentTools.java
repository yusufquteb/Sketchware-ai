package pro.sketchware.ai.tools.blocks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;
import pro.sketchware.util.SketchwareFileDecryptor;

/**
 * VariableAndComponentTools — 6 AI agent tools for editing Sketchware
 * project variables and components without opening the GUI.
 *
 * File formats (both AES-encrypted, key/IV = "sketchwaresecure"):
 *
 *   var file (name "var"):
 *     @ActivityName.java_var
 *     1:count
 *     2:userName
 *     0:isVisible
 *
 *   components file (name "components"):
 *     @ActivityName.java_components
 *     {"componentId":"myTimer","type":5,"param1":"1000","param2":"","param3":""}
 *
 * Variable type codes:  0=boolean  1=int  2=String  3=Map<String,Object>
 * List type codes:      0=boolean  1=int  2=String  3=Map<String,Object>
 *
 * Component type codes (ComponentBean constants):
 *   1=Intent   2=SharedPreferences   5=Timer   7=AlertDialog   8=MediaPlayer
 *   17=RequestNetwork   18=TextToSpeech   19=SpeechToText   21=LocationManager
 *   23=ProgressDialog   etc.
 */
public final class VariableAndComponentTools {

    private VariableAndComponentTools() {}

    // ── Shared helpers ────────────────────────────────────────────────────

    private static final String VAR_FILE  = "var";
    private static final String COMP_FILE = "components";

    private static ToolResult success(String msg) { return ToolResult.success(null, msg); }
    private static ToolResult error(String msg)   { return ToolResult.failure(null, msg); }

    private static String req(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) return null;
        return args.get(key).getAsString().trim();
    }

    private static void flushCache(String scId) {
        try { a.a.a.jC.a(scId, true); } catch (Throwable ignored) {}
    }

    // ── @Section\n{content} file I/O ─────────────────────────────────────

    /**
     * Reads a Sketchware "@Section\n" file and returns a map from section name → list of lines.
     * For var/list files the lines are "type:name". For components they are JSON.
     */
    private static Map<String, List<String>> readAtFile(String scId, String fileName) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            String raw = SketchwareFileDecryptor.decryptFile(scId, fileName);
            if (raw == null || raw.trim().isEmpty()) return result;
            String currentSection = null;
            for (String line : raw.split("\r?\n")) {
                if (line.startsWith("@")) {
                    currentSection = line.substring(1).trim();
                    result.put(currentSection, new ArrayList<>());
                } else if (currentSection != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) result.get(currentSection).add(trimmed);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("VarCompTools", "readAtFile(" + scId + ", " + fileName + ") failed", e);
        }
        return result;
    }

    /**
     * Serialises the section map back to "@Section\n{lines}\n" format and
     * AES-encrypts it onto disk.
     */
    private static boolean writeAtFile(String scId, String fileName,
                                        Map<String, List<String>> sections) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
            sb.append("@").append(entry.getKey()).append("\n");
            for (String line : entry.getValue()) sb.append(line).append("\n");
        }
        String content = sb.toString().trim();
        try {
            byte[] key = "sketchwaresecure".getBytes(StandardCharsets.UTF_8);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(key, "AES"),
                    new javax.crypto.spec.IvParameterSpec(key));
            byte[] encrypted = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));

            // Resolve path: .sketchware/data/{scId}/{fileName}
            File dataDir = new File(android.os.Environment.getExternalStorageDirectory(),
                    ".sketchware/data/" + scId);
            File target = new File(dataDir, fileName);
            if (!dataDir.exists()) dataDir.mkdirs();
            try (RandomAccessFile raf = new RandomAccessFile(target, "rw")) {
                raf.setLength(0);
                raf.write(encrypted);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static JsonObject baseSchema(String extra) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        addProp(props, "sc_id",         "string", "Sketchware project ID");
        addProp(props, "activity_name", "string", "Activity name without .java");
        if (extra != null) addProp(props, extra, "string", extra);
        schema.add("properties", props);
        JsonArray req = new JsonArray();
        req.add("sc_id"); req.add("activity_name");
        if (extra != null) req.add(extra);
        schema.add("required", req);
        return schema;
    }

    private static void addProp(JsonObject props, String key, String type, String desc) {
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        p.addProperty("description", desc);
        props.add(key, p);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Tool 1: get_variables
    // ══════════════════════════════════════════════════════════════════════

    public static class GetVariablesTool implements AgentTool {
        @Override public String getName() { return "get_variables"; }

        @Override public String getDescription() {
            return "Lists all variables defined in a Sketchware activity. "
                 + "Returns name, type (0=boolean 1=int 2=String 3=Map), and file (var or list). "
                 + "Use this before add_block to know which variable names are available.";
        }

        @Override public JsonObject getParametersSchema() { return baseSchema(null); }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = req(args, "sc_id");
            String actName = req(args, "activity_name");
            if (scId == null || actName == null) return error("sc_id and activity_name are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            JsonArray vars = new JsonArray();
            readVarsFromFile(scId, actName, VAR_FILE,  "variable", vars);
            readVarsFromFile(scId, actName, "list",    "list",     vars);

            JsonObject out = new JsonObject();
            out.addProperty("activity", actName);
            out.addProperty("count", vars.size());
            out.add("variables", vars);
            return success(out.toString());
        }

        private void readVarsFromFile(String scId, String actName,
                                       String fileName, String kind, JsonArray out) {
            Map<String, List<String>> sections = readAtFile(scId, fileName);
            String key = actName + ".java_var";
            if (fileName.equals("list")) key = actName + ".java_list";
            List<String> lines = sections.get(key);
            if (lines == null) return;
            for (String line : lines) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                JsonObject v = new JsonObject();
                v.addProperty("name", line.substring(colon + 1).trim());
                v.addProperty("type_code", line.substring(0, colon).trim());
                v.addProperty("kind", kind);
                out.add(v);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Tool 2: add_variable
    // ══════════════════════════════════════════════════════════════════════

    public static class AddVariableTool implements AgentTool {
        @Override public String getName() { return "add_variable"; }

        @Override public String getDescription() {
            return "Adds a new variable to a Sketchware activity. "
                 + "type_code: 0=boolean, 1=int, 2=String, 3=Map<String,Object>. "
                 + "is_list: true to add as a List instead of a single variable. "
                 + "After adding, reference the variable by name in add_block parameters. "
                 + "Example: add_variable(sc_id='601', activity='Main', name='count', type_code=1)";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = baseSchema(null);
            JsonObject props = schema.getAsJsonObject("properties");
            addProp(props, "name",      "string",  "Variable name (valid Java identifier)");
            addProp(props, "type_code", "integer", "0=boolean, 1=int, 2=String, 3=Map");
            addProp(props, "is_list",   "boolean", "true to create a List<type> instead of a plain var");
            JsonArray req = schema.getAsJsonArray("required");
            req.add("name"); req.add("type_code");
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = req(args, "sc_id");
            String actName = req(args, "activity_name");
            String name    = req(args, "name");
            if (scId == null || actName == null || name == null)
                return error("sc_id, activity_name, and name are required");
            if (!args.has("type_code")) return error("type_code is required (0=boolean,1=int,2=String,3=Map)");
            if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*"))
                return error("name must be a valid Java identifier: " + name);
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            int typeCode = args.get("type_code").getAsInt();
            boolean isList = args.has("is_list") && args.get("is_list").getAsBoolean();

            String fileName  = isList ? "list" : VAR_FILE;
            String sectionKey = actName + ".java_" + (isList ? "list" : "var");

            Map<String, List<String>> sections = readAtFile(scId, fileName);
            List<String> lines = sections.computeIfAbsent(sectionKey, k -> new ArrayList<>());

            if (lines.stream().anyMatch(l -> l.endsWith(":" + name)))
                return error("Variable '" + name + "' already exists in activity " + actName);

            lines.add(entry);
            if (!writeAtFile(scId, fileName, sections))
                return error("Failed to write " + fileName + " file.");

            flushCache(scId);
            return success("Variable '" + name + "' (type " + typeCode + ", " + (isList ? "list" : "var")
                    + ") added to activity '" + actName + "' in project " + scId);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Tool 3: delete_variable
    // ══════════════════════════════════════════════════════════════════════

    public static class DeleteVariableTool implements AgentTool {
        @Override public String getName() { return "delete_variable"; }

        @Override public String getDescription() {
            return "Removes a variable from a Sketchware activity. "
                 + "Set is_list=true to remove from the list file instead of the variable file.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = baseSchema(null);
            JsonObject props = schema.getAsJsonObject("properties");
            addProp(props, "name",    "string",  "Variable name to delete");
            addProp(props, "is_list", "boolean", "true if it is a list variable");
            JsonArray req = schema.getAsJsonArray("required");
            req.add("name");
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = req(args, "sc_id");
            String actName = req(args, "activity_name");
            String name    = req(args, "name");
            if (scId == null || actName == null || name == null)
                return error("sc_id, activity_name, and name are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            boolean isList = args.has("is_list") && args.get("is_list").getAsBoolean();
            String fileName   = isList ? "list" : VAR_FILE;
            String sectionKey = actName + ".java_" + (isList ? "list" : "var");

            Map<String, List<String>> sections = readAtFile(scId, fileName);
            List<String> lines = sections.get(sectionKey);
            if (lines == null) return error("No variables found for activity: " + actName);

            boolean removed = lines.removeIf(l -> l.endsWith(":" + name));
            if (!removed) return error("Variable '" + name + "' not found in activity: " + actName);

            if (!writeAtFile(scId, fileName, sections)) return error("Failed to save changes.");
            flushCache(scId);
            return success("Variable '" + name + "' removed from '" + actName + "' in project " + scId);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Tool 4: get_components
    // ══════════════════════════════════════════════════════════════════════

    public static class GetComponentsTool implements AgentTool {
        @Override public String getName() { return "get_components"; }

        @Override public String getDescription() {
            return "Lists all components (Intent, Timer, SharedPreferences, etc.) "
                 + "defined in a Sketchware activity. "
                 + "Returns componentId, type, and params for each component.";
        }

        @Override public JsonObject getParametersSchema() { return baseSchema(null); }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = req(args, "sc_id");
            String actName = req(args, "activity_name");
            if (scId == null || actName == null) return error("sc_id and activity_name are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            String sectionKey = actName + ".java_components";
            Map<String, List<String>> sections = readAtFile(scId, COMP_FILE);
            List<String> lines = sections.getOrDefault(sectionKey, new ArrayList<>());

            JsonArray comps = new JsonArray();
            for (String line : lines) {
                try {
                    JsonObject comp = JsonParser.parseString(line).getAsJsonObject();
                    // Annotate with human-readable type name
                    if (comp.has("type")) {
                        comp.addProperty("type_name", componentTypeName(comp.get("type").getAsInt()));
                    }
                    comps.add(comp);
                } catch (Exception ignored) {}
            }

            JsonObject out = new JsonObject();
            out.addProperty("activity", actName);
            out.addProperty("count", comps.size());
            out.add("components", comps);
            return success(out.toString());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Tool 5: add_component
    // ══════════════════════════════════════════════════════════════════════

    public static class AddComponentTool implements AgentTool {
        @Override public String getName() { return "add_component"; }

        @Override public String getDescription() {
            return "Adds a component to a Sketchware activity. "
                 + "component_id: unique identifier (valid Java identifier). "
                 + "type: 1=Intent, 2=SharedPreferences, 5=Timer, 7=AlertDialog, 8=MediaPlayer, "
                 + "17=RequestNetwork, 18=TextToSpeech, 19=SpeechToText, 21=LocationManager, "
                 + "23=ProgressDialog, 24=DatePickerDialog, 25=TimePickerDialog, 26=Notification. "
                 + "param1/param2/param3: component-specific configuration (often empty). "
                 + "For Timer: param1 = interval ms (e.g. '1000'). "
                 + "For SharedPreferences: param1 = file name (e.g. 'my_prefs'). "
                 + "For Intent: leave params empty (target set in block logic).";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = baseSchema(null);
            JsonObject props = schema.getAsJsonObject("properties");
            addProp(props, "component_id", "string",  "Unique component ID (valid Java identifier)");
            addProp(props, "type",         "integer", "Component type (see description)");
            addProp(props, "param1",       "string",  "First parameter (type-specific, often empty)");
            addProp(props, "param2",       "string",  "Second parameter (often empty)");
            addProp(props, "param3",       "string",  "Third parameter (often empty)");
            JsonArray req = schema.getAsJsonArray("required");
            req.add("component_id"); req.add("type");
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = req(args, "sc_id");
            String actName = req(args, "activity_name");
            String compId  = req(args, "component_id");
            if (scId == null || actName == null || compId == null)
                return error("sc_id, activity_name, and component_id are required");
            if (!args.has("type")) return error("type is required");
            if (!compId.matches("[a-zA-Z_][a-zA-Z0-9_]*"))
                return error("component_id must be a valid Java identifier: " + compId);
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            int type   = args.get("type").getAsInt();
            String p1  = args.has("param1") ? args.get("param1").getAsString() : "";
            String p2  = args.has("param2") ? args.get("param2").getAsString() : "";
            String p3  = args.has("param3") ? args.get("param3").getAsString() : "";

            String sectionKey = actName + ".java_components";
            Map<String, List<String>> sections = readAtFile(scId, COMP_FILE);
            List<String> lines = sections.computeIfAbsent(sectionKey, k -> new ArrayList<>());

            // Check for duplicate componentId
            for (String line : lines) {
                try {
                    JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                    if (compId.equals(obj.has("componentId") ? obj.get("componentId").getAsString() : ""))
                        return error("Component '" + compId + "' already exists in activity: " + actName);
                } catch (Exception ignored) {}
            }

            JsonObject comp = new JsonObject();
            comp.addProperty("componentId", compId);
            comp.addProperty("type",        type);
            comp.addProperty("param1",      p1);
            comp.addProperty("param2",      p2);
            comp.addProperty("param3",      p3);
            lines.add(comp.toString());

            if (!writeAtFile(scId, COMP_FILE, sections)) return error("Failed to save component file.");
            flushCache(scId);
            return success("Component '" + compId + "' (type " + type + " = " + componentTypeName(type)
                    + ") added to activity '" + actName + "' in project " + scId + ".");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Tool 6: delete_component
    // ══════════════════════════════════════════════════════════════════════

    public static class DeleteComponentTool implements AgentTool {
        @Override public String getName() { return "delete_component"; }

        @Override public String getDescription() {
            return "Removes a component from a Sketchware activity by its component ID.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = baseSchema(null);
            JsonObject props = schema.getAsJsonObject("properties");
            addProp(props, "component_id", "string", "ID of the component to delete");
            JsonArray req = schema.getAsJsonArray("required");
            req.add("component_id");
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = req(args, "sc_id");
            String actName = req(args, "activity_name");
            String compId  = req(args, "component_id");
            if (scId == null || actName == null || compId == null)
                return error("sc_id, activity_name, and component_id are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            String sectionKey = actName + ".java_components";
            Map<String, List<String>> sections = readAtFile(scId, COMP_FILE);
            List<String> lines = sections.get(sectionKey);
            if (lines == null) return error("No components found for activity: " + actName);

            boolean removed = lines.removeIf(line -> {
                try {
                    JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                    return compId.equals(obj.has("componentId") ? obj.get("componentId").getAsString() : "");
                } catch (Exception e) { return false; }
            });

            if (!removed) return error("Component '" + compId + "' not found in activity: " + actName);
            if (!writeAtFile(scId, COMP_FILE, sections)) return error("Failed to save changes.");
            flushCache(scId);
            return success("Component '" + compId + "' removed from '" + actName + "' in project " + scId);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private static String componentTypeName(int type) {
        switch (type) {
            case 1:  return "Intent";
            case 2:  return "SharedPreferences";
            case 3:  return "Calendar";
            case 4:  return "Vibrator";
            case 5:  return "Timer";
            case 6:  return "Firebase DB";
            case 7:  return "AlertDialog";
            case 8:  return "MediaPlayer";
            case 9:  return "SoundPool";
            case 10: return "ObjectAnimator";
            case 11: return "Gyroscope";
            case 12: return "Firebase Auth";
            case 13: return "InterstitialAd";
            case 14: return "Firebase Storage";
            case 15: return "Camera";
            case 16: return "FilePicker";
            case 17: return "RequestNetwork";
            case 18: return "TextToSpeech";
            case 19: return "SpeechToText";
            case 20: return "BluetoothConnect";
            case 21: return "LocationManager";
            case 22: return "RewardedVideoAd";
            case 23: return "ProgressDialog";
            case 24: return "DatePickerDialog";
            case 25: return "TimePickerDialog";
            case 26: return "Notification";
            case 27: return "FragmentAdapter";
            case 28: return "Firebase Auth Phone";
            case 30: return "Firebase Cloud Message";
            case 31: return "Firebase Auth Google";
            default: return "Unknown(" + type + ")";
        }
    }
}
