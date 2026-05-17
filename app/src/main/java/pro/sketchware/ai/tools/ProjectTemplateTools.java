package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.models.ToolResult;

/**
 * ProjectTemplateTools — أدوات القوالب الجاهزة والترجمة والتدويل.
 *
 * الأدوات المتاحة:
 *   create_from_template — إنشاء تطبيق من قالب جاهز (calculator, todo_list, إلخ)
 *   add_locale_strings   — إضافة ترجمات لموارد النصوص (ar, fr, es, de, ...)
 *
 * تم نقل هذه الأدوات من Phase3Tools لتجميع الأدوات المتشابهة في ملف واحد.
 */
public final class ProjectTemplateTools {

    private ProjectTemplateTools() {}

    // ── مساعدات مشتركة ────────────────────────────────────────────────────────

    static ToolResult ok(String output)  { return ToolResult.success(null, output); }
    static ToolResult err(String msg)    { return ToolResult.failure(null, msg); }

    static String req(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) return null;
        return args.get(key).getAsString().trim();
    }

    static void writeFile(File f, String content) throws IOException {
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(f)) { fw.write(content); }
    }

    static void addP(JsonObject props, String key, String type, String desc) {
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        p.addProperty("description", desc);
        props.add(key, p);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. CREATE FROM TEMPLATE TOOL
    // ════════════════════════════════════════════════════════════════════════

    public static class CreateFromTemplateTool implements AgentTool {
        @Override public String getName() { return "create_from_template"; }

        @Override public String getDescription() {
            return "Returns a step-by-step plan to build a Sketchware Pro app from a named template. "
                 + "Templates: calculator, todo_list, notes, login_screen, splash_screen, "
                 + "settings_screen, profile_screen, list_detail. "
                 + "Each plan uses only available tools (add_view, add_block, write_file, etc.).";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "template_name", "string", "calculator, todo_list, notes, login_screen, splash_screen");
            addP(p, "app_name",      "string", "Display name, e.g. 'My Calculator'");
            addP(p, "package_name",  "string", "Java package, e.g. 'com.example.calc'");
            s.add("properties", p);
            JsonArray r = new JsonArray();
            r.add("template_name"); r.add("app_name"); r.add("package_name");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String template = req(args, "template_name");
            String appName  = req(args, "app_name");
            String pkg      = req(args, "package_name");
            if (template == null || appName == null || pkg == null)
                return err("template_name, app_name and package_name are required");
            ctx.reportProgress("Generating template plan: " + template + "...", -1, true);

            switch (template.toLowerCase().trim()) {
                case "calculator":
                    return ok("TEMPLATE: Calculator App — " + appName + "\n\n"
                        + "1. create_project  name=\"" + appName + "\"  package=\"" + pkg + "\"\n"
                        + "2. add_view root LinearLayout vertical\n"
                        + "3. add_view TextView id=tvDisplay text=0 textSize=40 gravity=end\n"
                        + "4. add_view GridLayout for digit buttons (0-9, +, -, *, /, =, C)\n"
                        + "5. add_view Button for each digit and operator\n"
                        + "6. add_block MainActivity.onCreate: init String currentInput, String operator\n"
                        + "7. add_block each digit button onClick: currentInput+=digit; tvDisplay.setText(currentInput)\n"
                        + "8. add_block operator buttons: store operator, clear currentInput\n"
                        + "9. add_block equals button: evaluate and show result\n"
                        + "10. build_project\n\n"
                        + "Use addSourceDirectly opCode for Java expression evaluation.");

                case "todo_list":
                    return ok("TEMPLATE: Todo List App — " + appName + "\n\n"
                        + "1. create_project  name=\"" + appName + "\"  package=\"" + pkg + "\"\n"
                        + "2. add_view root LinearLayout vertical\n"
                        + "3. add_view LinearLayout horizontal: EditText id=etTask + Button id=btnAdd\n"
                        + "4. add_view ListView id=lvTasks  layout.weight=1\n"
                        + "5. write_file MainActivity.java with ArrayList<String> + ArrayAdapter\n"
                        + "6. add_block onCreate: init adapter, attach to ListView\n"
                        + "7. add_block btnAdd.onClick: add text, notifyDataSetChanged\n"
                        + "8. add_block lvTasks.onItemLongClick: delete dialog\n"
                        + "9. build_project");

                case "login_screen":
                    return ok("TEMPLATE: Login Screen — " + appName + "\n\n"
                        + "1. create_project  name=\"" + appName + "\"  package=\"" + pkg + "\"\n"
                        + "2. add_view root: ScrollView > LinearLayout vertical\n"
                        + "3. add_view ImageView id=ivLogo\n"
                        + "4. add_view EditText id=etEmail  inputType=textEmailAddress\n"
                        + "5. add_view EditText id=etPassword  inputType=textPassword\n"
                        + "6. add_view Button id=btnLogin text=Login\n"
                        + "7. add_view TextView id=tvSignup text=Create account\n"
                        + "8. create_activity HomeActivity\n"
                        + "9. add_block btnLogin.onClick: validate, startActivity(HomeActivity)\n"
                        + "10. build_project");

                case "splash_screen":
                    return ok("TEMPLATE: Splash Screen — " + appName + "\n\n"
                        + "1. create_project  name=\"" + appName + "\"  package=\"" + pkg + "\"\n"
                        + "2. add_view root RelativeLayout fullscreen\n"
                        + "3. add_view ImageView id=ivLogo centered\n"
                        + "4. add_view ProgressBar id=pbLoading below logo\n"
                        + "5. create_activity MainActivity\n"
                        + "6. add_block SplashActivity.onCreate: addSourceDirectly with:\n"
                        + "   new Handler(Looper.getMainLooper()).postDelayed(() -> {\n"
                        + "     startActivity(new Intent(this, MainActivity.class)); finish();\n"
                        + "   }, 2000);\n"
                        + "7. build_project");

                case "notes":
                    return ok("TEMPLATE: Notes App — " + appName + "\n\n"
                        + "1. create_project  name=\"" + appName + "\"  package=\"" + pkg + "\"\n"
                        + "2. create_activity NoteEditActivity\n"
                        + "3. Main activity: add_view RecyclerView/ListView + FloatingActionButton\n"
                        + "4. NoteEditActivity: EditText title + EditText multiline body\n"
                        + "5. Store notes as JSON in SharedPreferences\n"
                        + "6. FAB onClick: startActivity(NoteEditActivity)\n"
                        + "7. Save button: save JSON and finish()\n"
                        + "8. build_project");

                default:
                    return ok("Unknown template: " + template + "\n\n"
                        + "Available: calculator, todo_list, login_screen, splash_screen, notes");
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. ADD LOCALE STRINGS TOOL
    // ════════════════════════════════════════════════════════════════════════

    public static class AddLocaleStringsTool implements AgentTool {
        @Override public String getName() { return "add_locale_strings"; }

        @Override public String getDescription() {
            return "Adds a translated string resource to a Sketchware Pro project. "
                 + "Supported locales: ar (Arabic/RTL), fr (French), es (Spanish), "
                 + "de (German), tr (Turkish), hi (Hindi), ur (Urdu/RTL), zh (Chinese). "
                 + "Creates values-{locale}/strings.xml automatically.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "sc_id",       "string", "Project ID");
            addP(p, "locale",      "string", "Locale code: ar, fr, es, de, tr, hi, ur, zh");
            addP(p, "string_name", "string", "String resource name, e.g. 'app_name'");
            addP(p, "translation", "string", "Translated text value");
            s.add("properties", p);
            JsonArray r = new JsonArray();
            r.add("sc_id"); r.add("locale"); r.add("string_name"); r.add("translation");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId   = req(args, "sc_id");
            String locale = req(args, "locale");
            String name   = req(args, "string_name");
            String value  = req(args, "translation");
            if (scId == null || locale == null || name == null || value == null)
                return err("sc_id, locale, string_name and translation are required");
            if (!ctx.isProjectAllowed(scId)) return err("Access denied: project " + scId);

            String[] supported = {"ar","fr","es","de","tr","hi","ur","zh","ja","ko","pt","ru","it","nl"};
            boolean valid = false;
            for (String l : supported) if (l.equals(locale)) { valid = true; break; }
            if (!valid) return err("Unsupported locale: " + locale + ". Supported: ar,fr,es,de,tr,hi,ur,zh,...");

            ctx.reportProgress("Adding " + locale + " translation...", -1, true);

            File resDir = new File(ctx.getProjectResourceDir(scId), "values-" + locale);
            resDir.mkdirs();
            File stringsFile = new File(resDir, "strings.xml");

            String existing = "";
            if (stringsFile.exists()) {
                try {
                    StringBuilder sb = new StringBuilder();
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(stringsFile));
                    char[] buf = new char[4096]; int n;
                    while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
                    br.close();
                    existing = sb.toString();
                } catch (IOException ignored) {}
            }

            String escaped = value.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;").replace("\"", "&quot;");
            String entry = "    <string name=\"" + name + "\">" + escaped + "</string>\n";

            if (existing.contains("<resources>")) {
                existing = existing.replace("</resources>", entry + "</resources>");
            } else {
                boolean rtl = locale.equals("ar") || locale.equals("ur");
                existing = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + (rtl ? "<!-- RTL locale: " + locale + " -->\n" : "")
                        + "<resources>\n" + entry + "</resources>\n";
            }

            try { writeFile(stringsFile, existing); }
            catch (IOException e) { return err("Write failed: " + e.getMessage()); }

            boolean isRtl = locale.equals("ar") || locale.equals("ur");
            return ok("Translation added: " + locale + " / " + name + " = \"" + value + "\"\n"
                    + "File: values-" + locale + "/strings.xml\n"
                    + (isRtl ? "RTL: Add android:supportsRtl=\"true\" in AndroidManifest." : ""));
        }
    }
}
