package pro.sketchware.ai.manifest;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AiCapabilityManifest — The AI's self-awareness registry.
 *
 * <p>This class acts as the AI's "manifest" — a structured, queryable list of every
 * tool and capability available to it within Sketchware Pro. When the AI starts a
 * session, it reads this manifest to know exactly what it can do.
 *
 * <h2>Why this exists</h2>
 * <p>Without a manifest, the AI often doesn't know which capabilities are wired up
 * inside the IDE. This class provides:
 * <ul>
 *   <li>A categorised, human-readable list for display in the chat UI.</li>
 *   <li>A machine-readable system prompt injection for the AI model.</li>
 *   <li>A runtime lookup for tool availability checks.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Get system prompt injection
 * String injection = AiCapabilityManifest.buildSystemPromptInjection();
 *
 * // Get all tools in a category for display
 * List<ToolEntry> designTools = AiCapabilityManifest.getToolsByCategory("UI Layout");
 * }</pre>
 */
public final class AiCapabilityManifest {

    // ── Tool categories (order matters for display) ────────────────────────────
    public static final String CAT_PROJECT    = "Project Management";
    public static final String CAT_FILES      = "File Operations";
    public static final String CAT_ACTIVITIES = "Activities & Screens";
    public static final String CAT_UI         = "UI Layout & Design";
    public static final String CAT_LOGIC      = "Block Logic";
    public static final String CAT_RESOURCES  = "Resources";
    public static final String CAT_LIBRARIES  = "Library Management";
    public static final String CAT_BUILD      = "Build & Compile";
    public static final String CAT_AI_TOOLS   = "AI-Powered Tools";
    public static final String CAT_SEARCH     = "Search & Analysis";
    public static final String CAT_EXPORT     = "Export";

    // ── Singleton registry ─────────────────────────────────────────────────────
    private static final Map<String, List<ToolEntry>> REGISTRY;

    static {
        REGISTRY = new LinkedHashMap<>();

        // ── Project Management ─────────────────────────────────────────────────
        List<ToolEntry> projects = new ArrayList<>();
        projects.add(new ToolEntry("list_projects",         "List all projects in the workspace",              CAT_PROJECT, "ic_mtrl_folder_open"));
        projects.add(new ToolEntry("check_project_health", "Comprehensive project audit in one call",         CAT_PROJECT, "ic_mtrl_health_and_safety"));
        projects.add(new ToolEntry("get_project_info",    "Get a project's name, package, and version",      CAT_PROJECT,    "ic_mtrl_info"));
        projects.add(new ToolEntry("index_project",       "Build a compact symbol index (views, vars, events, components)", CAT_PROJECT, "ic_mtrl_account_tree"));
        projects.add(new ToolEntry("create_project",      "Create a brand-new Sketchware project",           CAT_PROJECT,    "ic_mtrl_add_circle"));
        projects.add(new ToolEntry("delete_project",      "Delete a project (requires confirmation)",        CAT_PROJECT,    "ic_mtrl_delete"));
        projects.add(new ToolEntry("duplicate_project",   "Clone an existing project",                       CAT_PROJECT,    "ic_mtrl_file_copy"));
        REGISTRY.put(CAT_PROJECT, projects);

        // ── File Operations ────────────────────────────────────────────────────
        List<ToolEntry> files = new ArrayList<>();
        files.add(new ToolEntry("read_file",          "Read any project file",                          CAT_FILES, "ic_mtrl_description"));
        files.add(new ToolEntry("write_file",         "Write or overwrite a file",                      CAT_FILES, "ic_mtrl_edit_document"));
        files.add(new ToolEntry("delete_file",        "Delete a file",                                  CAT_FILES, "ic_mtrl_delete"));
        files.add(new ToolEntry("list_files",         "List files in a directory",                      CAT_FILES, "ic_mtrl_folder_open"));
        files.add(new ToolEntry("copy_file",          "Copy a file within or between projects",         CAT_FILES, "ic_mtrl_file_copy"));
        files.add(new ToolEntry("move_file",          "Move or rename a file",                          CAT_FILES, "ic_mtrl_drive_file_move"));
        files.add(new ToolEntry("patch_file",         "Apply a surgical diff/patch to a file",          CAT_FILES, "ic_mtrl_deployed_code"));
        files.add(new ToolEntry("append_code",        "Append code to the end of a file",               CAT_FILES, "ic_mtrl_text_add"));
        files.add(new ToolEntry("insert_code_at_line","Insert code at a specific line number",          CAT_FILES, "ic_mtrl_add_box"));
        files.add(new ToolEntry("read_file_range",    "Read specific lines from a file",                CAT_FILES, "ic_mtrl_format_align_left"));
        files.add(new ToolEntry("global_search",      "Search for text across all project files",       CAT_FILES, "ic_mtrl_search"));
        files.add(new ToolEntry("get_recent_logs",    "Get the most recent build/app logs",             CAT_FILES, "ic_mtrl_log"));
        REGISTRY.put(CAT_FILES, files);

        // ── Activities & Screens ───────────────────────────────────────────────
        List<ToolEntry> activities = new ArrayList<>();
        activities.add(new ToolEntry("list_activities",    "List all screens/activities in a project", CAT_ACTIVITIES, "ic_mtrl_phone_android"));
        activities.add(new ToolEntry("get_screen_source",  "Get the Java source of an activity",       CAT_ACTIVITIES, "ic_mtrl_code"));
        activities.add(new ToolEntry("create_activity",    "Add a new screen/activity",                CAT_ACTIVITIES, "ic_mtrl_add_circle"));
        activities.add(new ToolEntry("delete_activity",    "Remove a screen/activity",                 CAT_ACTIVITIES, "ic_mtrl_delete"));
        activities.add(new ToolEntry("add_activity",       "Add an activity entry to the manifest",    CAT_ACTIVITIES, "ic_mtrl_add_box"));
        activities.add(new ToolEntry("add_permission",     "Add a manifest permission",                CAT_ACTIVITIES, "ic_mtrl_security"));
        REGISTRY.put(CAT_ACTIVITIES, activities);

        // ── UI Layout & Design ─────────────────────────────────────────────────
        List<ToolEntry> ui = new ArrayList<>();
        ui.add(new ToolEntry("describe_layout",      "Describe the current layout structure",         CAT_UI, "ic_mtrl_text_fields"));
        ui.add(new ToolEntry("edit_layout",          "Rewrite an entire layout XML",                  CAT_UI, "ic_mtrl_edit"));
        ui.add(new ToolEntry("add_view",             "Add a View to the layout (ViewBean format)",    CAT_UI, "ic_mtrl_add_box"));
        ui.add(new ToolEntry("modify_view",          "Modify an existing View's properties",          CAT_UI, "ic_mtrl_tune"));
        ui.add(new ToolEntry("remove_view",          "Remove a View from the layout",                 CAT_UI, "ic_mtrl_delete"));
        ui.add(new ToolEntry("add_view_xml",         "Add a View using raw XML fragment",             CAT_UI, "ic_mtrl_code"));
        ui.add(new ToolEntry("generate_layout",      "Generate a complete layout from description",   CAT_UI, "ic_mtrl_auto_fix_high"));
        ui.add(new ToolEntry("batch_patch_views",    "Patch multiple views' properties in one call",  CAT_UI, "ic_mtrl_tune"));
        ui.add(new ToolEntry("replace_subtree",      "Replace a container's children with new XML",   CAT_UI, "ic_mtrl_deployed_code"));
        ui.add(new ToolEntry("text_to_layout_ai",    "Full AI pipeline: text → complete layout XML",  CAT_AI_TOOLS, "ic_ai_robot"));
        ui.add(new ToolEntry("read_raw_resource_file",  "Read a raw resource file",                   CAT_UI, "ic_mtrl_description"));
        ui.add(new ToolEntry("write_raw_resource_file", "Write a raw resource file",                  CAT_UI, "ic_mtrl_edit_document"));
        ui.add(new ToolEntry("validate_rtl_layout",     "Validate RTL/LTR layout compatibility",      CAT_UI, "ic_mtrl_format_textdirection_r_to_l"));
        REGISTRY.put(CAT_UI, ui);

        // ── Block Logic ────────────────────────────────────────────────────────
        List<ToolEntry> logic = new ArrayList<>();
        logic.add(new ToolEntry("get_activity_events",   "List all events in an activity",             CAT_LOGIC, "ic_mtrl_event_note"));
        logic.add(new ToolEntry("get_event_blocks",      "Get all blocks in an event (raw JSON)",      CAT_LOGIC, "ic_mtrl_code_blocks"));
        logic.add(new ToolEntry("describe_block_logic",  "Show block logic as readable pseudocode",    CAT_LOGIC, "ic_mtrl_description"));
        logic.add(new ToolEntry("add_block",            "Add a block to an event",                    CAT_LOGIC, "ic_mtrl_add_box"));
        logic.add(new ToolEntry("modify_block",         "Modify an existing block",                   CAT_LOGIC, "ic_mtrl_tune"));
        logic.add(new ToolEntry("delete_block",         "Delete a block from an event",               CAT_LOGIC, "ic_mtrl_delete"));
        logic.add(new ToolEntry("get_moreblocks",       "List all custom MoreBlocks (functions)",     CAT_LOGIC, "ic_mtrl_functions"));
        logic.add(new ToolEntry("create_moreblock",     "Create a new custom MoreBlock",              CAT_LOGIC, "ic_mtrl_add_circle"));
        logic.add(new ToolEntry("delete_moreblock",     "Delete a custom MoreBlock",                  CAT_LOGIC, "ic_mtrl_delete"));
        REGISTRY.put(CAT_LOGIC, logic);

        // ── Resources ──────────────────────────────────────────────────────────
        List<ToolEntry> resources = new ArrayList<>();
        resources.add(new ToolEntry("add_string_resource", "Add a string resource",                   CAT_RESOURCES, "ic_mtrl_string"));
        resources.add(new ToolEntry("add_color_resource",  "Add a color resource",                    CAT_RESOURCES, "ic_mtrl_palette"));
        resources.add(new ToolEntry("list_resources",      "List all resources",                      CAT_RESOURCES, "ic_mtrl_folder_open"));
        REGISTRY.put(CAT_RESOURCES, resources);

        // ── Library Management ─────────────────────────────────────────────────
        List<ToolEntry> libs = new ArrayList<>();
        libs.add(new ToolEntry("list_libraries",        "List all enabled libraries",                 CAT_LIBRARIES, "ic_mtrl_library_books"));
        libs.add(new ToolEntry("add_library",           "Enable a built-in library (Firebase etc.)",  CAT_LIBRARIES, "ic_mtrl_add_circle"));
        libs.add(new ToolEntry("remove_library",        "Disable a library",                          CAT_LIBRARIES, "ic_mtrl_remove_circle"));
        libs.add(new ToolEntry("attach_local_library",  "Attach a local .jar/.aar library",           CAT_LIBRARIES, "ic_mtrl_attachment"));
        libs.add(new ToolEntry("detach_local_library",  "Detach a local library",                     CAT_LIBRARIES, "ic_mtrl_link_off"));
        libs.add(new ToolEntry("download_dependency",   "Download a Maven dependency",                CAT_LIBRARIES, "ic_mtrl_download"));
        libs.add(new ToolEntry("validate_libraries",    "Check for library conflicts",                 CAT_LIBRARIES, "ic_mtrl_verify"));
        libs.add(new ToolEntry("search_maven",              "Search Maven Central for a library",        CAT_LIBRARIES, "ic_mtrl_search"));
        libs.add(new ToolEntry("scan_dependencies",         "Scan project dependencies for issues",      CAT_LIBRARIES, "ic_mtrl_manage_search"));
        libs.add(new ToolEntry("validate_gradle_dependency","Validate a Maven coordinate before adding", CAT_LIBRARIES, "ic_mtrl_verify"));
        REGISTRY.put(CAT_LIBRARIES, libs);

        // ── Build & Compile ────────────────────────────────────────────────────
        List<ToolEntry> build = new ArrayList<>();
        build.add(new ToolEntry("build_project",        "Build debug APK (D8 dexer, incremental)",    CAT_BUILD, "ic_mtrl_build"));
        build.add(new ToolEntry("build_with_r8",        "Build with R8 shrink+minify (smaller APK)",  CAT_BUILD, "ic_mtrl_compress"));
        build.add(new ToolEntry("set_build_compiler",   "Set dexer (R8/D8/Dx), Java version, ECJ",    CAT_BUILD, "ic_mtrl_settings"));
        build.add(new ToolEntry("get_compile_logs",     "Get the latest compilation logs",             CAT_BUILD, "ic_mtrl_log"));
        build.add(new ToolEntry("analyze_build_error",  "Classify build errors + generate repair plan", CAT_BUILD, "ic_mtrl_troubleshoot"));
        build.add(new ToolEntry("get_project_structure","Show the project file/folder structure",      CAT_BUILD, "ic_mtrl_account_tree"));
        REGISTRY.put(CAT_BUILD, build);

        // ── AI-Powered Tools ───────────────────────────────────────────────────
        List<ToolEntry> aiTools = new ArrayList<>();
        aiTools.add(new ToolEntry("text_to_layout_ai",    "Generate a full layout from plain text using AI routing + validation", CAT_AI_TOOLS, "ic_ai_robot"));
        aiTools.add(new ToolEntry("analyze_code",         "Analyse code for bugs and improvements",  CAT_AI_TOOLS, "ic_mtrl_troubleshoot"));
        aiTools.add(new ToolEntry("review_source_code",   "Review code quality and best practices",  CAT_AI_TOOLS, "ic_mtrl_rate_review"));
        aiTools.add(new ToolEntry("create_from_template", "Create a project from a template",        CAT_AI_TOOLS, "ic_mtrl_auto_fix_high"));
        aiTools.add(new ToolEntry("add_locale_strings",   "Add localisation strings for a locale",   CAT_AI_TOOLS, "ic_mtrl_language"));
        aiTools.add(new ToolEntry("github_compare",       "Compare two GitHub repos or branches",    CAT_AI_TOOLS, "ic_mtrl_compare"));
        aiTools.add(new ToolEntry("github_search",        "Search GitHub for code/issues",           CAT_AI_TOOLS, "ic_mtrl_search"));
        REGISTRY.put(CAT_AI_TOOLS, aiTools);

        // ── Search & Analysis ──────────────────────────────────────────────────
        List<ToolEntry> search = new ArrayList<>();
        search.add(new ToolEntry("search_in_file",           "Search for a pattern inside a file (grep)", CAT_SEARCH, "ic_mtrl_manage_search"));
        search.add(new ToolEntry("filter_logcat",            "Filter device Logcat output",               CAT_SEARCH, "ic_mtrl_filter_alt"));
        search.add(new ToolEntry("analyze_unused_resources", "Find unused resources",                     CAT_SEARCH, "ic_mtrl_analytics"));
        REGISTRY.put(CAT_SEARCH, search);

        // ── Export ─────────────────────────────────────────────────────────────
        List<ToolEntry> export = new ArrayList<>();
        export.add(new ToolEntry("export_to_android_studio", "Export project to Android Studio format", CAT_EXPORT, "ic_mtrl_open_in_new"));
        REGISTRY.put(CAT_EXPORT, export);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Returns all tool categories in display order. */
    @NonNull
    public static List<String> getCategories() {
        return new ArrayList<>(REGISTRY.keySet());
    }

    /** Returns tools in the given category, or empty list if category not found. */
    @NonNull
    public static List<ToolEntry> getToolsByCategory(@NonNull String category) {
        List<ToolEntry> tools = REGISTRY.get(category);
        return tools != null ? Collections.unmodifiableList(tools) : Collections.emptyList();
    }

    /** Returns all tools as a flat list. */
    @NonNull
    public static List<ToolEntry> getAllTools() {
        List<ToolEntry> all = new ArrayList<>();
        for (List<ToolEntry> tools : REGISTRY.values()) all.addAll(tools);
        return all;
    }

    /** Returns a tool by name, or {@code null} if not found. */
    @androidx.annotation.Nullable
    public static ToolEntry findTool(@NonNull String toolName) {
        for (List<ToolEntry> tools : REGISTRY.values()) {
            for (ToolEntry tool : tools) {
                if (tool.name.equals(toolName)) return tool;
            }
        }
        return null;
    }

    /**
     * Builds a system prompt injection string listing all available tools.
     * This is prepended to the AI's system prompt so it knows its capabilities.
     */
    @NonNull
    public static String buildSystemPromptInjection() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n══════════════════════════════════════════════\n");
        sb.append("  YOUR AVAILABLE TOOLS IN SKETCHWARE PRO\n");
        sb.append("══════════════════════════════════════════════\n\n");
        sb.append("You have the following tools available. Use them proactively.\n\n");

        for (Map.Entry<String, List<ToolEntry>> entry : REGISTRY.entrySet()) {
            sb.append("── ").append(entry.getKey().toUpperCase()).append(" ──\n");
            for (ToolEntry tool : entry.getValue()) {
                sb.append("  • ").append(tool.name);
                sb.append(": ").append(tool.description).append("\n");
            }
            sb.append("\n");
        }

        sb.append("══════════════════════════════════════════════\n");
        sb.append("IMPORTANT: When the user asks you to do something,\n");
        sb.append("ALWAYS check if a tool above can help before responding.\n");
        sb.append("NEVER say you cannot do something if a tool exists for it.\n");
        sb.append("══════════════════════════════════════════════\n\n");

        sb.append("── SKETCHWARE NAMING RULES (CRITICAL — DO NOT VIOLATE) ──\n");
        sb.append("  • Activity names: pass \"main\", \"settings\", \"about\" to create_activity.\n");
        sb.append("    NEVER pass \"MainActivity\", \"SettingsActivity\" — Sketchware appends 'Activity' automatically.\n");
        sb.append("    The Java class for activity \"main\" is already called MainActivity.\n");
        sb.append("    The XML layout for activity \"main\" is called main.xml — NOT activity_main.xml.\n");
        sb.append("    In Java use R.layout.main — NOT R.layout.activity_main.\n");
        sb.append("  • Material Design colors in Java: NEVER use R.attr.colorPrimary etc.\n");
        sb.append("    These are in the Material library, not your app's R class.\n");
        sb.append("    Use: com.google.android.material.R.attr.colorPrimary (full package path).\n");
        sb.append("    Affected attributes: colorPrimary, colorPrimaryContainer, colorSurface,\n");
        sb.append("    colorSurfaceVariant, colorOnSurface, colorOnSurfaceVariant, colorSecondary, etc.\n\n");

        sb.append("── CROSS-REFERENCE RULES (avoid forgetting siblings) ──\n");
        sb.append("  • After editing a Layout XML → verify every R.id.X used in Java exists in that XML.\n");
        sb.append("  • After editing Java → verify every R.string.X / R.color.X exists in resources.\n");
        sb.append("  • After adding a View (add_view / add_view_xml) → add its event handler in Java if needed.\n");
        sb.append("  • After adding a String resource → do NOT add it again (causes 'duplicate resource' error).\n");
        sb.append("  • After deleting an Activity → remove all references to it (startActivity calls, manifest).\n\n");

        sb.append("── ERROR FIX PIPELINE (always follow this order) ──\n");
        sb.append("  1. Call analyze_build_error — it produces a prioritized repair plan.\n");
        sb.append("  2. Fix STAGE 1 first: AAPT/XML errors (malformed resource files).\n");
        sb.append("  3. Fix STAGE 2: missing resources (string/color/drawable/layout).\n");
        sb.append("  4. Fix STAGE 3: Material color attributes (R.attr.colorX → com.google.android.material.R.attr.colorX).\n");
        sb.append("  5. Fix STAGE 4: missing imports / libraries.\n");
        sb.append("  6. Fix STAGE 5: undeclared variables / type mismatches.\n");
        sb.append("  7. Fix STAGE 6: syntax errors (; ) } missing).\n");
        sb.append("  8. Call build_project to verify all stages are resolved.\n");
        sb.append("  DO NOT call build_project between individual fixes — batch all fixes then build once.\n\n");

        sb.append("── BUILD COMPILER QUICK REFERENCE ──\n");
        sb.append("  set_build_compiler dexer options:\n");
        sb.append("    \"D8\"  → modern dexer, no shrinking (default — use for most projects)\n");
        sb.append("    \"R8\"  → enables ProGuard+R8 shrink+minify (smallest APK, large projects)\n");
        sb.append("    \"Dx\" → legacy dexer (only for very old Sketchware projects)\n");
        sb.append("  java_version: \"1.7\" | \"1.8\" | \"11\" (default) | \"15\" | \"16\" | \"17\" | \"20\"\n");
        sb.append("  parallel_ecj: true = faster Java compile on multi-core devices\n\n");

        return sb.toString();
    }

    /**
     * Returns a markdown-formatted list of all tools, grouped by category.
     * Suitable for display in the chat UI as an info message.
     */
    @NonNull
    public static String buildMarkdownToolList() {
        StringBuilder sb = new StringBuilder("## 🤖 My Available Tools\n\n");
        for (Map.Entry<String, List<ToolEntry>> entry : REGISTRY.entrySet()) {
            sb.append("### ").append(entry.getKey()).append("\n");
            for (ToolEntry tool : entry.getValue()) {
                sb.append("- **`").append(tool.name).append("`** — ").append(tool.description).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ── Data types ─────────────────────────────────────────────────────────────

    /** Represents a single tool entry in the manifest. */
    public static final class ToolEntry {
        /** Tool name as registered in ToolRegistry (e.g. "list_projects"). */
        @NonNull public final String name;
        /** Human-readable description. */
        @NonNull public final String description;
        /** Category name (use CAT_* constants). */
        @NonNull public final String category;
        /** Drawable resource name for the tool's icon. */
        @NonNull public final String iconName;

        public ToolEntry(@NonNull String name, @NonNull String description,
                         @NonNull String category, @NonNull String iconName) {
            this.name        = name;
            this.description = description;
            this.category    = category;
            this.iconName    = iconName;
        }
    }

    private AiCapabilityManifest() {}
}
