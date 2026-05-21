package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import pro.sketchware.ai.models.ToolResult;

/**
 * AndroidManifest.xml tools — read and surgically edit the Sketchware raw_override.xml
 * which is the user-editable manifest layer merged with the generated base at build time.
 */
public class ManifestTools {

    private static ToolResult success(String msg) { return ToolResult.success(null, msg); }
    private static ToolResult error(String msg)   { return ToolResult.failure(null, msg); }

    private static File manifestFile(ToolContext ctx, String scId) {
        return new File(ctx.getProjectDataDir(scId),
                "manifest" + File.separator + "raw_override.xml");
    }

    private static String readFile(File f) {
        if (!f.exists()) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        } catch (IOException e) { return null; }
        return sb.toString();
    }

    private static void writeFile(File f, String content) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    // ── ReadManifestTool ──────────────────────────────────────────────────────

    public static class ReadManifestTool implements AgentTool {
        @Override public String getName() { return "read_manifest"; }
        @Override public String getDescription() {
            return "Reads the project's AndroidManifest raw_override.xml. Returns the full XML content.";
        }
        @Override public JsonObject getParametersSchema() {
            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "Project SC ID");
            JsonObject props = new JsonObject();
            props.add("sc_id", scIdProp);
            JsonArray req = new JsonArray(); req.add("sc_id");
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", props);
            schema.add("required", req);
            return schema;
        }
        @Override public ToolResult execute(JsonObject args, ToolContext context) {
            String scId = args.has("sc_id") ? args.get("sc_id").getAsString() : null;
            if (scId == null || scId.isEmpty()) return error("sc_id is required");
            if (!context.isProjectAllowed(scId)) return error("Project not in workspace");
            File f = manifestFile(context, scId);
            String content = readFile(f);
            if (content == null)
                return error("Manifest file not found at: " + f.getAbsolutePath());
            return success(content);
        }
    }

    // ── EditManifestAttributeTool ─────────────────────────────────────────────

    public static class EditManifestAttributeTool implements AgentTool {
        @Override public String getName() { return "edit_manifest_attribute"; }
        @Override public String getDescription() {
            return "Sets or updates an XML attribute on a specific element in AndroidManifest. "
                    + "Example: set android:screenOrientation=\"portrait\" on <activity android:name=\".MainActivity\">. "
                    + "Use read_manifest first to see the current content.";
        }
        @Override public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            addStringProp(props, "sc_id",          "Project SC ID");
            addStringProp(props, "element_tag",    "XML element tag to match, e.g. 'activity', 'application', 'manifest'");
            addStringProp(props, "match_attribute", "Optional: attribute+value to identify the correct element, e.g. 'android:name=\".MainActivity\"'");
            addStringProp(props, "set_attribute",   "Attribute name+value to set, e.g. 'android:screenOrientation=\"portrait\"'");
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("element_tag"); req.add("set_attribute");
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", props);
            schema.add("required", req);
            return schema;
        }
        @Override public ToolResult execute(JsonObject args, ToolContext context) {
            String scId          = args.has("sc_id")           ? args.get("sc_id").getAsString()           : null;
            String elementTag    = args.has("element_tag")     ? args.get("element_tag").getAsString()     : null;
            String matchAttr     = args.has("match_attribute") ? args.get("match_attribute").getAsString() : null;
            String setAttribute  = args.has("set_attribute")   ? args.get("set_attribute").getAsString()   : null;
            if (scId == null || scId.isEmpty()) return error("sc_id is required");
            if (!context.isProjectAllowed(scId)) return error("Project not in workspace");
            if (elementTag == null || setAttribute == null)
                return error("element_tag and set_attribute are required");

            File f = manifestFile(context, scId);
            String content = readFile(f);
            if (content == null) return error("Manifest file not found");

            // Split attribute name from "name=\"value\"" or "name=value"
            String attrName, attrValue;
            int eq = setAttribute.indexOf('=');
            if (eq == -1) return error("set_attribute must be in the form 'name=\"value\"'");
            attrName  = setAttribute.substring(0, eq).trim();
            attrValue = setAttribute.substring(eq + 1).trim()
                    .replaceAll("^\"|\"$", ""); // strip surrounding quotes

            // Find the element opening tag
            String searchTag = "<" + elementTag;
            int tagIdx = -1;
            if (matchAttr != null && !matchAttr.isEmpty()) {
                // Search for element containing the match attribute
                int start = 0;
                while ((tagIdx = content.indexOf(searchTag, start)) != -1) {
                    int tagEnd = content.indexOf('>', tagIdx);
                    if (tagEnd == -1) break;
                    String tagContent = content.substring(tagIdx, tagEnd + 1);
                    if (tagContent.contains(matchAttr)) break;
                    start = tagIdx + 1;
                }
            } else {
                tagIdx = content.indexOf(searchTag);
            }

            if (tagIdx == -1)
                return error("Element <" + elementTag + "> not found in manifest"
                        + (matchAttr != null ? " with attribute: " + matchAttr : ""));

            int tagEnd = content.indexOf('>', tagIdx);
            if (tagEnd == -1) return error("Malformed manifest XML");
            String tag = content.substring(tagIdx, tagEnd);

            String newTag;
            if (tag.contains(attrName + "=")) {
                // Replace existing attribute value
                newTag = tag.replaceAll(java.util.regex.Pattern.quote(attrName) + "\\s*=\\s*\"[^\"]*\"",
                        attrName + "=\"" + attrValue + "\"");
            } else {
                // Insert new attribute before closing > or />
                if (tag.endsWith("/")) {
                    newTag = tag.substring(0, tag.length() - 1) + " " + attrName + "=\"" + attrValue + "\" /";
                } else {
                    newTag = tag + " " + attrName + "=\"" + attrValue + "\"";
                }
            }

            String newContent = content.substring(0, tagIdx) + newTag + content.substring(tagEnd);
            try {
                writeFile(f, newContent);
                return success("Updated " + elementTag + ": set " + attrName + "=\"" + attrValue + "\"");
            } catch (IOException e) {
                return error("Write failed: " + e.getMessage());
            }
        }

        private static void addStringProp(JsonObject props, String name, String desc) {
            JsonObject p = new JsonObject();
            p.addProperty("type", "string");
            p.addProperty("description", desc);
            props.add(name, p);
        }
    }

    // ── AddManifestTagTool ────────────────────────────────────────────────────

    public static class AddManifestTagTool implements AgentTool {
        @Override public String getName() { return "add_manifest_tag"; }
        @Override public String getDescription() {
            return "Inserts a raw XML tag inside a specified parent element in AndroidManifest. "
                    + "Use for adding <meta-data>, <intent-filter>, <provider>, <service>, <receiver>, etc. "
                    + "The tag is inserted before the closing parent tag.";
        }
        @Override public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            addStringProp(props, "sc_id",        "Project SC ID");
            addStringProp(props, "parent_tag",   "Parent element tag, e.g. 'application', 'activity'");
            addStringProp(props, "match_parent",  "Optional: attribute to identify the correct parent element");
            addStringProp(props, "xml_content",  "Full XML snippet to insert, e.g. '<meta-data android:name=\"key\" android:value=\"val\" />'");
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("parent_tag"); req.add("xml_content");
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", props);
            schema.add("required", req);
            return schema;
        }
        @Override public ToolResult execute(JsonObject args, ToolContext context) {
            String scId       = args.has("sc_id")         ? args.get("sc_id").getAsString()         : null;
            String parentTag  = args.has("parent_tag")    ? args.get("parent_tag").getAsString()    : null;
            String matchPar   = args.has("match_parent")  ? args.get("match_parent").getAsString()  : null;
            String xmlContent = args.has("xml_content")   ? args.get("xml_content").getAsString()   : null;
            if (scId == null || scId.isEmpty()) return error("sc_id is required");
            if (!context.isProjectAllowed(scId)) return error("Project not in workspace");
            if (parentTag == null || xmlContent == null) return error("parent_tag and xml_content are required");

            File f = manifestFile(context, scId);
            String content = readFile(f);
            if (content == null) return error("Manifest file not found");

            // Find the closing tag of the parent element
            String closingTag = "</" + parentTag + ">";
            int closeIdx = -1;
            if (matchPar != null && !matchPar.isEmpty()) {
                // Find the parent element then its closing tag
                int openIdx = content.indexOf("<" + parentTag);
                while (openIdx != -1) {
                    int openEnd = content.indexOf('>', openIdx);
                    if (openEnd != -1 && content.substring(openIdx, openEnd + 1).contains(matchPar)) {
                        closeIdx = content.indexOf(closingTag, openEnd);
                        break;
                    }
                    openIdx = content.indexOf("<" + parentTag, openIdx + 1);
                }
            } else {
                closeIdx = content.lastIndexOf(closingTag);
            }

            if (closeIdx == -1)
                return error("Closing tag </" + parentTag + "> not found in manifest");

            String indent = "    ";
            String newContent = content.substring(0, closeIdx)
                    + indent + xmlContent.trim() + "\n"
                    + content.substring(closeIdx);
            try {
                writeFile(f, newContent);
                return success("Inserted into <" + parentTag + ">:\n" + xmlContent.trim());
            } catch (IOException e) {
                return error("Write failed: " + e.getMessage());
            }
        }

        private static void addStringProp(JsonObject props, String name, String desc) {
            JsonObject p = new JsonObject();
            p.addProperty("type", "string");
            p.addProperty("description", desc);
            props.add(name, p);
        }
    }
}
