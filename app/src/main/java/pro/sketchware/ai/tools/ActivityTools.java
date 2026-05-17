package pro.sketchware.ai.tools;

import com.besome.sketch.beans.ProjectFileBean;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import a.a.a.jC;
import a.a.a.yq;
import pro.sketchware.activities.projecttools.ProjectToolPaths;
import pro.sketchware.ai.models.ToolResult;

public final class ActivityTools {

    private ActivityTools() {
    }

    private static ToolResult success(String output) {
        return new ToolResult(null, true, output, null);
    }

    private static ToolResult error(String message) {
        return new ToolResult(null, false, null, message);
    }

    /**
     * Raw File API: reads a file as UTF-8 text without format assumptions.
     * Handles BOM, XML comments, and any encoding issues gracefully.
     */
    private static String readFileContent(File file) throws IOException {
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
        // Strip BOM if present
        if (sb.length() > 0 && sb.charAt(0) == '\uFEFF') {
            sb.deleteCharAt(0);
        }
        return sb.toString();
    }

    private static void writeFileContent(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    /**
     * Reads the Sketchware activity/file data file using Raw File API.
     * Step 1: Read as raw text (no format assumption).
     * Step 2: Try JSON parse.
     * Step 3: Fallback to empty array on failure (never throws on content format).
     *
     * <p>This fixes the MalformedJsonException that occurred when the file contained
     * XML comments or non-JSON content.
     */
    private static JsonArray readActivityArray(File fileFile) throws IOException {
        if (!fileFile.exists()) {
            return new JsonArray();
        }
        // Step 1: Raw read
        String content = readFileContent(fileFile);
        if (content.trim().isEmpty()) {
            return new JsonArray();
        }
        // Step 2: Try JSON parse
        try {
            JsonElement element = JsonParser.parseString(content);
            if (element.isJsonArray()) {
                return element.getAsJsonArray();
            }
        } catch (JsonSyntaxException e) {
            // Step 3: Fallback – content is not JSON, return empty
            android.util.Log.w("ActivityTools",
                    "readActivityArray: file is not valid JSON, returning empty. File: "
                            + fileFile.getAbsolutePath() + ", error: " + e.getMessage());
        }
        return new JsonArray();
    }

    /**
     * Resolves the editable file data path via ProjectToolPaths.
     * Uses the Low-level File API path: .sketchware/data/{scId}/file
     */
    private static File getActivityDataFile(ToolContext context, String scId) {
        return new File(ProjectToolPaths.getProjectDataDir(scId), "file");
    }

    private static int calculateOptions(boolean hasToolbar, boolean fullscreen, boolean hasFab, boolean hasDrawer) {
        int options = 0;
        if (hasToolbar) options |= ProjectFileBean.OPTION_ACTIVITY_TOOLBAR;
        if (fullscreen) options |= ProjectFileBean.OPTION_ACTIVITY_FULLSCREEN;
        if (hasFab) options |= ProjectFileBean.OPTION_ACTIVITY_FAB;
        if (hasDrawer) options |= ProjectFileBean.OPTION_ACTIVITY_DRAWER;
        return options;
    }

    public static class ListActivitiesTool implements AgentTool {
        @Override
        public String getName() {
            return "list_activities";
        }

        @Override
        public String getDescription() {
            return "Lists activities and visual files registered in a Sketchware Pro project.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();
            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

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

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            try {
                ArrayList<ProjectFileBean> files = jC.b(scId).b();
                JsonArray result = new JsonArray();
                if (files != null) {
                    for (ProjectFileBean file : files) {
                        JsonObject entry = new JsonObject();
                        entry.addProperty("fileName", file.fileName);
                        entry.addProperty("xmlName", file.getXmlName());
                        entry.addProperty("javaName", file.getJavaName());
                        entry.addProperty("fileType", file.fileType);
                        entry.addProperty("orientation", file.orientation);
                        entry.addProperty("keyboardSetting", file.keyboardSetting);
                        entry.addProperty("options", file.options);
                        result.add(entry);
                    }
                }
                return success(result.toString());
            } catch (Throwable e) {
                return error("Failed to list activities: " + e.getMessage());
            }
        }
    }



    public static class GetScreenSourceTool implements AgentTool {
        @Override
        public String getName() {
            return "get_screen_source";
        }

        @Override
        public String getDescription() {
            return "Returns the generated Java and XML source for a Sketchware screen, including fragments and dialog fragments.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();
            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject screenProp = new JsonObject();
            screenProp.addProperty("type", "string");
            screenProp.addProperty("description", "The fileName of the screen, such as main, settings_fragment, sheet_bottomdialog_fragment");
            properties.add("screen_name", screenProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("screen_name");

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
            if (!arguments.has("screen_name") || arguments.get("screen_name").isJsonNull()) {
                return error("Missing required parameter: screen_name");
            }

            String scId = arguments.get("sc_id").getAsString();
            String screenName = arguments.get("screen_name").getAsString();
            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            try {
                ArrayList<ProjectFileBean> files = new ArrayList<>();
                if (jC.b(scId).b() != null) files.addAll(jC.b(scId).b());
                if (jC.b(scId).c() != null) files.addAll(jC.b(scId).c());
                ProjectFileBean target = null;
                for (ProjectFileBean file : files) {
                    if (screenName.equals(file.fileName) || screenName.equals(file.getJavaName()) || screenName.equals(file.getXmlName())) {
                        target = file;
                        break;
                    }
                }
                if (target == null) {
                    return error("Screen not found: " + screenName);
                }

                yq metadata = new yq(context.getAppContext(), scId);
                JsonObject result = new JsonObject();
                result.addProperty("sc_id", scId);
                result.addProperty("file_name", target.fileName);
                result.addProperty("file_type", target.fileType);
                result.addProperty("java_name", target.getJavaName());
                result.addProperty("xml_name", target.getXmlName());
                result.addProperty("java_source", metadata.getFileSrc(target.getJavaName(), jC.b(scId), jC.a(scId), jC.c(scId)));
                result.addProperty("xml_source", metadata.getFileSrc(target.getXmlName(), jC.b(scId), jC.a(scId), jC.c(scId)));
                return success(result.toString());
            } catch (Throwable e) {
                return error("Failed to get screen source: " + e.getMessage());
            }
        }
    }

    public static class CreateActivityTool implements AgentTool {
        @Override
        public String getName() {
            return "create_activity";
        }

        @Override
        public String getDescription() {
            return "Creates a new activity entry using Sketchware's project-file manager so the IDE can recognize it.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject nameProp = new JsonObject();
            nameProp.addProperty("type", "string");
            nameProp.addProperty("description", "Activity name (e.g., \"main\", \"settings\", \"about\")");
            properties.add("activity_name", nameProp);

            JsonObject orientProp = new JsonObject();
            orientProp.addProperty("type", "integer");
            orientProp.addProperty("description", "Screen orientation: 0=portrait, 1=landscape, 2=both (default: 0)");
            properties.add("orientation", orientProp);

            JsonObject kbProp = new JsonObject();
            kbProp.addProperty("type", "integer");
            kbProp.addProperty("description", "Keyboard setting: 0=unspecified, 1=visible, 2=hidden (default: 0)");
            properties.add("keyboard_setting", kbProp);

            JsonObject toolbarProp = new JsonObject();
            toolbarProp.addProperty("type", "boolean");
            toolbarProp.addProperty("description", "Whether the activity has a toolbar (default: true)");
            properties.add("has_toolbar", toolbarProp);

            JsonObject fullscreenProp = new JsonObject();
            fullscreenProp.addProperty("type", "boolean");
            fullscreenProp.addProperty("description", "Whether the activity is fullscreen (default: false)");
            properties.add("fullscreen", fullscreenProp);

            JsonObject fabProp = new JsonObject();
            fabProp.addProperty("type", "boolean");
            fabProp.addProperty("description", "Whether the activity has a floating action button (default: false)");
            properties.add("has_fab", fabProp);

            JsonObject drawerProp = new JsonObject();
            drawerProp.addProperty("type", "boolean");
            drawerProp.addProperty("description", "Whether the activity has a navigation drawer (default: false)");
            properties.add("has_drawer", drawerProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("activity_name");

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
            if (!arguments.has("activity_name") || arguments.get("activity_name").isJsonNull()) {
                return error("Missing required parameter: activity_name");
            }

            String scId = arguments.get("sc_id").getAsString();
            String activityName = arguments.get("activity_name").getAsString().trim();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }
            if (!activityName.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
                return error("Invalid activity name: must start with a letter and contain only letters, digits, and underscores");
            }

            int orientation = arguments.has("orientation") && !arguments.get("orientation").isJsonNull()
                    ? arguments.get("orientation").getAsInt() : ProjectFileBean.ORIENTATION_PORTRAIT;
            int keyboardSetting = arguments.has("keyboard_setting") && !arguments.get("keyboard_setting").isJsonNull()
                    ? arguments.get("keyboard_setting").getAsInt() : ProjectFileBean.KEYBOARD_STATE_UNSPECIFIED;
            boolean hasToolbar = !arguments.has("has_toolbar") || arguments.get("has_toolbar").isJsonNull()
                    || arguments.get("has_toolbar").getAsBoolean();
            boolean fullscreen = arguments.has("fullscreen") && !arguments.get("fullscreen").isJsonNull()
                    && arguments.get("fullscreen").getAsBoolean();
            boolean hasFab = arguments.has("has_fab") && !arguments.get("has_fab").isJsonNull()
                    && arguments.get("has_fab").getAsBoolean();
            boolean hasDrawer = arguments.has("has_drawer") && !arguments.get("has_drawer").isJsonNull()
                    && arguments.get("has_drawer").getAsBoolean();

            try {
                ArrayList<ProjectFileBean> files = jC.b(scId).b();
                if (files != null) {
                    for (ProjectFileBean file : files) {
                        if (activityName.equals(file.fileName)) {
                            return error("Activity already exists: " + activityName);
                        }
                    }
                }

                ProjectFileBean fileBean = new ProjectFileBean(
                        ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY,
                        activityName,
                        orientation,
                        keyboardSetting,
                        calculateOptions(hasToolbar, fullscreen, hasFab, hasDrawer)
                );
                jC.b(scId).a(fileBean);
                jC.b(scId).j();
                jC.b(scId).l();

                JsonObject result = new JsonObject();
                result.addProperty("sc_id", scId);
                result.addProperty("activity_name", activityName);
                result.addProperty("xml_name", fileBean.getXmlName());
                result.addProperty("java_name", fileBean.getJavaName());
                result.addProperty("message", "Activity created successfully");
                return success(result.toString());
            } catch (Throwable e) {
                return error("Failed to create activity: " + e.getMessage());
            }
        }
    }

    public static class DeleteActivityTool implements AgentTool {
        @Override
        public String getName() {
            return "delete_activity";
        }

        @Override
        public String getDescription() {
            return "Deletes an activity entry from a Sketchware project.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();
            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject activityProp = new JsonObject();
            activityProp.addProperty("type", "string");
            activityProp.addProperty("description", "The activity file name");
            properties.add("activity_name", activityProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("activity_name");

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
            if (!arguments.has("activity_name") || arguments.get("activity_name").isJsonNull()) {
                return error("Missing required parameter: activity_name");
            }

            String scId = arguments.get("sc_id").getAsString();
            String activityName = arguments.get("activity_name").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }
            if ("main".equals(activityName)) {
                return error("The main activity cannot be deleted");
            }

            File dataDir = context.getProjectDataDir(scId);
            File fileFile = new File(dataDir, "file");

            try {
                JsonArray activities = readActivityArray(fileFile);
                boolean found = false;
                JsonArray updated = new JsonArray();

                for (JsonElement element : activities) {
                    if (element.isJsonObject()) {
                        JsonObject activity = element.getAsJsonObject();
                        if (activity.has("fileName")
                                && activity.get("fileName").getAsString().equals(activityName)) {
                            found = true;
                            continue;
                        }
                    }
                    updated.add(element);
                }

                if (!found) {
                    return error("Activity not found: " + activityName);
                }

                writeFileContent(fileFile, updated.toString());

                File logicFile = new File(dataDir, "logic");
                if (logicFile.exists()) {
                    try {
                        String logicContent = readFileContent(logicFile);
                        if (!logicContent.trim().isEmpty()) {
                            JsonArray logicArray = JsonParser.parseString(logicContent).getAsJsonArray();
                            JsonArray updatedLogic = new JsonArray();
                            String prefix = activityName + ".java_";
                            for (JsonElement element : logicArray) {
                                if (element.isJsonObject()) {
                                    JsonObject logicEntry = element.getAsJsonObject();
                                    if (logicEntry.has("name")
                                            && logicEntry.get("name").getAsString().startsWith(prefix)) {
                                        continue;
                                    }
                                }
                                updatedLogic.add(element);
                            }
                            writeFileContent(logicFile, updatedLogic.toString());
                        }
                    } catch (JsonSyntaxException ignored) {
                    }
                }

                File viewFile = new File(dataDir, "view");
                if (viewFile.exists()) {
                    try {
                        String viewContent = readFileContent(viewFile);
                        if (!viewContent.trim().isEmpty()) {
                            JsonArray viewArray = JsonParser.parseString(viewContent).getAsJsonArray();
                            JsonArray updatedView = new JsonArray();
                            String viewId = activityName + ".xml";
                            for (JsonElement element : viewArray) {
                                if (element.isJsonObject()) {
                                    JsonObject viewEntry = element.getAsJsonObject();
                                    if (viewEntry.has("id")
                                            && viewEntry.get("id").getAsString().equals(viewId)) {
                                        continue;
                                    }
                                }
                                updatedView.add(element);
                            }
                            writeFileContent(viewFile, updatedView.toString());
                        }
                    } catch (JsonSyntaxException ignored) {
                    }
                }

                JsonObject result = new JsonObject();
                result.addProperty("activity_name", activityName);
                result.addProperty("message", "Activity deleted successfully");
                return success(result.toString());
            } catch (IOException e) {
                return error("Failed to delete activity: " + e.getMessage());
            }
        }
    }
}
