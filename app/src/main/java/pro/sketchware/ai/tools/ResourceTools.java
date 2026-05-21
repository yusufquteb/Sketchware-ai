package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.activities.projecttools.ProjectToolPaths;
import pro.sketchware.ai.models.ToolResult;

/**
 * Contains tools for managing string and color resources in Sketchware Pro projects.
 *
 * <p>Storage strategy (in priority order):
 * <ol>
 *   <li>Sketchware project-data file: {@code .sketchware/data/{sc_id}/resource} (JSON array)</li>
 *   <li>Editable Android XML resource files via {@link ProjectToolPaths}:
 *       {@code .sketchware/data/{sc_id}/files/resource/values/strings.xml} and
 *       {@code .sketchware/data/{sc_id}/files/resource/values/colors.xml}</li>
 * </ol>
 *
 * <p><b>Why Raw File Access?</b><br>
 * The Sketchware {@code resource} data file is a JSON array, but editable XML resource files
 * (colors.xml, strings.xml) are standard Android XML. Earlier high-level tools assumed JSON
 * everywhere and threw {@code MalformedJsonException} when encountering XML comments or raw text.
 * This implementation separates reading from parsing:
 * <ul>
 *   <li>Step 1 – Read as raw String (no format assumption)</li>
 *   <li>Step 2 – Detect format (JSON vs XML) and parse accordingly</li>
 *   <li>Step 3 – Fallback to raw text return on parse failure</li>
 * </ul>
 */
public final class ResourceTools {

    private ResourceTools() {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static ToolResult success(String output) {
        return new ToolResult(null, true, output, null);
    }

    private static ToolResult error(String message) {
        return new ToolResult(null, false, null, message);
    }

    /**
     * Reads a file as raw UTF-8 text without any parsing.
     * This is the low-level "Raw File API" that never throws on content format issues.
     */
    static String readRawFile(File file) throws IOException {
        if (!file.exists()) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
        }
        return sb.toString();
    }

    /**
     * Writes raw text to a file (UTF-8), creating parent directories as needed.
     */
    static void writeRawFile(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    /**
     * Reads the Sketchware {@code resource} data file.
     *
     * <p>Decoupled reading from parsing:
     * <ol>
     *   <li>Read file as raw String</li>
     *   <li>Try to parse as JSON array</li>
     *   <li>On failure, return empty array (do not crash)</li>
     * </ol>
     */
    private static JsonArray readResourceArray(File resourceFile) throws IOException {
        if (!resourceFile.exists()) {
            return new JsonArray();
        }
        // Step 1: Read raw – no format assumption
        String content = readRawFile(resourceFile);
        if (content.trim().isEmpty()) {
            return new JsonArray();
        }
        // Step 2: Strip BOM if present (handles UTF-8 BOM encoding issues)
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        // Step 3: Try JSON parse, fallback gracefully
        try {
            JsonElement element = JsonParser.parseString(content);
            return element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
        } catch (JsonSyntaxException e) {
            // Content is not JSON – might be XML or legacy format; return empty and let caller decide
            return new JsonArray();
        }
    }

    /**
     * Resolves the editable XML resource file for a given type via ProjectToolPaths.
     * Uses the Low-level File API path: reads as raw text, parses with XmlPullParser.
     *
     * @param scId project SC ID
     * @param xmlFileName e.g. "colors.xml" or "strings.xml"
     */
    private static File getEditableXmlResourceFile(ToolContext context, String scId, String xmlFileName) {
        // ProjectToolPaths.getProjectEditableResDir → .sketchware/data/{scId}/files/resource
        File resDir = ProjectToolPaths.getProjectEditableResDir(scId);
        return new File(resDir, "values" + File.separator + xmlFileName);
    }

    /**
     * Parses an Android XML resource file (colors.xml or strings.xml) into a JsonArray.
     * Uses XmlPullParser – handles comments, BOM, and non-standard formatting correctly.
     *
     * @param xmlFile the XML file to parse
     * @param tagName "color" or "string"
     * @return JsonArray of {name, value} objects
     */
    private static JsonArray parseXmlResourceFile(File xmlFile, String tagName) throws IOException {
        JsonArray result = new JsonArray();
        if (!xmlFile.exists()) return result;

        // Raw read first (handles BOM)
        String raw = readRawFile(xmlFile);
        if (raw.trim().isEmpty()) return result;

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(raw));

            int eventType = parser.getEventType();
            String currentName = null;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && tagName.equals(parser.getName())) {
                    currentName = parser.getAttributeValue(null, "name");
                } else if (eventType == XmlPullParser.TEXT && currentName != null) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("resType", tagName);
                    entry.addProperty("resName", currentName);
                    entry.addProperty("resValue", parser.getText().trim());
                    result.add(entry);
                    currentName = null;
                } else if (eventType == XmlPullParser.END_TAG) {
                    currentName = null;
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException e) {
            // XML parse failed – return what we have so far
        }
        return result;
    }

    /**
     * Writes or updates a single resource entry in an Android XML file.
     * If the file does not exist, creates a minimal valid XML structure.
     * Uses raw text read/write – no JSON intermediate layer.
     */
    private static void upsertXmlResource(File xmlFile, String tagName, String resName, String resValue)
            throws IOException {
        String raw = readRawFile(xmlFile);
        if (raw.trim().isEmpty()) {
            // Create new XML file
            raw = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n";
        }

        // Check if entry already exists
        String searchPattern = "name=\"" + resName + "\"";
        if (raw.contains(searchPattern)) {
            // Update existing: replace the value between tags
            // Pattern: <tagName name="resName">VALUE</tagName>
            String regex = "(<" + tagName + "[^>]*name=\"" + resName + "\"[^>]*>)([^<]*)(</" + tagName + ">)";
            raw = raw.replaceAll(regex, "$1" + resValue.replace("$", "\\$") + "$3");
        } else {
            // Insert before </resources>
            String newEntry = "    <" + tagName + " name=\"" + resName + "\">" + resValue + "</" + tagName + ">\n";
            raw = raw.replace("</resources>", newEntry + "</resources>");
        }
        writeRawFile(xmlFile, raw);
    }

    /**
     * Reads all resources from both the JSON data file and the editable XML files,
     * merging them into a single JsonArray. XML takes precedence for color/string types.
     */
    private static JsonArray readAllResources(ToolContext context, String scId) throws IOException {
        File resourceFile = new File(context.getProjectDataDir(scId), "resource");
        JsonArray jsonResources = readResourceArray(resourceFile);

        // Also read from editable XML files via ProjectToolPaths
        File colorsXml = getEditableXmlResourceFile(context, scId, "colors.xml");
        File stringsXml = getEditableXmlResourceFile(context, scId, "strings.xml");

        JsonArray xmlColors = parseXmlResourceFile(colorsXml, "color");
        JsonArray xmlStrings = parseXmlResourceFile(stringsXml, "string");

        // Merge: start with JSON data, add XML entries not already present (deduplicate by name)
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (JsonElement el : jsonResources) {
            if (el.isJsonObject()) {
                JsonElement name = el.getAsJsonObject().get("name");
                if (name != null) seen.add(name.getAsString());
            }
        }
        for (JsonElement el : xmlColors) {
            if (el.isJsonObject()) {
                JsonElement name = el.getAsJsonObject().get("name");
                if (name == null || seen.add(name.getAsString())) {
                    jsonResources.add(el);
                }
            }
        }
        for (JsonElement el : xmlStrings) {
            if (el.isJsonObject()) {
                JsonElement name = el.getAsJsonObject().get("name");
                if (name == null || seen.add(name.getAsString())) {
                    jsonResources.add(el);
                }
            }
        }

        return jsonResources;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tool: add_string_resource
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Adds a string resource to a project.
     * Writes to both the JSON data file AND the editable strings.xml via ProjectToolPaths.
     */
    public static class AddStringResourceTool implements AgentTool {

        @Override
        public String getName() {
            return "add_string_resource";
        }

        @Override
        public String getDescription() {
            return "Adds a string resource (key-value pair) to a Sketchware Pro project. "
                    + "Writes to both the project data file (JSON) and the editable strings.xml "
                    + "via ProjectToolPaths (Raw File API). "
                    + "If a resource with the same key already exists, it will be updated.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject keyProp = new JsonObject();
            keyProp.addProperty("type", "string");
            keyProp.addProperty("description", "The string resource key (e.g., \"app_name\", \"welcome_message\")");
            properties.add("key", keyProp);

            JsonObject valueProp = new JsonObject();
            valueProp.addProperty("type", "string");
            valueProp.addProperty("description", "The string value");
            properties.add("value", valueProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("key");
            required.add("value");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }
            if (!arguments.has("key") || arguments.get("key").isJsonNull()) {
                return error("Missing required parameter: key");
            }
            if (!arguments.has("value") || arguments.get("value").isJsonNull()) {
                return error("Missing required parameter: value");
            }

            String scId = arguments.get("sc_id").getAsString();
            String key = arguments.get("key").getAsString();
            String value = arguments.get("value").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            // ── Step 1: Write to JSON data file (Raw File API: read → parse → modify → write) ──
            File resourceFile = new File(context.getProjectDataDir(scId), "resource");
            boolean updatedJson = false;
            try {
                JsonArray resources = readResourceArray(resourceFile);
                boolean updated = false;
                for (JsonElement element : resources) {
                    if (element.isJsonObject()) {
                        JsonObject res = element.getAsJsonObject();
                        if (res.has("resType") && "string".equals(res.get("resType").getAsString())
                                && res.has("resName") && res.get("resName").getAsString().equals(key)) {
                            res.addProperty("resValue", value);
                            updated = true;
                            break;
                        }
                    }
                }
                if (!updated) {
                    JsonObject newResource = new JsonObject();
                    newResource.addProperty("resType", "string");
                    newResource.addProperty("resName", key);
                    newResource.addProperty("resValue", value);
                    resources.add(newResource);
                }
                writeRawFile(resourceFile, resources.toString());
                updatedJson = updated;
            } catch (IOException e) {
                return error("Failed to write to resource data file: " + e.getMessage());
            }

            // ── Step 2: Write to editable strings.xml via ProjectToolPaths (Low-level File API) ──
            boolean updatedXml = false;
            try {
                File stringsXml = getEditableXmlResourceFile(context, scId, "strings.xml");
                String existingRaw = readRawFile(stringsXml);
                updatedXml = existingRaw.contains("name=\"" + key + "\"");
                upsertXmlResource(stringsXml, "string", key, value);
            } catch (IOException e) {
                // XML write failure is non-fatal; JSON was already written
            }

            JsonObject result = new JsonObject();
            result.addProperty("key", key);
            result.addProperty("value", value);
            result.addProperty("action", updatedJson || updatedXml ? "updated" : "added");
            result.addProperty("message", "String resource " + (updatedJson || updatedXml ? "updated" : "added")
                    + " successfully (JSON data + strings.xml)");
            return success(result.toString());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tool: add_color_resource
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Adds a color resource to a project.
     * Writes to both the JSON data file AND the editable colors.xml via ProjectToolPaths.
     */
    public static class AddColorResourceTool implements AgentTool {

        @Override
        public String getName() {
            return "add_color_resource";
        }

        @Override
        public String getDescription() {
            return "Adds a color resource to a Sketchware Pro project. "
                    + "The color value should be a hex color string (e.g., \"#FF5722\") or an integer color value. "
                    + "Writes to both the project data file (JSON) and the editable colors.xml "
                    + "via ProjectToolPaths (Raw File API – no JSON parsing of XML files). "
                    + "If a resource with the same key exists, it will be updated.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject keyProp = new JsonObject();
            keyProp.addProperty("type", "string");
            keyProp.addProperty("description", "The color resource key (e.g., \"primary_color\", \"accent_color\")");
            properties.add("key", keyProp);

            JsonObject valueProp = new JsonObject();
            valueProp.addProperty("type", "string");
            valueProp.addProperty("description", "The color value as hex string (e.g., \"#FF5722\") or integer");
            properties.add("value", valueProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("key");
            required.add("value");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }
            if (!arguments.has("key") || arguments.get("key").isJsonNull()) {
                return error("Missing required parameter: key");
            }
            if (!arguments.has("value") || arguments.get("value").isJsonNull()) {
                return error("Missing required parameter: value");
            }

            String scId = arguments.get("sc_id").getAsString();
            String key = arguments.get("key").getAsString();
            String value = arguments.get("value").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            // ── Step 1: Write to JSON data file ──
            File resourceFile = new File(context.getProjectDataDir(scId), "resource");
            boolean updatedJson = false;
            try {
                JsonArray resources = readResourceArray(resourceFile);
                boolean updated = false;
                for (JsonElement element : resources) {
                    if (element.isJsonObject()) {
                        JsonObject res = element.getAsJsonObject();
                        if (res.has("resType") && "color".equals(res.get("resType").getAsString())
                                && res.has("resName") && res.get("resName").getAsString().equals(key)) {
                            res.addProperty("resValue", value);
                            updated = true;
                            break;
                        }
                    }
                }
                if (!updated) {
                    JsonObject newResource = new JsonObject();
                    newResource.addProperty("resType", "color");
                    newResource.addProperty("resName", key);
                    newResource.addProperty("resValue", value);
                    resources.add(newResource);
                }
                writeRawFile(resourceFile, resources.toString());
                updatedJson = updated;
            } catch (IOException e) {
                return error("Failed to write to resource data file: " + e.getMessage());
            }

            // ── Step 2: Write to editable colors.xml via ProjectToolPaths (Raw File API) ──
            boolean updatedXml = false;
            try {
                File colorsXml = getEditableXmlResourceFile(context, scId, "colors.xml");
                String existingRaw = readRawFile(colorsXml);
                updatedXml = existingRaw.contains("name=\"" + key + "\"");
                upsertXmlResource(colorsXml, "color", key, value);
            } catch (IOException e) {
                // XML write failure is non-fatal
            }

            JsonObject result = new JsonObject();
            result.addProperty("key", key);
            result.addProperty("value", value);
            result.addProperty("action", updatedJson || updatedXml ? "updated" : "added");
            result.addProperty("message", "Color resource " + (updatedJson || updatedXml ? "updated" : "added")
                    + " successfully (JSON data + colors.xml)");
            return success(result.toString());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tool: list_resources
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lists resources in a project, optionally filtered by type.
     * Reads from both the JSON data file and the editable XML files via ProjectToolPaths.
     */
    public static class ListResourcesTool implements AgentTool {

        @Override
        public String getName() {
            return "list_resources";
        }

        @Override
        public String getDescription() {
            return "Lists all resources in a Sketchware Pro project, optionally filtered by type "
                    + "(\"string\", \"color\"). Reads from both the project data file and the "
                    + "editable XML resource files (colors.xml, strings.xml) via ProjectToolPaths. "
                    + "Uses Raw File API: reads as text first, then parses – never fails on XML content.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject typeProp = new JsonObject();
            typeProp.addProperty("type", "string");
            typeProp.addProperty("description", "Filter by resource type: \"string\", \"color\". If not specified, all resources are returned.");
            properties.add("resource_type", typeProp);

            JsonObject sourceProp = new JsonObject();
            sourceProp.addProperty("type", "string");
            sourceProp.addProperty("description", "Source to read from: \"data\" (JSON data file), \"xml\" (editable XML files), \"all\" (both, default)");
            properties.add("source", sourceProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }

            String scId = arguments.get("sc_id").getAsString();
            String resourceType = arguments.has("resource_type") && !arguments.get("resource_type").isJsonNull()
                    ? arguments.get("resource_type").getAsString() : null;
            String source = arguments.has("source") && !arguments.get("source").isJsonNull()
                    ? arguments.get("source").getAsString() : "all";

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            JsonArray combined = new JsonArray();

            // ── Read from JSON data file ──
            if ("all".equals(source) || "data".equals(source)) {
                File resourceFile = new File(context.getProjectDataDir(scId), "resource");
                try {
                    JsonArray jsonResources = readResourceArray(resourceFile);
                    for (JsonElement el : jsonResources) combined.add(el);
                } catch (IOException e) {
                    // Non-fatal: continue to XML
                }
            }

            // ── Read from editable XML files via ProjectToolPaths (Raw File API) ──
            if ("all".equals(source) || "xml".equals(source)) {
                try {
                    if (resourceType == null || "color".equals(resourceType)) {
                        File colorsXml = getEditableXmlResourceFile(context, scId, "colors.xml");
                        JsonArray xmlColors = parseXmlResourceFile(colorsXml, "color");
                        for (JsonElement el : xmlColors) combined.add(el);
                    }
                    if (resourceType == null || "string".equals(resourceType)) {
                        File stringsXml = getEditableXmlResourceFile(context, scId, "strings.xml");
                        JsonArray xmlStrings = parseXmlResourceFile(stringsXml, "string");
                        for (JsonElement el : xmlStrings) combined.add(el);
                    }
                } catch (IOException e) {
                    // Non-fatal
                }
            }

            // ── Filter and build result ──
            JsonArray result = new JsonArray();
            for (JsonElement element : combined) {
                if (!element.isJsonObject()) continue;
                JsonObject res = element.getAsJsonObject();

                if (resourceType != null) {
                    String type = res.has("resType") ? res.get("resType").getAsString() : "";
                    if (!type.equals(resourceType)) continue;
                }

                JsonObject entry = new JsonObject();
                entry.addProperty("type", res.has("resType") ? res.get("resType").getAsString() : "unknown");
                entry.addProperty("key", res.has("resName") ? res.get("resName").getAsString() : "");
                entry.addProperty("value", res.has("resValue") ? res.get("resValue").getAsString() : "");
                result.add(entry);
            }

            JsonObject response = new JsonObject();
            response.add("resources", result);
            response.addProperty("count", result.size());
            response.addProperty("source", source);
            return success(response.toString());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tool: read_raw_resource_file
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reads any resource file as raw text without parsing.
     * This is the pure Low-level File API tool for when high-level tools fail.
     */
    public static class ReadRawResourceFileTool implements AgentTool {

        @Override
        public String getName() {
            return "read_raw_resource_file";
        }

        @Override
        public String getDescription() {
            return "Reads a project resource file as raw text without any JSON/XML parsing. "
                    + "Use this when other resource tools fail due to format issues. "
                    + "Supported files: \"resource\" (JSON data), \"colors.xml\", \"strings.xml\", "
                    + "or any path relative to the editable resource directory. "
                    + "IMPORTANT: For raw XML layout files like res/layout/design.xml, "
                    + "use read_file (not describe_layout) since these are standard Android XML, "
                    + "not Sketchware JSON view format.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject fileNameProp = new JsonObject();
            fileNameProp.addProperty("type", "string");
            fileNameProp.addProperty("description", "File to read: \"resource\", \"colors.xml\", \"strings.xml\", or relative path in editable res dir");
            properties.add("file_name", fileNameProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("file_name");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }
            if (!arguments.has("file_name") || arguments.get("file_name").isJsonNull()) {
                return error("Missing required parameter: file_name");
            }

            String scId = arguments.get("sc_id").getAsString();
            String fileName = arguments.get("file_name").getAsString().trim();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            // Resolve file path
            File targetFile;
            if ("resource".equals(fileName)) {
                targetFile = new File(context.getProjectDataDir(scId), "resource");
            } else if (fileName.endsWith(".xml")) {
                // Try editable res dir first, then values subdir
                File resDir = ProjectToolPaths.getProjectEditableResDir(scId);
                File candidate = new File(resDir, "values" + File.separator + fileName);
                if (!candidate.exists()) {
                    candidate = new File(resDir, fileName);
                }
                targetFile = candidate;
            } else {
                targetFile = new File(context.getProjectDataDir(scId), fileName);
            }

            if (!targetFile.exists()) {
                JsonObject result = new JsonObject();
                result.addProperty("file", fileName);
                result.addProperty("exists", false);
                result.addProperty("content", "");
                return success(result.toString());
            }

            try {
                String content = readRawFile(targetFile);
                // Return RAW content directly to avoid AI agent failing on large JSON metadata
                return success(content);
            } catch (IOException e) {
                return error("Failed to read file: " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tool: write_raw_resource_file
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Writes raw text to a resource file without any parsing.
     * This is the pure Low-level File API write tool.
     */
    public static class WriteRawResourceFileTool implements AgentTool {

        @Override
        public String getName() {
            return "write_raw_resource_file";
        }

        @Override
        public String getDescription() {
            return "Writes raw text content to a project resource file without any JSON/XML parsing. "
                    + "Use this to directly edit colors.xml, strings.xml, or the resource data file. "
                    + "Supported files: \"resource\", \"colors.xml\", \"strings.xml\". "
                    + "For raw XML layout files (e.g. res/layout/design.xml), use write_file instead. "
                    + "When writing XML with android:id, always use @+id/ to declare IDs (not @id/).";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject fileNameProp = new JsonObject();
            fileNameProp.addProperty("type", "string");
            fileNameProp.addProperty("description", "File to write: \"resource\", \"colors.xml\", or \"strings.xml\"");
            properties.add("file_name", fileNameProp);

            JsonObject contentProp = new JsonObject();
            contentProp.addProperty("type", "string");
            contentProp.addProperty("description", "The raw content to write to the file");
            properties.add("content", contentProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("file_name");
            required.add("content");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }
            if (!arguments.has("file_name") || arguments.get("file_name").isJsonNull()) {
                return error("Missing required parameter: file_name");
            }
            if (!arguments.has("content") || arguments.get("content").isJsonNull()) {
                return error("Missing required parameter: content");
            }

            String scId = arguments.get("sc_id").getAsString();
            String fileName = arguments.get("file_name").getAsString().trim();
            String content = arguments.get("content").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            // Security: only allow known resource files
            if (!fileName.equals("resource") && !fileName.equals("colors.xml")
                    && !fileName.equals("strings.xml") && !fileName.endsWith(".xml")) {
                return error("Unsupported file: " + fileName + ". Only resource, colors.xml, strings.xml, and XML files are allowed.");
            }

            File targetFile;
            if ("resource".equals(fileName)) {
                targetFile = new File(context.getProjectDataDir(scId), "resource");
            } else {
                File resDir = ProjectToolPaths.getProjectEditableResDir(scId);
                targetFile = new File(resDir, "values" + File.separator + fileName);
            }

            try {
                writeRawFile(targetFile, content);
                // Return simple raw success message
                return success("SUCCESS: Resource file '" + fileName + "' written (" + content.length() + " bytes)");
            } catch (IOException e) {
                return error("Failed to write file: " + e.getMessage());
            }
        }
    }

    // ══ extract_strings ═══════════════════════════════════════════════════════

    /**
     * Scans layout XML files and Java source for hardcoded strings, extracts them
     * to strings.xml, and patches the source files to use @string/xxx / R.string.xxx.
     */
    public static class ExtractStringsTool implements AgentTool {

        // XML attributes that contain translatable text
        private static final String[] XML_TEXT_ATTRS = {
            "android:text", "android:hint", "android:contentDescription",
            "android:title", "android:summary", "android:label",
            "android:description", "android:prompt"
        };

        // Pattern: android:text="some text" (not @string/ and not @+id/)
        private static final Pattern XML_HARDCODED = Pattern.compile(
            "(android:(?:text|hint|contentDescription|title|summary|label|description|prompt))=\"([^@\"][^\"]+)\"");

        // Pattern: .setText("some text")  .setHint("...") etc.
        private static final Pattern JAVA_HARDCODED = Pattern.compile(
            "\\.set(?:Text|Hint|Title|Message|Label)\\(\"([^\"]{2,})\"\\)");

        @Override public String getName() { return "extract_strings"; }

        @Override public String getDescription() {
            return "Scans layout XML files and Java source files for hardcoded strings "
                 + "(android:text=\"...\", setText(\"...\") etc.) and extracts them to strings.xml. "
                 + "Patches the source files to use @string/xxx (in XML) or R.string.xxx (in Java). "
                 + "Parameters: sc_id (required), activity_name (optional — scan one activity, "
                 + "default scans all), dry_run (boolean, default false — set true to preview without changes).";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject(); scId.addProperty("type","string");
            scId.addProperty("description","Project SC ID"); props.add("sc_id", scId);
            JsonObject act = new JsonObject(); act.addProperty("type","string");
            act.addProperty("description","Activity base name (e.g. 'main'). Omit to scan all.");
            props.add("activity_name", act);
            JsonObject dry = new JsonObject(); dry.addProperty("type","boolean");
            dry.addProperty("description","If true, report what would change without writing files");
            props.add("dry_run", dry);
            JsonArray req = new JsonArray(); req.add("sc_id");
            JsonObject s = new JsonObject(); s.addProperty("type","object");
            s.add("properties", props); s.add("required", req);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = args.has("sc_id") ? args.get("sc_id").getAsString() : null;
            if (scId == null || scId.isEmpty()) return error("sc_id is required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: " + scId);

            boolean dryRun = args.has("dry_run") && args.get("dry_run").getAsBoolean();
            String actFilter = args.has("activity_name") && !args.get("activity_name").isJsonNull()
                    ? args.get("activity_name").getAsString().trim() : null;

            File resDir  = ProjectToolPaths.getProjectEditableResDir(scId);
            File strFile = new File(resDir, "values/strings.xml");
            File dataDir = new File(ctx.getSketchwareDir(), "data/" + scId);

            // Load existing string keys to avoid duplicates
            java.util.Set<String> existingKeys = loadExistingStringKeys(strFile);

            // Discovered: key → value
            Map<String, String> discovered = new LinkedHashMap<>();
            // Patches: [file, old_text, new_text]
            java.util.List<String[]> patches = new ArrayList<>();

            // ── Scan layout files (view data file) ─────────────────────────
            File viewFile = new File(dataDir, "view");
            if (viewFile.exists()) {
                try {
                    String content = readRaw(viewFile);
                    scanXmlContent(content, actFilter, discovered, patches, existingKeys);
                } catch (IOException ignored) {}
            }

            // ── Scan editable XML layouts ───────────────────────────────────
            File layoutDir = new File(resDir, "layout");
            if (layoutDir.exists()) {
                File[] xmlFiles = layoutDir.listFiles((d, n) -> n.endsWith(".xml"));
                if (xmlFiles != null) {
                    for (File f : xmlFiles) {
                        if (actFilter != null && !f.getName().startsWith(actFilter)) continue;
                        try {
                            String content = readRaw(f);
                            scanXmlInline(f.getAbsolutePath(), content, discovered, patches, existingKeys);
                        } catch (IOException ignored) {}
                    }
                }
            }

            // ── Scan Java source files ──────────────────────────────────────
            File javaDir = new File(ctx.getSketchwareDir(), "mysc/" + scId + "/java");
            if (javaDir.exists()) {
                for (File jf : findJavaFiles(javaDir, actFilter)) {
                    try {
                        String content = readRaw(jf);
                        scanJava(jf.getAbsolutePath(), content, discovered, patches, existingKeys);
                    } catch (IOException ignored) {}
                }
            }

            if (discovered.isEmpty()) {
                return success("No hardcoded strings found" + (actFilter != null ? " in '" + actFilter + "'" : "") + ". All text already uses @string/ or R.string.xxx.");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(discovered.size()).append(" hardcoded string(s) to extract.\n\n");

            if (dryRun) {
                sb.append("DRY RUN — no files modified.\n\n");
                sb.append("Strings that would be added to strings.xml:\n");
                for (Map.Entry<String, String> e : discovered.entrySet())
                    sb.append("  <string name=\"").append(e.getKey()).append("\">").append(e.getValue()).append("</string>\n");
                sb.append("\nPatches that would be applied:\n");
                for (String[] p : patches)
                    sb.append("  ").append(p[0]).append(": \"").append(p[1]).append("\" → \"").append(p[2]).append("\"\n");
                return success(sb.toString());
            }

            // ── Apply: add strings to strings.xml ──────────────────────────
            try {
                appendStringsToFile(strFile, discovered);
                sb.append("✅ Added ").append(discovered.size()).append(" string(s) to strings.xml.\n");
            } catch (IOException e) {
                return error("Failed to write strings.xml: " + e.getMessage());
            }

            // ── Apply: patch source files ───────────────────────────────────
            int patched = 0;
            for (String[] p : patches) {
                try {
                    File f = new File(p[0]);
                    String content = readRaw(f);
                    String newContent = content.replace(p[1], p[2]);
                    if (!newContent.equals(content)) {
                        writeRaw(f, newContent);
                        patched++;
                    }
                } catch (IOException ignored) {}
            }
            sb.append("✅ Patched ").append(patched).append(" file(s).\n\n");
            sb.append("Extracted strings:\n");
            for (Map.Entry<String, String> e : discovered.entrySet())
                sb.append("  @string/").append(e.getKey()).append(" = \"").append(e.getValue()).append("\"\n");

            return success(sb.toString());
        }

        private void scanXmlContent(String content, String actFilter,
                Map<String, String> out, java.util.List<String[]> patches,
                java.util.Set<String> existing) {
            Matcher m = XML_HARDCODED.matcher(content);
            while (m.find()) {
                String attr = m.group(1);
                String val  = m.group(2).trim();
                if (val.isEmpty() || val.startsWith("@") || val.startsWith("?")) continue;
                String key = toResourceKey(val);
                if (existing.contains(key)) continue;
                out.put(key, val);
                existing.add(key);
            }
        }

        private void scanXmlInline(String filePath, String content,
                Map<String, String> out, java.util.List<String[]> patches,
                java.util.Set<String> existing) {
            Matcher m = XML_HARDCODED.matcher(content);
            while (m.find()) {
                String attr = m.group(1);
                String val  = m.group(2).trim();
                if (val.isEmpty() || val.startsWith("@") || val.startsWith("?")) continue;
                String key = toResourceKey(val);
                if (!existing.contains(key)) {
                    out.put(key, val);
                    existing.add(key);
                }
                patches.add(new String[]{filePath,
                        attr + "=\"" + val + "\"",
                        attr + "=\"@string/" + key + "\""});
            }
        }

        private void scanJava(String filePath, String content,
                Map<String, String> out, java.util.List<String[]> patches,
                java.util.Set<String> existing) {
            Matcher m = JAVA_HARDCODED.matcher(content);
            while (m.find()) {
                String val = m.group(1).trim();
                if (val.isEmpty()) continue;
                String key = toResourceKey(val);
                if (!existing.contains(key)) {
                    out.put(key, val);
                    existing.add(key);
                }
                String methodCall = m.group(0);
                String replaced   = methodCall.replace("\"" + val + "\"",
                        "getString(R.string." + key + ")");
                patches.add(new String[]{filePath, methodCall, replaced});
            }
        }

        private static String toResourceKey(String value) {
            return value.toLowerCase()
                    .replaceAll("[^a-z0-9]+", "_")
                    .replaceAll("^_+|_+$", "")
                    .replaceAll("_+", "_");
        }

        private java.util.Set<String> loadExistingStringKeys(File strFile) {
            java.util.Set<String> keys = new java.util.HashSet<>();
            if (!strFile.exists()) return keys;
            try {
                String content = readRaw(strFile);
                Matcher m = Pattern.compile("name=\"([^\"]+)\"").matcher(content);
                while (m.find()) keys.add(m.group(1));
            } catch (IOException ignored) {}
            return keys;
        }

        private void appendStringsToFile(File f, Map<String, String> strings) throws IOException {
            f.getParentFile().mkdirs();
            String existing = f.exists() ? readRaw(f) : "<resources>\n</resources>";
            StringBuilder entries = new StringBuilder();
            for (Map.Entry<String, String> e : strings.entrySet()) {
                String escaped = e.getValue()
                        .replace("&", "&amp;").replace("<", "&lt;")
                        .replace(">", "&gt;").replace("'", "\\'")
                        .replace("\"", "\\\"");
                entries.append("    <string name=\"").append(e.getKey()).append("\">")
                       .append(escaped).append("</string>\n");
            }
            String updated = existing.contains("</resources>")
                    ? existing.replace("</resources>", entries + "</resources>")
                    : existing + "\n" + entries;
            writeRaw(f, updated);
        }

        private java.util.List<File> findJavaFiles(File dir, String actFilter) {
            java.util.List<File> result = new ArrayList<>();
            File[] files = dir.listFiles();
            if (files == null) return result;
            for (File f : files) {
                if (f.isDirectory()) result.addAll(findJavaFiles(f, actFilter));
                else if (f.getName().endsWith(".java")) {
                    if (actFilter == null || f.getName().toLowerCase().contains(actFilter.toLowerCase()))
                        result.add(f);
                }
            }
            return result;
        }

        private static String readRaw(File f) throws IOException {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append("\n");
            }
            return sb.toString();
        }

        private static void writeRaw(File f, String content) throws IOException {
            f.getParentFile().mkdirs();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) { w.write(content); }
        }
    }

    // ══ create_locale_strings ═════════════════════════════════════════════════

    /**
     * Creates or updates a locale-specific strings.xml (values-XX/strings.xml).
     * The AI provides the translations as a JSON key→value map.
     */
    public static class CreateLocaleStringsTool implements AgentTool {

        @Override public String getName() { return "create_locale_strings"; }

        @Override public String getDescription() {
            return "Creates or updates a locale-specific strings.xml file for app translation. "
                 + "Usage flow: (1) call list_resources to get all string keys and values, "
                 + "(2) translate the values in your response, "
                 + "(3) call this tool with the translations map to write values-XX/strings.xml. "
                 + "Supported locales: ar (Arabic), fr (French), es (Spanish), de (German), "
                 + "zh (Chinese), ru (Russian), tr (Turkish), pt (Portuguese), etc. "
                 + "Pass 'translations' as a JSON object: {\"key\": \"translated_value\", ...}";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject(); scId.addProperty("type","string");
            scId.addProperty("description","Project SC ID"); props.add("sc_id", scId);
            JsonObject locale = new JsonObject(); locale.addProperty("type","string");
            locale.addProperty("description","BCP-47 locale code: ar, fr, es, de, zh, ru, tr, pt, ja, ko, hi, etc.");
            props.add("locale", locale);
            JsonObject trans = new JsonObject(); trans.addProperty("type","object");
            trans.addProperty("description","JSON object of {\"string_key\": \"translated_value\", ...}");
            props.add("translations", trans);
            JsonArray req = new JsonArray(); req.add("sc_id"); req.add("locale"); req.add("translations");
            JsonObject s = new JsonObject(); s.addProperty("type","object");
            s.add("properties", props); s.add("required", req);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId   = args.has("sc_id")   ? args.get("sc_id").getAsString()   : null;
            String locale = args.has("locale")  ? args.get("locale").getAsString()  : null;
            if (scId == null || scId.isEmpty())   return error("sc_id is required");
            if (locale == null || locale.isEmpty()) return error("locale is required (e.g. 'ar', 'fr')");
            if (!args.has("translations") || args.get("translations").isJsonNull())
                return error("translations is required — pass a JSON object of {key: value}");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: " + scId);

            JsonObject trans = args.get("translations").getAsJsonObject();
            if (trans.size() == 0) return error("translations map is empty");

            File resDir  = ProjectToolPaths.getProjectEditableResDir(scId);
            File valDir  = new File(resDir, "values-" + locale);
            File strFile = new File(valDir, "strings.xml");

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            xml.append("<resources>\n");
            for (Map.Entry<String, com.google.gson.JsonElement> e : trans.entrySet()) {
                String key = e.getKey();
                String val = e.getValue().isJsonNull() ? "" : e.getValue().getAsString();
                String escaped = val.replace("&","&amp;").replace("<","&lt;")
                        .replace(">","&gt;").replace("'","\\'").replace("\"","\\\"");
                xml.append("    <string name=\"").append(key).append("\">")
                   .append(escaped).append("</string>\n");
            }
            xml.append("</resources>\n");

            try {
                valDir.mkdirs();
                try (Writer w = new OutputStreamWriter(new FileOutputStream(strFile), StandardCharsets.UTF_8)) { w.write(xml.toString()); }
                return success("Created values-" + locale + "/strings.xml with "
                        + trans.size() + " translation(s).\nPath: " + strFile.getAbsolutePath());
            } catch (IOException e) {
                return error("Failed to write: " + e.getMessage());
            }
        }
    }
}
