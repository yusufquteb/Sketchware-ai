package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import pro.sketchware.activities.projecttools.ProjectToolPaths;
import pro.sketchware.ai.models.ToolResult;

/**
 * DrawableTools — AI tools for creating and managing XML drawable resources.
 *
 * Tools:
 *   create_drawable  — generate any XML drawable (shape, selector, ripple, layer-list, gradient)
 *                      from parameters OR from a raw XML string.
 */
public final class DrawableTools {

    private DrawableTools() {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ToolResult success(String o) { return ToolResult.success(null, o); }
    private static ToolResult error(String m)   { return ToolResult.failure(null, m); }

    private static void writeFile(File f, String content) throws IOException {
        f.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) { w.write(content); }
    }

    // ══ create_drawable ═══════════════════════════════════════════════════════

    public static class CreateDrawableTool implements AgentTool {

        @Override public String getName() { return "create_drawable"; }

        @Override public String getDescription() {
            return "Creates an XML drawable resource file in the project's res/drawable directory. "
                 + "Can create shapes (rectangle, oval, ring), selectors (state-based), ripple effects, "
                 + "layer-lists, and gradient backgrounds. "
                 + "Pass 'xml_content' with the full drawable XML, OR use the 'template' shortcuts "
                 + "(rounded_button, circle, gradient_bg, ripple_primary, divider, card_bg). "
                 + "The file is saved as res/drawable/<name>.xml and immediately available as @drawable/<name>.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();

            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            scId.addProperty("description", "Project SC ID");
            props.add("sc_id", scId);

            JsonObject name = new JsonObject();
            name.addProperty("type", "string");
            name.addProperty("description",
                    "Drawable file name WITHOUT extension — e.g. \"btn_primary\", \"bg_card\". "
                    + "Use lowercase with underscores.");
            props.add("name", name);

            JsonObject xmlContent = new JsonObject();
            xmlContent.addProperty("type", "string");
            xmlContent.addProperty("description",
                    "Complete XML content for the drawable. Must start with <?xml version... "
                    + "or with the root element tag. Use this when you need full control.");
            props.add("xml_content", xmlContent);

            JsonObject template = new JsonObject();
            template.addProperty("type", "string");
            template.addProperty("description",
                    "Quick template shortcut (use instead of xml_content for common shapes):\n"
                    + "  rounded_button  — filled rectangle with 8dp corners\n"
                    + "  circle          — solid oval\n"
                    + "  gradient_bg     — vertical gradient background\n"
                    + "  ripple_primary  — ripple over a colored background\n"
                    + "  divider         — 1dp horizontal line\n"
                    + "  card_bg         — white rectangle with 12dp corners + shadow\n"
                    + "  selector_btn    — pressed/normal state selector");
            props.add("template", template);

            JsonObject fillColor = new JsonObject();
            fillColor.addProperty("type", "string");
            fillColor.addProperty("description", "Fill color hex (#RRGGBB or #AARRGGBB) used in templates");
            props.add("fill_color", fillColor);

            JsonObject strokeColor = new JsonObject();
            strokeColor.addProperty("type", "string");
            strokeColor.addProperty("description", "Stroke/border color hex used in templates");
            props.add("stroke_color", strokeColor);

            JsonObject cornerRadius = new JsonObject();
            cornerRadius.addProperty("type", "integer");
            cornerRadius.addProperty("description", "Corner radius in dp (for rounded_button / card_bg templates)");
            props.add("corner_radius_dp", cornerRadius);

            JsonObject startColor = new JsonObject();
            startColor.addProperty("type", "string");
            startColor.addProperty("description", "Gradient start color (for gradient_bg template)");
            props.add("gradient_start", startColor);

            JsonObject endColor = new JsonObject();
            endColor.addProperty("type", "string");
            endColor.addProperty("description", "Gradient end color (for gradient_bg template)");
            props.add("gradient_end", endColor);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("name");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", props);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            if (!args.has("sc_id") || args.get("sc_id").isJsonNull())
                return error("Missing sc_id");
            if (!args.has("name") || args.get("name").isJsonNull())
                return error("Missing name");

            String scId    = args.get("sc_id").getAsString().trim();
            String name    = args.get("name").getAsString().trim()
                              .replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();

            if (!ctx.isProjectAllowed(scId))
                return error("Access denied: project " + scId);
            if (name.isEmpty())
                return error("Drawable name must not be empty");

            // Resolve XML content
            String xml;
            if (args.has("xml_content") && !args.get("xml_content").isJsonNull()
                    && !args.get("xml_content").getAsString().trim().isEmpty()) {
                xml = args.get("xml_content").getAsString().trim();
                if (!xml.startsWith("<?xml") && !xml.startsWith("<"))
                    return error("xml_content must start with <?xml or a root XML tag");
            } else if (args.has("template") && !args.get("template").isJsonNull()) {
                xml = buildTemplate(args);
                if (xml == null)
                    return error("Unknown template. Valid templates: rounded_button, circle, "
                            + "gradient_bg, ripple_primary, divider, card_bg, selector_btn");
            } else {
                return error("Provide either 'xml_content' (full XML) or 'template' (shortcut name)");
            }

            // Ensure proper XML header
            if (!xml.startsWith("<?xml"))
                xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + xml;

            File drawableDir = new File(ProjectToolPaths.getProjectEditableResDir(scId), "drawable");
            File outFile = new File(drawableDir, name + ".xml");

            try {
                writeFile(outFile, xml);
                return success("Drawable created: @drawable/" + name
                        + "\nPath: " + outFile.getAbsolutePath()
                        + "\nSize: " + xml.length() + " bytes"
                        + "\n\nContent:\n" + xml);
            } catch (IOException e) {
                return error("Failed to write drawable: " + e.getMessage());
            }
        }

        // ── Templates ─────────────────────────────────────────────────────────

        private String buildTemplate(JsonObject args) {
            String t           = args.has("template") ? args.get("template").getAsString() : "";
            String fill        = color(args, "fill_color",     "#6200EE");
            String stroke      = color(args, "stroke_color",   "");
            String gStart      = color(args, "gradient_start", "#6200EE");
            String gEnd        = color(args, "gradient_end",   "#3700B3");
            int    corners     = intVal(args, "corner_radius_dp", 8);

            switch (t.toLowerCase().trim()) {
                case "rounded_button":
                    return "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                         + "    android:shape=\"rectangle\">\n"
                         + "    <solid android:color=\"" + fill + "\" />\n"
                         + "    <corners android:radius=\"" + corners + "dp\" />\n"
                         + (stroke.isEmpty() ? "" :
                           "    <stroke android:width=\"1dp\" android:color=\"" + stroke + "\" />\n")
                         + "</shape>\n";

                case "circle":
                    return "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                         + "    android:shape=\"oval\">\n"
                         + "    <solid android:color=\"" + fill + "\" />\n"
                         + "</shape>\n";

                case "gradient_bg":
                    return "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                         + "    android:shape=\"rectangle\">\n"
                         + "    <gradient\n"
                         + "        android:startColor=\"" + gStart + "\"\n"
                         + "        android:endColor=\"" + gEnd + "\"\n"
                         + "        android:angle=\"270\" />\n"
                         + "</shape>\n";

                case "ripple_primary":
                    return "<ripple xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                         + "    android:color=\"?attr/colorControlHighlight\">\n"
                         + "    <item>\n"
                         + "        <shape android:shape=\"rectangle\">\n"
                         + "            <solid android:color=\"" + fill + "\" />\n"
                         + "            <corners android:radius=\"" + corners + "dp\" />\n"
                         + "        </shape>\n"
                         + "    </item>\n"
                         + "</ripple>\n";

                case "divider":
                    return "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                         + "    android:shape=\"rectangle\">\n"
                         + "    <solid android:color=\"" + (fill.equals("#6200EE") ? "#DDDDDD" : fill) + "\" />\n"
                         + "    <size android:height=\"1dp\" />\n"
                         + "</shape>\n";

                case "card_bg":
                    return "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                         + "    android:shape=\"rectangle\">\n"
                         + "    <solid android:color=\"" + (fill.equals("#6200EE") ? "#FFFFFF" : fill) + "\" />\n"
                         + "    <corners android:radius=\"" + corners + "dp\" />\n"
                         + "    <stroke android:width=\"1dp\" android:color=\"#E0E0E0\" />\n"
                         + "</shape>\n";

                case "selector_btn":
                    return "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                         + "    <item android:state_pressed=\"true\">\n"
                         + "        <shape android:shape=\"rectangle\">\n"
                         + "            <solid android:color=\"" + gEnd + "\" />\n"
                         + "            <corners android:radius=\"" + corners + "dp\" />\n"
                         + "        </shape>\n"
                         + "    </item>\n"
                         + "    <item>\n"
                         + "        <shape android:shape=\"rectangle\">\n"
                         + "            <solid android:color=\"" + fill + "\" />\n"
                         + "            <corners android:radius=\"" + corners + "dp\" />\n"
                         + "        </shape>\n"
                         + "    </item>\n"
                         + "</selector>\n";

                default:
                    return null;
            }
        }

        private static String color(JsonObject a, String key, String fallback) {
            if (a.has(key) && !a.get(key).isJsonNull()) {
                String v = a.get(key).getAsString().trim();
                if (!v.isEmpty()) return v;
            }
            return fallback;
        }

        private static int intVal(JsonObject a, String key, int fallback) {
            if (a.has(key) && !a.get(key).isJsonNull()) {
                try { return a.get(key).getAsInt(); } catch (Exception ignored) {}
            }
            return fallback;
        }
    }
}
