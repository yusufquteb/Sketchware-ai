package pro.sketchware.ai.tools.project;

import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;

import static mod.hey.studios.util.ProjectFile.COLOR_ACCENT;
import static mod.hey.studios.util.ProjectFile.COLOR_CONTROL_HIGHLIGHT;
import static mod.hey.studios.util.ProjectFile.COLOR_CONTROL_NORMAL;
import static mod.hey.studios.util.ProjectFile.COLOR_PRIMARY;
import static mod.hey.studios.util.ProjectFile.COLOR_PRIMARY_DARK;
import static mod.hey.studios.util.ProjectFile.getDefaultColor;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import a.a.a.GB;
import a.a.a.lC;
import a.a.a.oB;
import a.a.a.wq;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.ai.engine.risk.RiskLevel;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.models.Workspace;
import pro.sketchware.ai.storage.WorkspaceManager;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public final class ProjectTools {

    private static final Gson GSON = new Gson();

    private ProjectTools() {
    }

    private static File getSketchwareDir() {
        return new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware");
    }

    private static File getDataDir() {
        return new File(getSketchwareDir(), "data");
    }

    private static File getMyscListDir() {
        return new File(getSketchwareDir(), "mysc" + File.separator + "list");
    }

    private static File getMyscDir() {
        return new File(getSketchwareDir(), "mysc");
    }

    private static void copyDirectory(File source, File target) throws IOException {
        if (!target.exists()) {
            target.mkdirs();
        }
        File[] files = source.listFiles();
        if (files == null) return;
        for (File file : files) {
            File dest = new File(target, file.getName());
            if (file.isDirectory()) {
                copyDirectory(file, dest);
            } else {
                copyFile(file, dest);
            }
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    private static String generateNewScId() {
        return lC.b();
    }

    private static ToolResult success(String output) {
        return new ToolResult(null, true, output, null);
    }

    private static ToolResult error(String message) {
        return new ToolResult(null, false, null, message);
    }

    private static void ensureProjectDirectories(String scId) {
        FilePathUtil filePathUtil = new FilePathUtil();
        FileUtil.makeDir(wq.b(scId));
        FileUtil.makeDir(filePathUtil.getPathJava(scId));
        FileUtil.makeDir(filePathUtil.getPathResource(scId));
        FileUtil.makeDir(filePathUtil.getPathResource(scId) + File.separator + "layout");
        FileUtil.makeDir(filePathUtil.getPathResource(scId) + File.separator + "values");
        FileUtil.makeDir(filePathUtil.getPathAssets(scId));
        FileUtil.makeDir(filePathUtil.getPathNativelibs(scId));
        FileUtil.makeDir(wq.b(scId) + File.separator + "files" + File.separator + "classpath");
    }

    private static void seedDefaultResources(String scId, String appName) {
        FilePathUtil filePathUtil = new FilePathUtil();
        String valuesDir = filePathUtil.getPathResource(scId) + File.separator + "values";
        FileUtil.makeDir(valuesDir);
        FileUtil.writeFile(valuesDir + File.separator + "strings.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n    <string name=\"app_name\">"
                        + escapeXml(appName) + "</string>\n</resources>\n");
        FileUtil.writeFile(valuesDir + File.separator + "colors.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n"
                        + "    <color name=\"colorPrimary\">#2196F3</color>\n"
                        + "    <color name=\"colorPrimaryDark\">#1976D2</color>\n"
                        + "    <color name=\"colorAccent\">#2196F3</color>\n"
                        + "</resources>\n");
        FileUtil.writeFile(filePathUtil.getPathResource(scId) + File.separator + "layout" + File.separator + "main.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    android:layout_width=\"match_parent\"\n"
                        + "    android:layout_height=\"match_parent\"\n"
                        + "    android:gravity=\"center\"\n"
                        + "    android:orientation=\"vertical\"\n"
                        + "    android:padding=\"16dp\">\n\n"
                        + "    <TextView\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Hello from Sketchware AI\"\n"
                        + "        android:textSize=\"20sp\" />\n\n"
                        + "</LinearLayout>\n");
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static HashMap<String, Object> createProjectMetadata(ToolContext context, String scId,
                                                                 String projectName, String packageName,
                                                                 String appName, String versionCode,
                                                                 String versionName) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("sc_id", scId);
        data.put("proj_type", 1);
        data.put("my_sc_pkg_name", packageName);
        data.put("my_ws_name", sanitizeProjectName(projectName));
        data.put("my_app_name", TextUtils.isEmpty(appName) ? projectName : appName);
        data.put("my_sc_reg_dt", new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date()));
        data.put("custom_icon", false);
        data.put("isIconAdaptive", false);
        data.put("sc_ver_code", versionCode);
        data.put("sc_ver_name", versionName);
        data.put("sketchware_ver", GB.d(context.getAppContext()));
        data.put(COLOR_ACCENT, getDefaultColor(COLOR_ACCENT));
        data.put(COLOR_PRIMARY, getDefaultColor(COLOR_PRIMARY));
        data.put(COLOR_PRIMARY_DARK, getDefaultColor(COLOR_PRIMARY_DARK));
        data.put(COLOR_CONTROL_HIGHLIGHT, getDefaultColor(COLOR_CONTROL_HIGHLIGHT));
        data.put(COLOR_CONTROL_NORMAL, getDefaultColor(COLOR_CONTROL_NORMAL));
        return data;
    }

    private static void addProjectToWorkspace(ToolContext context, String scId) {
        if (scId == null || scId.isEmpty()) {
            return;
        }
        context.addAllowedProjectId(scId);
        if (context.getWorkspaceId() == null || context.getWorkspaceId().isEmpty()) {
            return;
        }
        WorkspaceManager manager = new WorkspaceManager(context.getAppContext());
        Workspace workspace = manager.getWorkspace(context.getWorkspaceId());
        if (workspace != null && !workspace.hasProject(scId)) {
            workspace.addProject(scId);
            manager.updateWorkspace(workspace);
        }
    }

    private static void removeProjectFromWorkspace(ToolContext context, String scId) {
        if (scId == null || scId.isEmpty()) {
            return;
        }
        context.removeAllowedProjectId(scId);
        if (context.getWorkspaceId() == null || context.getWorkspaceId().isEmpty()) {
            return;
        }
        WorkspaceManager manager = new WorkspaceManager(context.getAppContext());
        Workspace workspace = manager.getWorkspace(context.getWorkspaceId());
        if (workspace != null && workspace.hasProject(scId)) {
            workspace.removeProject(scId);
            manager.updateWorkspace(workspace);
        }
    }

    private static String sanitizeProjectName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "NewProject";
        }
        value = value.replaceAll("[^A-Za-z0-9 _.-]", " ").trim();
        return value.isEmpty() ? "NewProject" : value;
    }

    private static boolean isValidPackageName(String packageName) {
        return packageName != null && packageName.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+");
    }

    public static class ListProjectsTool implements AgentTool {
        @Override
        public String getName() {
            return "list_projects";
        }

        @Override
        public String getDescription() {
            return "Lists all Sketchware Pro projects accessible in the current workspace. "
                    + "Returns project SC IDs, names, package names, and version names.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", new JsonObject());
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            try {
                JsonArray projects = new JsonArray();
                List<HashMap<String, Object>> allProjects = lC.a();
                for (HashMap<String, Object> data : allProjects) {
                    String scId = String.valueOf(data.get("sc_id"));
                    if (!context.isProjectAllowed(scId)) continue;

                    JsonObject entry = new JsonObject();
                    entry.addProperty("sc_id", scId);
                    entry.addProperty("name", String.valueOf(data.get("my_app_name")));
                    entry.addProperty("workspace_name", String.valueOf(data.get("my_ws_name")));
                    entry.addProperty("package_name", String.valueOf(data.get("my_sc_pkg_name")));
                    entry.addProperty("version_name", String.valueOf(data.get("sc_ver_name")));
                    entry.addProperty("version_code", String.valueOf(data.get("sc_ver_code")));
                    projects.add(entry);
                }
                return success(projects.toString());
            } catch (Exception e) {
                return error("Failed to list projects: " + e.getMessage());
            }
        }
    }

    public static class GetProjectInfoTool implements AgentTool {
        @Override
        public String getName() {
            return "get_project_info";
        }

        @Override
        public String getDescription() {
            return "Gets detailed metadata about a Sketchware Pro project including app name, "
                    + "package name, version info, configuration, and file-system readiness.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();
            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID (e.g., \"601\")");
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
                HashMap<String, Object> data = lC.b(scId);
                if (data == null) {
                    return error("Project not found: " + scId);
                }

                JsonObject result = GSON.toJsonTree(data).getAsJsonObject();
                result.addProperty("data_dir_exists", new File(wq.b(scId)).exists());
                result.addProperty("mysc_dir_exists", new File(wq.d(scId)).exists());
                result.addProperty("mysc_list_dir_exists", new File(wq.c(scId)).exists());
                result.addProperty("project_config_exists", new File(wq.b(scId), "project_config").exists());
                return success(result.toString());
            } catch (Exception e) {
                return error("Failed to read project info: " + e.getMessage());
            }
        }
    }

    public static class CreateProjectTool implements AgentTool {
        @Override
        public String getName() {
            return "create_project";
        }

        @Override
        public RiskLevel getRiskLevel() { return RiskLevel.MEDIUM; }

        @Override
        public String getDescription() {
            return "Creates a new Sketchware Pro project using the real project bootstrap flow, "
                    + "including metadata, storage directories, resources, and default project settings.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject appNameProp = new JsonObject();
            appNameProp.addProperty("type", "string");
            appNameProp.addProperty("description", "The display name of the app");
            properties.add("app_name", appNameProp);

            JsonObject pkgNameProp = new JsonObject();
            pkgNameProp.addProperty("type", "string");
            pkgNameProp.addProperty("description", "The Java package name (e.g., \"com.example.myapp\")");
            properties.add("package_name", pkgNameProp);

            JsonObject projNameProp = new JsonObject();
            projNameProp.addProperty("type", "string");
            projNameProp.addProperty("description", "The internal project/workspace name. Defaults to app_name if not specified.");
            properties.add("project_name", projNameProp);

            JsonObject verNameProp = new JsonObject();
            verNameProp.addProperty("type", "string");
            verNameProp.addProperty("description", "Version name string (default: \"1.0\")");
            properties.add("version_name", verNameProp);

            JsonObject verCodeProp = new JsonObject();
            verCodeProp.addProperty("type", "integer");
            verCodeProp.addProperty("description", "Version code number (default: 1)");
            properties.add("version_code", verCodeProp);

            JsonArray required = new JsonArray();
            required.add("app_name");
            required.add("package_name");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("app_name") || arguments.get("app_name").isJsonNull()) {
                return error("Missing required parameter: app_name");
            }
            if (!arguments.has("package_name") || arguments.get("package_name").isJsonNull()) {
                return error("Missing required parameter: package_name");
            }

            String appName = arguments.get("app_name").getAsString().trim();
            String packageName = arguments.get("package_name").getAsString().trim();
            String projectName = arguments.has("project_name") && !arguments.get("project_name").isJsonNull()
                    ? arguments.get("project_name").getAsString().trim() : appName;
            String versionName = arguments.has("version_name") && !arguments.get("version_name").isJsonNull()
                    ? arguments.get("version_name").getAsString().trim() : "1.0";
            String versionCode = arguments.has("version_code") && !arguments.get("version_code").isJsonNull()
                    ? String.valueOf(arguments.get("version_code").getAsInt()) : "1";

            if (appName.isEmpty()) {
                return error("app_name must not be empty");
            }
            if (!isValidPackageName(packageName)) {
                return error("Invalid package_name. Use a Java-style package such as com.example.app");
            }
            if (lC.a(packageName) != null) {
                return error("A project with package " + packageName + " already exists");
            }

            try {
                String scId = generateNewScId();
                HashMap<String, Object> metadata = createProjectMetadata(
                        context, scId, projectName, packageName, appName, versionCode, versionName);

                lC.a(scId, metadata);
                wq.a(context.getAppContext(), scId);
                new oB().b(wq.b(scId));
                ensureProjectDirectories(scId);
                seedDefaultResources(scId, appName);

                ProjectSettings projectSettings = new ProjectSettings(scId);
                projectSettings.setValue(ProjectSettings.SETTING_NEW_XML_COMMAND, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
                projectSettings.setValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, ProjectSettings.SETTING_GENERIC_VALUE_FALSE);

                addProjectToWorkspace(context, scId);

                JsonObject result = new JsonObject();
                result.addProperty("sc_id", scId);
                result.addProperty("app_name", appName);
                result.addProperty("package_name", packageName);
                result.addProperty("workspace_name", sanitizeProjectName(projectName));
                result.addProperty("message", "Project created successfully");
                return success(result.toString());
            } catch (Throwable e) {
                return error("Failed to create project: " + e.getMessage());
            }
        }
    }

    public static class DeleteProjectTool implements AgentTool {
        @Override
        public String getName() {
            return "delete_project";
        }

        @Override
        public RiskLevel getRiskLevel() { return RiskLevel.CRITICAL; }

        @Override
        public String getDescription() {
            return "Deletes a Sketchware Pro project and all its associated files. This action cannot be undone.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();
            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID to delete");
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

            File dataDir = context.getProjectDataDir(scId);
            if (!dataDir.exists()) {
                return error("Project not found: " + scId);
            }

            try {
                boolean allDeleted = true;
                if (dataDir.exists()) {
                    allDeleted &= deleteRecursive(dataDir);
                }

                File myscListDir = context.getProjectMyscListDir(scId);
                if (myscListDir.exists()) {
                    allDeleted &= deleteRecursive(myscListDir);
                }

                File myscDir = context.getProjectMyscDir(scId);
                if (myscDir.exists()) {
                    allDeleted &= deleteRecursive(myscDir);
                }

                File bakDir = context.getProjectBackupDir(scId);
                if (bakDir.exists()) {
                    allDeleted &= deleteRecursive(bakDir);
                }

                removeProjectFromWorkspace(context, scId);

                if (allDeleted) {
                    JsonObject result = new JsonObject();
                    result.addProperty("sc_id", scId);
                    result.addProperty("message", "Project deleted successfully");
                    return success(result.toString());
                } else {
                    return error("Some project files could not be deleted for project: " + scId);
                }
            } catch (Exception e) {
                return error("Failed to delete project: " + e.getMessage());
            }
        }
    }

    public static class DuplicateProjectTool implements AgentTool {
        @Override
        public String getName() {
            return "duplicate_project";
        }

        @Override
        public RiskLevel getRiskLevel() { return RiskLevel.MEDIUM; }

        @Override
        public String getDescription() {
            return "Duplicates an existing Sketchware Pro project with a new SC ID and preserves its storage structure.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();
            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The SC ID of the project to duplicate");
            properties.add("sc_id", scIdProp);

            JsonObject newNameProp = new JsonObject();
            newNameProp.addProperty("type", "string");
            newNameProp.addProperty("description", "New app name for the duplicated project. If not specified, the original name is kept.");
            properties.add("new_app_name", newNameProp);

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
            String sourceScId = arguments.get("sc_id").getAsString();
            String newAppName = arguments.has("new_app_name") && !arguments.get("new_app_name").isJsonNull()
                    ? arguments.get("new_app_name").getAsString().trim() : null;

            if (!context.isProjectAllowed(sourceScId)) {
                return error("Access denied: project " + sourceScId + " is not in the current workspace");
            }

            File sourceDataDir = context.getProjectDataDir(sourceScId);
            if (!sourceDataDir.exists()) {
                return error("Source project not found: " + sourceScId);
            }

            try {
                String newScId = generateNewScId();

                File newDataDir = new File(getDataDir(), newScId);
                copyDirectory(sourceDataDir, newDataDir);

                File sourceMyscListDir = context.getProjectMyscListDir(sourceScId);
                if (sourceMyscListDir.exists()) {
                    File newMyscListDir = new File(getMyscListDir(), newScId);
                    copyDirectory(sourceMyscListDir, newMyscListDir);
                }

                File sourceMyscDir = context.getProjectMyscDir(sourceScId);
                if (sourceMyscDir.exists()) {
                    File newMyscDir = new File(getMyscDir(), newScId);
                    copyDirectory(sourceMyscDir, newMyscDir);
                }

                HashMap<String, Object> metadata = lC.b(sourceScId);
                if (metadata == null) {
                    return error("Failed to load source project metadata");
                }
                metadata.put("sc_id", newScId);
                metadata.put("my_sc_reg_dt", new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date()));
                if (newAppName != null && !newAppName.isEmpty()) {
                    metadata.put("my_app_name", newAppName);
                    metadata.put("my_ws_name", sanitizeProjectName(newAppName));
                }
                lC.a(newScId, metadata);
                addProjectToWorkspace(context, newScId);

                JsonObject result = new JsonObject();
                result.addProperty("sc_id", newScId);
                result.addProperty("source_sc_id", sourceScId);
                result.addProperty("message", "Project duplicated successfully");
                return success(result.toString());
            } catch (Throwable e) {
                return error("Failed to duplicate project: " + e.getMessage());
            }
        }
    }
    
    

    public static class AddPermissionTool implements AgentTool {
        @Override public String getName() { return "add_permission"; }
        @Override public String getDescription() { return "Adds a permission to the project's AndroidManifest.xml (e.g., android.permission.INTERNET)."; }
        @Override public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject(); scId.addProperty("type", "string"); scId.addProperty("description", "Project SC ID");
            JsonObject perm = new JsonObject(); perm.addProperty("type", "string"); perm.addProperty("description", "Permission string");
            props.add("sc_id", scId); props.add("permission", perm);
            JsonArray req = new JsonArray(); req.add("sc_id"); req.add("permission");
            JsonObject schema = new JsonObject(); schema.addProperty("type", "object"); schema.add("properties", props); schema.add("required", req);
            return schema;
        }
        @Override
        public ToolResult execute(JsonObject args, ToolContext context) {
            String scId = args.get("sc_id").getAsString();
            String perm = args.get("permission").getAsString();
            if (!context.isProjectAllowed(scId)) return error("Access denied");
            try {
                java.io.File file = new java.io.File(context.getProjectDataDir(scId), "manifest" + java.io.File.separator + "raw_override.xml");
                String content = pro.sketchware.utility.FileUtil.readFileIfExist(file.getAbsolutePath());
                if (content.contains(perm)) return success("Permission already exists");
                int idx = content.lastIndexOf("</manifest>");
                if (idx == -1) return error("Manifest error");
                String newContent = content.substring(0, idx) + "    <uses-permission android:name=\"" + perm + "\" />\n" + content.substring(idx);
                pro.sketchware.utility.FileUtil.writeFile(file.getAbsolutePath(), newContent);
                return success("Permission added: " + perm);
            } catch (Exception e) { return error(e.getMessage()); }
        }
    }

    public static class AddActivityTool implements AgentTool {
        @Override public String getName() { return "add_activity"; }
        @Override public String getDescription() { return "Adds a new activity to the project's AndroidManifest.xml."; }
        @Override public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject(); scId.addProperty("type", "string");
            JsonObject act = new JsonObject(); act.addProperty("type", "string");
            props.add("sc_id", scId); props.add("activity_name", act);
            JsonArray req = new JsonArray(); req.add("sc_id"); req.add("activity_name");
            JsonObject schema = new JsonObject(); schema.addProperty("type", "object"); schema.add("properties", props); schema.add("required", req);
            return schema;
        }
        @Override
        public ToolResult execute(JsonObject args, ToolContext context) {
            String scId = args.get("sc_id").getAsString();
            String name = args.get("activity_name").getAsString();
            if (!context.isProjectAllowed(scId)) return error("Access denied");
            try {
                java.io.File file = new java.io.File(context.getProjectDataDir(scId), "manifest" + java.io.File.separator + "raw_override.xml");
                String content = pro.sketchware.utility.FileUtil.readFileIfExist(file.getAbsolutePath());
                String tag = "    <activity android:name=\"" + name + "\" android:exported=\"false\" />\n";
                int idx = content.lastIndexOf("</application>");
                if (idx == -1) return error("Manifest error");
                String newContent = content.substring(0, idx) + tag + content.substring(idx);
                pro.sketchware.utility.FileUtil.writeFile(file.getAbsolutePath(), newContent);
                return success("Activity added: " + name);
            } catch (Exception e) { return error(e.getMessage()); }
        }
    }

}
