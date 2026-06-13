package pro.sketchware.ai.tools;

/**
 * SketchwareAiPipeline — Tool category routing utilities for the AI agent.
 *
 * <p>The AI system prompt text has been extracted to
 * {@code assets/ai_system_prompt.txt} and is loaded at runtime by
 * {@link pro.sketchware.ai.storage.AiPreferences#getDefaultSystemPrompt}.
 */
public final class SketchwareAiPipeline {

    private SketchwareAiPipeline() {}

    // ── Tool category constants ────────────────────────────────────────────────
    public static final String CAT_UI       = "UI_LAYOUT";
    public static final String CAT_BLOCKS   = "BLOCK_LOGIC";
    public static final String CAT_JAVA     = "JAVA_CODE";
    public static final String CAT_XML_RES  = "XML_RESOURCE";
    public static final String CAT_PROJECT  = "PROJECT_ACTIVITY";
    public static final String CAT_LIBRARY  = "LIBRARY";
    public static final String CAT_BUILD    = "BUILD";
    public static final String CAT_ERROR    = "ERROR_LOG";
    public static final String CAT_FILE     = "FILE_MGMT";
    public static final String CAT_SEARCH   = "SEARCH_ANALYSIS";

    public static String getCategoryFor(String toolName) {
        if (toolName == null) return "UNKNOWN";
        switch (toolName) {
            case "describe_layout": case "add_view_xml": case "generate_layout":
                return CAT_UI;
            case "get_activity_events": case "get_event_blocks": case "add_block":
            case "modify_block": case "delete_block": case "get_more_blocks":
            case "create_more_block": case "delete_more_block":
                return CAT_BLOCKS;
            case "get_screen_source": case "patch_file": case "write_file":
            case "append_code": case "insert_code_at_line":
                return CAT_JAVA;
            case "list_resources": case "add_string_resource": case "add_color_resource":
            case "read_raw_resource_file": case "write_raw_resource_file":
            case "add_locale_strings": case "scan_unused_resources": case "delete_unused_resources":
                return CAT_XML_RES;
            case "list_projects": case "get_project_info": case "create_project":
            case "duplicate_project": case "delete_project": case "list_activities":
            case "create_activity": case "delete_activity": case "add_permission":
            case "get_project_structure":
                return CAT_PROJECT;
            case "list_libraries": case "validate_libraries": case "search_maven":
            case "dependency_scan": case "add_library": case "remove_library":
            case "attach_local_library": case "detach_local_library": case "download_dependency":
                return CAT_LIBRARY;
            case "build_project": case "build_with_r8": case "set_build_compiler":
            case "export_to_android_studio":
                return CAT_BUILD;
            case "get_compile_logs": case "get_recent_logs": case "analyze_code":
            case "review_source_code": case "validate_rtl_layout":
                return CAT_ERROR;
            case "list_files": case "read_file": case "read_file_range":
            case "delete_file": case "copy_file": case "move_file": case "global_search":
                return CAT_FILE;
            case "search_in_file": case "web_search": case "github_search":
            case "github_compare": case "logcat_filter":
                return CAT_SEARCH;
            default: return "UNKNOWN";
        }
    }

    public static boolean requiresReadFirst(String t) {
        if (t == null) return false;
        switch (t) {
            case "patch_file": case "write_file": case "append_code":
            case "insert_code_at_line": case "add_view_xml": case "add_block":
            case "modify_block": case "delete_block": case "remove_library":
            case "delete_activity": case "delete_file":
                return true;
            default: return false;
        }
    }

    public static boolean requiresConfirmation(String t) {
        if (t == null) return false;
        switch (t) {
            case "delete_file": case "delete_activity": case "delete_project":
            case "delete_unused_resources": case "remove_library":
            case "delete_more_block": case "detach_local_library":
                return true;
            default: return false;
        }
    }
}
