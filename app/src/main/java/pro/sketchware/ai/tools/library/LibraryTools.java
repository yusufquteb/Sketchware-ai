package pro.sketchware.ai.tools.library;

import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.LocalLibrary;
import mod.hey.studios.build.BuildSettings;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.editor.manage.library.EnableBuiltInLibrariesActivity;
import mod.jbk.editor.manage.library.ExcludeBuiltInLibrariesActivity;
import mod.pranav.dependency.resolver.DependencyResolver;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.util.library.BuiltInLibraryCompatibilityMatrix;

public class LibraryTools {

    private static final Gson GSON = new Gson();

    private static ToolResult success(String output) {
        return ToolResult.success(null, output);
    }

    private static ToolResult error(String message) {
        return ToolResult.failure(null, message);
    }

    private static ToolResult validateProject(String scId, ToolContext context) {
        if (scId == null || scId.isEmpty()) {
            return error("sc_id is required");
        }
        if (!context.isProjectAllowed(scId)) {
            return error("Project not in workspace");
        }
        return null;
    }

    private static String readFile(File file) {
        if (!file.exists()) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            return sb.toString().trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeFile(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<HashMap<String, Object>> getAttachedLocalLibraries(String scId) {
        return LocalLibrariesUtil.getLocalLibraries(scId);
    }

    private static void saveAttachedLocalLibraries(String scId, ArrayList<HashMap<String, Object>> libs) {
        LocalLibrariesUtil.rewriteLocalLibFile(scId, GSON.toJson(libs));
    }

    private static boolean hasAttachedLibrary(ArrayList<HashMap<String, Object>> libs, String name) {
        for (HashMap<String, Object> entry : libs) {
            Object value = entry.get("name");
            if (value != null && name.equals(value.toString())) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject buildLocalLibraryEntry(HashMap<String, Object> entry) {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, Object> item : entry.entrySet()) {
            if (item.getValue() != null) {
                object.addProperty(item.getKey(), item.getValue().toString());
            }
        }
        return object;
    }

    public static class ListLibrariesTool implements AgentTool {
        @Override
        public String getName() {
            return "list_libraries";
        }

        @Override
        public String getDescription() {
            return "Lists built-in libraries, attached local libraries, and downloaded local libraries available to a project. "
                 + "Use compact=true to get a concise name-only summary (saves tokens). "
                 + "Use compact=false (default) for full details including sizes and enabled status.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            scId.addProperty("description", "The project SC ID");
            props.add("sc_id", scId);
            // Compact mode — returns only names/versions to save tokens
            JsonObject compact = new JsonObject();
            compact.addProperty("type", "boolean");
            compact.addProperty("description",
                "If true, returns a concise summary (names only, no sizes). "
              + "If false (default), returns full details. "
              + "Use compact=true when you only need to know what libraries are attached.");
            props.add("compact", compact);
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString() : null;
            ToolResult validation = validateProject(scId, context);
            if (validation != null) return validation;

            boolean compact = arguments.has("compact") && arguments.get("compact").getAsBoolean();

            // ── COMPACT MODE: return a concise name-only summary ──────────────────
            if (compact) {
                JsonObject summary = new JsonObject();
                summary.addProperty("sc_id", scId);
                summary.addProperty("mode", "compact");

                // Built-in library enabled flags (compat/firebase/admob/maps)
                File libraryFile = new File(context.getSketchwareDir(), "data/" + scId + "/library");
                JsonArray builtInNames = new JsonArray();
                if (libraryFile.exists()) {
                    String content = readFile(libraryFile);
                    if (content != null && !content.trim().isEmpty()) {
                        try {
                            JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
                            for (JsonElement el : arr) {
                                if (el.isJsonObject()) {
                                    JsonObject lib = el.getAsJsonObject();
                                    String name = lib.has("name") ? lib.get("name").getAsString() : "?";
                                    String useYn = lib.has("useYn") ? lib.get("useYn").getAsString() : "N";
                                    builtInNames.add(name + ":" + useYn);
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
                summary.add("built_in", builtInNames);

                // Manually enabled built-ins
                JsonArray manualNames = new JsonArray();
                for (BuiltInLibraries.BuiltInLibrary library : EnableBuiltInLibrariesActivity.getEnabledLibraries(scId)) {
                    manualNames.add(library.getName());
                }
                summary.add("manually_enabled", manualNames);

                // Attached local libraries — names only
                JsonArray attachedNames = new JsonArray();
                for (HashMap<String, Object> entry : getAttachedLocalLibraries(scId)) {
                    Object name = entry.get("name");
                    if (name != null) attachedNames.add(name.toString());
                }
                summary.add("attached_local", attachedNames);

                summary.addProperty("tip", "Use compact=false for full details including sizes.");
                return success(summary.toString());
            }

            // ── FULL MODE: return complete details ────────────────────────────────
            JsonObject result = new JsonObject();
            result.addProperty("sc_id", scId);

            File libraryFile = new File(context.getSketchwareDir(), "data/" + scId + "/library");
            if (libraryFile.exists()) {
                String content = readFile(libraryFile);
                if (content != null && !content.trim().isEmpty()) {
                    try {
                        result.add("built_in_libraries", JsonParser.parseString(content));
                    } catch (Exception e) {
                        result.addProperty("built_in_libraries_raw", content);
                    }
                }
            }

            JsonArray manuallyEnabledBuiltIns = new JsonArray();
            for (BuiltInLibraries.BuiltInLibrary library : EnableBuiltInLibrariesActivity.getEnabledLibraries(scId)) {
                manuallyEnabledBuiltIns.add(library.getName());
            }
            result.add("manually_enabled_built_in_libraries", manuallyEnabledBuiltIns);

            JsonArray excludedBuiltIns = new JsonArray();
            for (BuiltInLibraries.BuiltInLibrary library : ExcludeBuiltInLibrariesActivity.getExcludedLibraries(scId)) {
                excludedBuiltIns.add(library.getName());
            }
            result.add("excluded_built_in_libraries", excludedBuiltIns);

            JsonArray attachedLocalLibraries = new JsonArray();
            for (HashMap<String, Object> entry : getAttachedLocalLibraries(scId)) {
                attachedLocalLibraries.add(buildLocalLibraryEntry(entry));
            }
            result.add("attached_local_libraries", attachedLocalLibraries);

            JsonArray downloaded = new JsonArray();
            for (LocalLibrary library : LocalLibrariesUtil.getAllLocalLibraries()) {
                JsonObject item = new JsonObject();
                item.addProperty("name", library.getName());
                item.addProperty("size", library.getSize());
                item.addProperty("attached", hasAttachedLibrary(getAttachedLocalLibraries(scId), library.getName()));
                downloaded.add(item);
            }
            result.add("downloaded_local_libraries", downloaded);
            return success(result.toString());
        }
    }

    public static class AddLibraryTool implements AgentTool {
        @Override
        public String getName() {
            return "add_library";
        }

        @Override
        public String getDescription() {
            return "Adds or enables a built-in library for a project. Available: compat, material3, firebase, admob, googlemap.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            props.add("sc_id", scId);
            JsonObject libName = new JsonObject();
            libName.addProperty("type", "string");
            libName.addProperty("description", "Library name: compat, material3, firebase, admob, googlemap");
            props.add("library_name", libName);
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id");
            req.add("library_name");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString() : null;
            String libName = arguments.has("library_name") ? arguments.get("library_name").getAsString() : null;
            ToolResult validation = validateProject(scId, context);
            if (validation != null) return validation;
            if (libName == null || libName.isEmpty()) return error("library_name is required");

            File libraryFile = new File(context.getSketchwareDir(), "data/" + scId + "/library");
            JsonArray libraries;
            if (libraryFile.exists()) {
                String content = readFile(libraryFile);
                try {
                    libraries = (content != null && !content.isEmpty()) ? JsonParser.parseString(content).getAsJsonArray() : new JsonArray();
                } catch (Exception e) {
                    libraries = new JsonArray();
                }
            } else {
                libraries = new JsonArray();
            }

            for (JsonElement el : libraries) {
                if (el.isJsonObject() && el.getAsJsonObject().has("name") &&
                        el.getAsJsonObject().get("name").getAsString().equals(libName)) {
                    el.getAsJsonObject().addProperty("useYn", "Y");
                    try {
                        writeFile(libraryFile, libraries.toString());
                        return success("{\"library_name\":\"" + libName + "\",\"status\":\"enabled\"}");
                    } catch (IOException e) {
                        return error("Write failed: " + e.getMessage());
                    }
                }
            }

            JsonObject newLib = new JsonObject();
            newLib.addProperty("adUnits", "");
            newLib.addProperty("data", "");
            newLib.addProperty("libType", 0);
            newLib.addProperty("name", libName);
            newLib.addProperty("reserved1", "");
            newLib.addProperty("reserved2", "");
            newLib.addProperty("reserved3", "");
            newLib.addProperty("testDevices", "");
            newLib.addProperty("useYn", "Y");
            libraries.add(newLib);

            try {
                writeFile(libraryFile, libraries.toString());
                return success("{\"library_name\":\"" + libName + "\",\"status\":\"added\"}");
            } catch (IOException e) {
                return error("Write failed: " + e.getMessage());
            }
        }
    }

    public static class RemoveLibraryTool implements AgentTool {
        @Override
        public String getName() {
            return "remove_library";
        }

        @Override
        public String getDescription() {
            return "Disables a built-in library from a project.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            props.add("sc_id", scId);
            JsonObject libName = new JsonObject();
            libName.addProperty("type", "string");
            props.add("library_name", libName);
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id");
            req.add("library_name");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString() : null;
            String libName = arguments.has("library_name") ? arguments.get("library_name").getAsString() : null;
            ToolResult validation = validateProject(scId, context);
            if (validation != null) return validation;
            if (libName == null || libName.isEmpty()) return error("library_name is required");

            File libraryFile = new File(context.getSketchwareDir(), "data/" + scId + "/library");
            if (!libraryFile.exists()) return success("{\"library_name\":\"" + libName + "\",\"status\":\"not_configured\"}");

            String content = readFile(libraryFile);
            if (content == null || content.trim().isEmpty()) return success("{\"library_name\":\"" + libName + "\",\"status\":\"empty\"}");

            try {
                JsonArray libraries = JsonParser.parseString(content).getAsJsonArray();
                for (JsonElement el : libraries) {
                    if (el.isJsonObject() && el.getAsJsonObject().has("name") &&
                            el.getAsJsonObject().get("name").getAsString().equals(libName)) {
                        el.getAsJsonObject().addProperty("useYn", "N");
                        writeFile(libraryFile, libraries.toString());
                        return success("{\"library_name\":\"" + libName + "\",\"status\":\"disabled\"}");
                    }
                }
                return success("{\"library_name\":\"" + libName + "\",\"status\":\"not_found\"}");
            } catch (Exception e) {
                return error("Failed: " + e.getMessage());
            }
        }
    }

    public static class AttachLocalLibraryTool implements AgentTool {
        @Override
        public String getName() {
            return "attach_local_library";
        }

        @Override
        public String getDescription() {
            return "Attaches an already-downloaded local library to a project so it is included in builds.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            props.add("sc_id", scId);
            JsonObject name = new JsonObject();
            name.addProperty("type", "string");
            name.addProperty("description", "Downloaded local library directory name");
            props.add("library_name", name);
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id");
            req.add("library_name");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString() : null;
            String libraryName = arguments.has("library_name") ? arguments.get("library_name").getAsString() : null;
            ToolResult validation = validateProject(scId, context);
            if (validation != null) return validation;
            if (libraryName == null || libraryName.isEmpty()) return error("library_name is required");

            File libraryFolder = new File(context.getSketchwareDir(), "libs/local_libs/" + libraryName);
            if (!libraryFolder.exists() || !libraryFolder.isDirectory()) {
                return error("Downloaded local library not found: " + libraryName);
            }

            ArrayList<HashMap<String, Object>> attached = getAttachedLocalLibraries(scId);
            if (!hasAttachedLibrary(attached, libraryName)) {
                attached.add(LocalLibrariesUtil.createLibraryMap(libraryName, null));
                saveAttachedLocalLibraries(scId, attached);
            }

            JsonObject result = new JsonObject();
            result.addProperty("library_name", libraryName);
            result.addProperty("status", "attached");
            return success(result.toString());
        }
    }

    public static class DetachLocalLibraryTool implements AgentTool {
        @Override
        public String getName() {
            return "detach_local_library";
        }

        @Override
        public String getDescription() {
            return "Detaches a local library from the current project without deleting the downloaded library from storage.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            props.add("sc_id", scId);
            JsonObject name = new JsonObject();
            name.addProperty("type", "string");
            props.add("library_name", name);
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id");
            req.add("library_name");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString() : null;
            String libraryName = arguments.has("library_name") ? arguments.get("library_name").getAsString() : null;
            ToolResult validation = validateProject(scId, context);
            if (validation != null) return validation;
            if (libraryName == null || libraryName.isEmpty()) return error("library_name is required");

            ArrayList<HashMap<String, Object>> attached = getAttachedLocalLibraries(scId);
            attached.removeIf(entry -> {
                Object value = entry.get("name");
                return value != null && libraryName.equals(value.toString());
            });
            saveAttachedLocalLibraries(scId, attached);

            JsonObject result = new JsonObject();
            result.addProperty("library_name", libraryName);
            result.addProperty("status", "detached");
            return success(result.toString());
        }
    }

    public static class DownloadDependencyTool implements AgentTool {
        @Override
        public String getName() {
            return "download_dependency";
        }

        @Override
        public String getDescription() {
            return "Downloads a Maven dependency into Sketchware local libraries, dexes it, and attaches the resolved libraries to the project.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            props.add("sc_id", scId);
            JsonObject dependency = new JsonObject();
            dependency.addProperty("type", "string");
            dependency.addProperty("description", "Maven coordinate in group:artifact:version format");
            props.add("dependency", dependency);
            JsonObject includeTransitives = new JsonObject();
            includeTransitives.addProperty("type", "boolean");
            includeTransitives.addProperty("description", "Whether transitive dependencies should also be downloaded. Defaults to true.");
            props.add("include_transitives", includeTransitives);
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id");
            req.add("dependency");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString() : null;
            String dependency = arguments.has("dependency") ? arguments.get("dependency").getAsString() : null;
            boolean includeTransitives = !arguments.has("include_transitives") || arguments.get("include_transitives").getAsBoolean();
            ToolResult validation = validateProject(scId, context);
            if (validation != null) return validation;
            if (dependency == null || dependency.isEmpty()) return error("dependency is required");

            String[] parts = dependency.split(":");
            if (parts.length != 3) {
                return error("dependency must be in group:artifact:version format");
            }

            // ── Check if already downloaded ────────────────────────────────
            // The artifact folder name follows the pattern: group.artifact-version
            // We normalise it the same way DependencyResolver does.
            String artifactFolderName = parts[0].replace(".", "_") + "_" + parts[1] + "-" + parts[2];
            File existingLibDir = new File(context.getSketchwareDir(),
                    "libs/local_libs/" + artifactFolderName);

            ArrayList<HashMap<String, Object>> alreadyAttached = getAttachedLocalLibraries(scId);

            if (existingLibDir.exists() && existingLibDir.isDirectory()) {
                // Library already downloaded — just attach if not yet attached
                boolean wasAttached = hasAttachedLibrary(alreadyAttached, artifactFolderName);
                if (!wasAttached) {
                    alreadyAttached.add(LocalLibrariesUtil.createLibraryMap(artifactFolderName, dependency));
                    saveAttachedLocalLibraries(scId, alreadyAttached);
                }
                JsonObject result = new JsonObject();
                result.addProperty("dependency", dependency);
                result.addProperty("library_name", artifactFolderName);
                result.addProperty("status", wasAttached ? "already_attached" : "found_locally_and_attached");
                result.addProperty("message",
                        wasAttached
                        ? "Library already downloaded and attached. No download needed."
                        : "Library found in local storage and attached without re-downloading.");
                return success(result.toString());
            }

            // Also check by iterating all local libraries for a name-only match (version-agnostic)
            // FIX (Suggested.txt): Verify that the matched library's artifact ID actually matches
            // the requested artifact to avoid false-positive name matches (e.g. 'preference' matching
            // 'datastore-preferences-core' which has a completely different API surface).
            String artifactId = parts[1];
            for (LocalLibrary lib : LocalLibrariesUtil.getAllLocalLibraries()) {
                String libName = lib.getName();
                if (libName == null) continue;
                // Strict match: the local library name must start with or equal the artifact ID
                // (after normalising separators), not just contain it as a substring.
                String normLibName  = libName.toLowerCase().replace("-", "_").replace(".", "_");
                String normArtifact = artifactId.toLowerCase().replace("-", "_").replace(".", "_");
                boolean strictMatch = normLibName.startsWith(normArtifact + "-")
                        || normLibName.startsWith(normArtifact + "_")
                        || normLibName.equals(normArtifact)
                        || normLibName.startsWith(parts[0].replace(".", "_") + "_" + normArtifact);
                if (!strictMatch) continue;
                boolean wasAttached = hasAttachedLibrary(alreadyAttached, libName);
                if (!wasAttached) {
                    alreadyAttached.add(LocalLibrariesUtil.createLibraryMap(libName, dependency));
                    saveAttachedLocalLibraries(scId, alreadyAttached);
                }
                JsonObject result = new JsonObject();
                result.addProperty("dependency", dependency);
                result.addProperty("library_name", libName);
                result.addProperty("matched_artifact_id", artifactId);
                result.addProperty("status", wasAttached ? "already_attached" : "found_locally_and_attached");
                result.addProperty("message",
                        "Compatible local library '" + libName + "' already exists. " +
                        (wasAttached ? "Already attached." : "Attached without re-downloading."));
                return success(result.toString());
            }

            // Not found locally — proceed with download
            Set<String> resolvedLibraries = new LinkedHashSet<>();
            BuildSettings buildSettings = new BuildSettings(scId);
            context.reportProgress("Resolving dependency…", 5);

            try {
                DependencyResolver resolver = new DependencyResolver(parts[0], parts[1], parts[2], !includeTransitives, buildSettings);
                resolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                    @Override
                    public void onDownloadStart(org.cosmic.ide.dependency.resolver.api.Artifact artifact) {
                        context.reportProgress("Downloading " + artifact.getArtifactId() + ":" + artifact.getVersion() + "…", 20, true);
                    }

                    @Override
                    public void onDownloadEnd(org.cosmic.ide.dependency.resolver.api.Artifact artifact) {
                        context.reportProgress("Downloaded " + artifact.getArtifactId() + ":" + artifact.getVersion(), 45);
                    }

                    @Override
                    public void unzipping(org.cosmic.ide.dependency.resolver.api.Artifact artifact) {
                        context.reportProgress("Extracting " + artifact.getArtifactId() + "…", 60);
                    }

                    @Override
                    public void dexing(org.cosmic.ide.dependency.resolver.api.Artifact artifact) {
                        context.reportProgress("Dexing " + artifact.getArtifactId() + "…", 75);
                    }

                    @Override
                    public void onTaskCompleted(List<String> artifacts) {
                        resolvedLibraries.addAll(artifacts);
                        context.reportProgress("Dependency ready", 95);
                    }
                });

                if (context.isCancelled()) {
                    return error("Dependency download cancelled");
                }

                ArrayList<HashMap<String, Object>> attached = getAttachedLocalLibraries(scId);
                JsonArray attachedNow = new JsonArray();
                for (String libraryName : resolvedLibraries) {
                    if (!hasAttachedLibrary(attached, libraryName)) {
                        attached.add(LocalLibrariesUtil.createLibraryMap(libraryName, dependency));
                    }
                    attachedNow.add(libraryName);
                }
                saveAttachedLocalLibraries(scId, attached);

                JsonObject result = new JsonObject();
                result.addProperty("dependency", dependency);
                result.add("attached_libraries", attachedNow);
                result.addProperty("status", "downloaded_and_attached");
                result.addProperty("message", resolvedLibraries.isEmpty()
                        ? "No downloadable artifacts were resolved"
                        : "Dependency downloaded and attached successfully");
                return success(result.toString());
            } catch (Exception e) {
                return error("Failed to download dependency: " + e.getMessage());
            }
        }
    }

    public static class ValidateLibrariesTool implements AgentTool {
        @Override
        public String getName() {
            return "validate_libraries";
        }

        @Override
        public String getDescription() {
            return "Validates the project's built-in and local library configuration and returns dependency health details. "
                 + "Now includes granular error diagnosis: each error is cross-referenced with the library that caused it "
                 + "and a suggested fix action (e.g. 'enable compat', 'download_dependency ...'). "
                 + "Run this after build failures to get actionable remediation steps.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            scId.addProperty("description", "The project SC ID");
            props.add("sc_id", scId);
            // Optional: pass a build error message to get targeted diagnosis
            JsonObject buildError = new JsonObject();
            buildError.addProperty("type", "string");
            buildError.addProperty("description",
                "Optional: paste the build error message here to get targeted library diagnosis. "
              + "The tool will cross-reference the error with attached libraries and suggest fixes.");
            props.add("build_error", buildError);
            schema.add("properties", props);
            JsonArray required = new JsonArray();
            required.add("sc_id");
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString() : null;
            ToolResult validation = validateProject(scId, context);
            if (validation != null) return validation;

            String buildError = arguments.has("build_error") && !arguments.get("build_error").isJsonNull()
                    ? arguments.get("build_error").getAsString() : null;

            BuiltInLibraryCompatibilityMatrix.ValidationResult validationResult =
                    BuiltInLibraryCompatibilityMatrix.validate(scId);
            JsonObject result = new JsonObject();
            result.addProperty("sc_id", scId);
            result.addProperty("valid", validationResult.isValid());

            // ── Granular error diagnosis (Suggested.txt improvement) ──────────────
            // Each error is enriched with a 'fix' suggestion so the AI agent knows
            // exactly which tool call to make next without guessing.
            JsonArray enrichedErrors = new JsonArray();
            for (String errorMsg : validationResult.getErrors()) {
                JsonObject errObj = new JsonObject();
                errObj.addProperty("error", errorMsg);
                // Derive a fix suggestion based on the error text
                String fix = deriveFixSuggestion(errorMsg);
                if (fix != null) errObj.addProperty("suggested_fix", fix);
                enrichedErrors.add(errObj);
            }
            result.add("errors", enrichedErrors);

            JsonArray requiredLibraries = new JsonArray();
            for (String library : validationResult.getRequiredLibraries()) {
                requiredLibraries.add(library);
            }
            result.add("required_libraries", requiredLibraries);
            result.addProperty("attached_local_library_count", getAttachedLocalLibraries(scId).size());

            // ── Build error cross-reference (if provided) ─────────────────────────
            if (buildError != null && !buildError.trim().isEmpty()) {
                JsonObject diagnosis = diagnoseBuildError(buildError, scId, context);
                result.add("build_error_diagnosis", diagnosis);
            }

            return success(result.toString());
        }

        /**
         * Maps a known validation error message to a concrete fix action.
         * Returns null if no specific fix is known.
         */
        private static String deriveFixSuggestion(String errorMsg) {
            if (errorMsg == null) return null;
            String lower = errorMsg.toLowerCase();
            if (lower.contains("firebase") && lower.contains("appcompat"))
                return "Call add_library with library_name='compat' to enable AppCompat/Design.";
            if (lower.contains("material 3") && lower.contains("appcompat"))
                return "Call add_library with library_name='compat' to enable AppCompat/Design.";
            if (lower.contains("excluded") && lower.contains("required"))
                return "Remove the conflicting library from the exclusion list via ExcludeBuiltInLibrariesActivity, or disable the feature that requires it.";
            if (lower.contains("admob") || lower.contains("play_services_ads"))
                return "Call add_library with library_name='admob' to enable AdMob.";
            if (lower.contains("maps") || lower.contains("play_services_maps"))
                return "Call add_library with library_name='googlemap' to enable Google Maps.";
            return null;
        }

        /**
         * Cross-references a build error message with the project's attached libraries
         * to identify which library is likely missing or misconfigured.
         */
        private static JsonObject diagnoseBuildError(String buildError, String scId, ToolContext context) {
            JsonObject diag = new JsonObject();
            diag.addProperty("build_error_snippet",
                    buildError.length() > 300 ? buildError.substring(0, 300) + "..." : buildError);

            String lower = buildError.toLowerCase();
            List<String> suspects = new ArrayList<>();
            List<String> fixes    = new ArrayList<>();

            // Common build error patterns → library mapping
            if (lower.contains("cannot find symbol") || lower.contains("package does not exist")) {
                suspects.add("A required library class is missing from the classpath.");
                fixes.add("Run scan_dependencies to identify missing imports, then use download_dependency.");
            }
            if (lower.contains("duplicate class")) {
                suspects.add("Two attached libraries contain the same class (version conflict).");
                fixes.add("Use remove_library or detach_local_library to remove the duplicate, then re-attach the correct version.");
            }
            if (lower.contains("resource not found") || lower.contains("no resource identifier found")) {
                suspects.add("A resource (layout/drawable/string) referenced in code is missing.");
                fixes.add("Check that AppCompat/Design (compat) is enabled if using Material/AppCompat resources.");
            }
            if (lower.contains("minsdkversion") || lower.contains("minsdk")) {
                suspects.add("A library requires a higher minSdkVersion than the project.");
                fixes.add("Update minSdkVersion in build settings, or use a lower version of the library.");
            }
            if (lower.contains("multidex")) {
                suspects.add("Too many methods — MultiDex is required.");
                fixes.add("Enable MultiDex in build settings.");
            }

            // Cross-reference with attached libraries
            ArrayList<HashMap<String, Object>> attached = getAttachedLocalLibraries(scId);
            JsonArray attachedNames = new JsonArray();
            for (HashMap<String, Object> lib : attached) {
                Object name = lib.get("name");
                if (name != null) attachedNames.add(name.toString());
            }
            diag.add("currently_attached_libraries", attachedNames);

            JsonArray suspectsArr = new JsonArray();
            for (String s : suspects) suspectsArr.add(s);
            diag.add("suspected_causes", suspectsArr);

            JsonArray fixesArr = new JsonArray();
            for (String f : fixes) fixesArr.add(f);
            diag.add("suggested_fixes", fixesArr);

            if (suspects.isEmpty())
                diag.addProperty("note", "No known pattern matched. Review the full build log with get_compile_logs.");

            return diag;
        }
    }

}
