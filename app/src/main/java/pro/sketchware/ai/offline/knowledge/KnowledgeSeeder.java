package pro.sketchware.ai.offline.knowledge;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * One-shot, idempotent seeding of {@link KnowledgeStore} from the reorganized Sketchware AI
 * Knowledge Base (55 source JSON files, audited and deduplicated 2026-07-18), covering every
 * AI category in the app for both online and offline (on-device) models:
 * SYSTEM rules, CRITICAL knowledge, the 11 TOOL WORKFLOW categories, ADVANCED categories,
 * AI-SPECIFIC behaviour (context/token/memory/provider-routing/local-model rules), and
 * REFERENCE material (tool registry, block opcodes, error catalog, examples, master
 * pipelines).
 *
 * <p>This REPLACES the previous {@code KnowledgeSeeder} content wholesale — it is generated
 * from the new knowledge base, not merged with the old seed data. Bumping
 * {@link #CURRENT_SEED_VERSION} makes {@link #seedIfNeeded} re-run and upsert this new
 * content in place (existing rows are replaced by category+title, never duplicated — see
 * {@link KnowledgeStore#upsert}).
 *
 * <p>Generated from: Sketchware_AI_Knowledge_Base_COMPLETE.zip (55 JSON files, flat
 * structure). Pure metadata-about-the-knowledge-base files (index, statistics, audit
 * summary, migration guide, priority hierarchy, retrieval guide, usage recommendations,
 * context-budget notes, verification checklist) are intentionally NOT seeded as runtime
 * entries — they describe the knowledge base, they are not knowledge the model needs at
 * inference time.
 */
public final class KnowledgeSeeder {

    private KnowledgeSeeder() {}

    private static final String PREFS_NAME = "ai_knowledge_seed";
    private static final String KEY_SEED_VERSION = "seed_version";

    /**
     * Bumped for the 2026-07-18 knowledge base replacement (55 files, reorganized by
     * priority/category, deduplicated, covering online + offline AI paths). Bump again
     * whenever the seed content below changes.
     */
    private static final int CURRENT_SEED_VERSION = 2;

    public static void seedIfNeeded(@NonNull Context context, @NonNull KnowledgeStore store) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int seededVersion = prefs.getInt(KEY_SEED_VERSION, 0);
        if (seededVersion >= CURRENT_SEED_VERSION) return;

        seed(store);

        prefs.edit().putInt(KEY_SEED_VERSION, CURRENT_SEED_VERSION).apply();
    }

    /** Runs every upsert unconditionally — exposed separately so a settings screen could
     *  offer a manual "reset to defaults" action. */
    public static void seed(@NonNull KnowledgeStore store) {
        seed_core_rules(store);
        seed_permissions(store);
        seed_response_rules(store);
        seed_environments(store);
        seed_forbidden_actions(store);
        seed_naming_conventions(store);
        seed_material_attributes(store);
        seed_id_protection_rules(store);
        seed_build_verification(store);
        seed_ui_layout_category(store);
        seed_block_logic_category(store);
        seed_java_code_category(store);
        seed_xml_resources_category(store);
        seed_project_activities_category(store);
        seed_libraries_category(store);
        seed_build_category(store);
        seed_error_logs_category(store);
        seed_file_management_category(store);
        seed_search_analysis_category(store);
        seed_project_snapshots_category(store);
        seed_resources_management(store);
        seed_manifest_editing(store);
        seed_themes_styles(store);
        seed_local_libraries(store);
        seed_conflict_resolution(store);
        seed_consistency_validation(store);
        seed_error_recovery(store);
        seed_context_management(store);
        seed_token_optimization(store);
        seed_memory_rules(store);
        seed_tool_selection(store);
        seed_provider_routing(store);
        seed_local_model_rules(store);
        seed_tool_registry(store);
        seed_block_opcode_reference(store);
        seed_error_catalog(store);
        seed_examples_and_patterns(store);
        seed_master_pipelines(store);
        seed_block_manager_comprehensive(store);
        seed_build_comprehensive(store);
        seed_detailed_pipelines(store);
        seed_error_routing_table(store);
        seed_java_comprehensive(store);
        seed_library_comprehensive(store);
        seed_manifest_comprehensive(store);
        seed_resources_comprehensive(store);
    }

    // Source: 00_core_rules.json
    private static void seed_core_rules(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.ENV, "core_rules",
                // CORE RULES
                "section: SYSTEM; priority: CRITICAL; rules: id: R1; title: READ BEFORE WRITE; scope: ALL, id: "
                        + "R2; title: MINIMAL PATCH; scope: JAVA & XML, id: R3; title: ID PROTECTION; scope: XML & JAVA "
                        + "REFS, id: R4; title: ONE STEP AT A TIME; scope: ALL MULTI-STEP, id: R5; title: JAVA ONLY; "
                        + "scope: CODE, id: R6; title: VALIDATE BEFORE APPLY; scope: XML, id: R7; title: CONFIRM "
                        + "DESTRUCTIVE; scope: DELETE/OVERWRITE, id: R8; title: ENCRYPTED FILES; scope: LOGIC/VIEW, id: "
                        + "R9; title: BUILD AFTER EDIT; scope: POST-EDIT, id: R10; title: FIX ROOT CAUSE; scope: ERROR "
                        + "RECOVERY",
                "after all apply at before build cause code confirm critical delete destructive edit encrypted "
                        + "error files fix id java logic minimal multi one only overwrite patch post protection r1 r10 r2 "
                        + "r3 r4 r5 r6 r7 r8 r9 read recovery refs root rules scope step system time title validate view "
                        + "write xml",
                KnowledgeStore.Priority.CRITICAL);
    }

    // Source: 01_permissions.json
    private static void seed_permissions(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.ENV, "permissions",
                // PERMISSIONS
                "section: SYSTEM; priority: CRITICAL; permissions: full_project_access, all_tools, all_files; "
                        + "error handling: inform_clearly, suggest_alternatives, never_stop",
                "all_files all_tools critical error_handling full_project_access inform_clearly never_stop "
                        + "permissions suggest_alternatives system",
                KnowledgeStore.Priority.CRITICAL);
    }

    // Source: 02_response_rules.json
    private static void seed_response_rules(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.ENV, "response_rules",
                // RESPONSE RULES
                "section: SYSTEM; priority: CRITICAL; format: brief_intro, inline_execution, line_summary, "
                        + "step_marks",
                "brief_intro critical format inline_execution line_summary step_marks system",
                KnowledgeStore.Priority.CRITICAL);
    }

    // Source: 03_environments.json
    private static void seed_environments(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.ENV, "environments",
                // ENVIRONMENTS
                "section: SYSTEM; priority: CRITICAL; sketchware storage: .sketchware/data/{sc_id}/; build: "
                        + "compileSdk: 36; minSdk: 21; targetSdk: 36; java: 17; viewBinding: True",
                "build compilesdk critical data java minsdk sc_id sketchware sketchware_storage system targetsdk "
                        + "viewbinding",
                KnowledgeStore.Priority.CRITICAL);
    }

    // Source: 10_forbidden_actions.json
    private static void seed_forbidden_actions(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.RULE, "forbidden_actions",
                // FORBIDDEN ACTIONS
                "section: CRITICAL; priority: CRITICAL; prohibitions: write_file to encrypted view/logic, Kotlin "
                        + "in Java files, build with known errors, rename/remove android:id, duplicate android:id, rewrite "
                        + "entire class, network on main thread, fix multiple errors together, fabricate content, delete "
                        + "without confirm, build 3+ times without fix",
                "android build class confirm content critical delete duplicate encrypted entire errors fabricate "
                        + "files fix id in java known kotlin logic main multiple network on prohibitions remove rename "
                        + "rewrite thread times to together view with without write_file",
                KnowledgeStore.Priority.CRITICAL);
    }

    // Source: 11_naming_conventions.json
    private static void seed_naming_conventions(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.RULE, "naming_conventions",
                // NAMING CONVENTIONS
                "section: CRITICAL; priority: CRITICAL; activities: rule: snake_case; sketchware adds: Activity; "
                        + "resources: strings: snake_case; colors: snake_case; drawables: snake_case; ids: rule: @+id/ for "
                        + "new, @id/ for reference",
                "activities activity colors critical drawables for id ids new reference resources rule "
                        + "sketchware_adds snake_case strings",
                KnowledgeStore.Priority.CRITICAL);
    }

    // Source: 12_material_attributes.json
    private static void seed_material_attributes(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.RULE, "material_attributes",
                // MATERIAL ATTRIBUTES
                "section: CRITICAL; priority: CRITICAL; critical: Material R.attr.* are NOT in app R class; "
                        + "correct path: com.google.android.material.R.attr.colorX; attributes: colorPrimary, "
                        + "colorPrimaryContainer, colorSurface, colorSurfaceVariant",
                "android app are attr attributes class colorprimary colorprimarycontainer colorsurface "
                        + "colorsurfacevariant colorx com correct_path critical google in material not",
                KnowledgeStore.Priority.CRITICAL);
    }

    // Source: 13_id_protection_rules.json
    private static void seed_id_protection_rules(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.RULE, "id_protection_rules",
                // ID PROTECTION RULES
                "section: CRITICAL; priority: CRITICAL; at plus id: @+id/ declares NEW, @id/ references "
                        + "existing; viewbinding: binding.viewId not findViewById(); safety: Never rename ID after "
                        + "creation",
                "after at_plus_id binding creation critical declares existing findviewbyid id never new not "
                        + "references rename safety viewbinding viewid",
                KnowledgeStore.Priority.CRITICAL);
    }

    // Source: 14_build_verification.json
    private static void seed_build_verification(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.RULE, "build_verification",
                // BUILD VERIFICATION
                "section: CRITICAL; priority: CRITICAL; mandatory: build after Java patch, build after XML "
                        + "change, build after lib add; failure handling: read logs, identify first error, fix one, "
                        + "rebuild, repeat",
                "add after build change critical error failure_handling first fix identify java lib logs "
                        + "mandatory one patch read rebuild repeat xml",
                KnowledgeStore.Priority.CRITICAL);
    }

    // Source: 20_ui_layout_category.json
    private static void seed_ui_layout_category(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "ui_layout_category",
                // TOOL WORKFLOWS: UI LAYOUT
                "section: TOOL WORKFLOWS; category: UI LAYOUT; tools: describe_layout, add_view_xml, "
                        + "generate_layout, modify_view; pipeline edit: describe_layout READ, add_view_xml ADD one view, "
                        + "describe_layout VERIFY; pipeline new: list_activities, describe_layout confirm empty, "
                        + "generate_layout, build_project; rules: no full rewrite, protect IDs, no duplicates, use @+id/, "
                        + "48dp min touch",
                "48dp add add_view_xml build_project confirm describe_layout duplicates empty full "
                        + "generate_layout id ids layout list_activities min modify_view no one pipeline_edit pipeline_new "
                        + "protect read rewrite rules tool tools touch ui use verify view workflows",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 21_block_logic_category.json
    private static void seed_block_logic_category(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "block_logic_category",
                // TOOL WORKFLOWS: BLOCK LOGIC
                "section: TOOL WORKFLOWS; category: BLOCK LOGIC; tools: get_activity_events, get_event_blocks, "
                        + "add_block, modify_block, delete_block; opcodes: addSourceDirectly, showToast, startActivity, "
                        + "finish, ifElse, repeat; pipeline add: get_activity_events, get_event_blocks, add_block, "
                        + "get_event_blocks VERIFY; rules: no direct write, no guess IDs, use blocks only",
                "add_block addsourcedirectly block blocks delete_block direct finish get_activity_events "
                        + "get_event_blocks guess ids ifelse logic modify_block no only opcodes pipeline_add repeat rules "
                        + "showtoast startactivity tool tools use verify workflows write",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 22_java_code_category.json
    private static void seed_java_code_category(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "java_code_category",
                // TOOL WORKFLOWS: JAVA CODE
                "section: TOOL WORKFLOWS; category: JAVA CODE; tools: get_screen_source, search_in_file, "
                        + "patch_file, write_file; pipeline: get_screen_source READ, search_in_file LOCATE, patch_file "
                        + "MINIMAL, build_project VERIFY; rules: no rewrite class, no Kotlin, no main thread IO, patch "
                        + "preferred, write LAST RESORT",
                "build_project class code get_screen_source io java kotlin last locate main minimal no patch "
                        + "patch_file pipeline preferred read resort rewrite rules search_in_file thread tool tools verify "
                        + "workflows write write_file",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 23_xml_resources_category.json
    private static void seed_xml_resources_category(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "xml_resources_category",
                // TOOL WORKFLOWS: XML RESOURCES
                "section: TOOL WORKFLOWS; category: XML RESOURCES; tools: list_resources, add_string_resource, "
                        + "add_color_resource, write_raw_resource_file; pipelines: ADD STRING, ADD COLOR, CLEAN UNUSED",
                "add add_color_resource add_string_resource clean color list_resources pipelines resources "
                        + "string tool tools unused workflows write_raw_resource_file xml",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 24_project_activities_category.json
    private static void seed_project_activities_category(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "project_activities_category",
                // TOOL WORKFLOWS: PROJECT & ACTIVITIES
                "section: TOOL WORKFLOWS; category: PROJECT & ACTIVITIES; tools: list_projects, "
                        + "get_project_info, create_project, add_activity, delete_project; pipeline create: list_projects, "
                        + "create_project, list_activities, add_activity, describe_layout",
                "activities add_activity create_project delete_project describe_layout get_project_info "
                        + "list_activities list_projects pipeline_create project tool tools workflows",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 25_libraries_category.json
    private static void seed_libraries_category(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "libraries_category",
                // TOOL WORKFLOWS: LIBRARIES
                "section: TOOL WORKFLOWS; category: LIBRARIES; tools: list_libraries, validate_libraries, "
                        + "search_maven, add_library, remove_library; pipeline add: list_libraries, search_maven, "
                        + "validate_libraries, download_dependency, build_project",
                "add_library build_project download_dependency libraries list_libraries pipeline_add "
                        + "remove_library search_maven tool tools validate_libraries workflows",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 26_build_category.json
    private static void seed_build_category(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "build_category",
                // TOOL WORKFLOWS: BUILD
                "section: TOOL WORKFLOWS; category: BUILD; tools: build_project, build_with_r8, "
                        + "set_build_compiler, analyze_build_error; pipeline standard: build_project ECJ→D8→APK, success "
                        + "report APK, failure get_compile_logs; pipeline r8: validate_libraries, build_with_r8, add "
                        + "proguard rules if failed",
                "add analyze_build_error apk build build_project build_with_r8 d8 ecj failed failure "
                        + "get_compile_logs if pipeline_r8 pipeline_standard proguard report rules set_build_compiler "
                        + "success tool tools validate_libraries workflows",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 27_error_logs_category.json
    private static void seed_error_logs_category(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "error_logs_category",
                // TOOL WORKFLOWS: ERROR LOGS
                "section: TOOL WORKFLOWS; category: ERROR LOGS; tools: get_compile_logs, get_recent_logs, "
                        + "analyze_code, review_source_code; pipeline compile error: get_compile_logs, identify first "
                        + "error, read context, diagnose, patch_file, build_project",
                "analyze_code build_project context diagnose error first get_compile_logs get_recent_logs "
                        + "identify logs patch_file pipeline_compile_error read review_source_code tool tools workflows",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 28_file_management_category.json
    private static void seed_file_management_category(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "file_management_category",
                // TOOL WORKFLOWS: FILE MANAGEMENT
                "section: TOOL WORKFLOWS; category: FILE MANAGEMENT; tools: list_files, read_file, "
                        + "read_file_range, write_file, delete_file; rule: max 100 lines per range read",
                "100 delete_file file lines list_files management max per range read read_file read_file_range "
                        + "rule tool tools workflows write_file",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 29_search_analysis_category.json
    private static void seed_search_analysis_category(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "search_analysis_category",
                // TOOL WORKFLOWS: SEARCH & ANALYSIS
                "section: TOOL WORKFLOWS; category: SEARCH & ANALYSIS; tools: search_in_file, global_search, "
                        + "analyze_code, web_search, github_search",
                "analysis analyze_code github_search global_search search search_in_file tool tools web_search "
                        + "workflows",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 30_project_snapshots_category.json
    private static void seed_project_snapshots_category(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "project_snapshots_category",
                // TOOL WORKFLOWS: PROJECT SNAPSHOTS
                "section: TOOL WORKFLOWS; category: PROJECT SNAPSHOTS; tools: create_snapshot, list_snapshots, "
                        + "restore_snapshot; pipeline: create_snapshot before risky change, perform edit, build_project, "
                        + "if broken restore_snapshot",
                "before broken build_project change create_snapshot edit if list_snapshots perform pipeline "
                        + "project restore_snapshot risky snapshots tool tools workflows",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 40_resources_management.json
    private static void seed_resources_management(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "resources_management",
                // ADVANCED: RESOURCES MANAGEMENT
                "section: ADVANCED; category: RESOURCES MANAGEMENT; resource types: strings, colors, drawables, "
                        + "themes, styles, fonts, menus, animations; validation: Always verify cross-references after "
                        + "resource change",
                "advanced after always animations change colors cross drawables fonts management menus "
                        + "references resource resource_types resources strings styles themes validation verify",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 41_manifest_editing.json
    private static void seed_manifest_editing(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "manifest_editing",
                // ADVANCED: MANIFEST EDITING
                "section: ADVANCED; category: MANIFEST EDITING; elements: permissions, activities, services, "
                        + "receivers, providers, intent-filters; rule: Patch only target node, never full rewrite",
                "activities advanced editing elements filters full intent manifest never node only patch "
                        + "permissions providers receivers rewrite rule services target",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 42_themes_styles.json
    private static void seed_themes_styles(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "themes_styles",
                // ADVANCED: THEMES & STYLES
                "section: ADVANCED; category: THEMES & STYLES; components: themes.xml, themes-night.xml, "
                        + "DayNight sync, Material compatibility; rule: Always update both light and dark themes",
                "advanced always and both compatibility components dark daynight light material night rule "
                        + "styles sync themes update xml",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 43_local_libraries.json
    private static void seed_local_libraries(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "local_libraries",
                // ADVANCED: LOCAL LIBRARIES
                "section: ADVANCED; category: LOCAL LIBRARIES; formats: AAR, JAR, DEX; pipeline: "
                        + "list_local_libraries, validate, attach, scan_dependencies, build_project; rule: Never attach "
                        + "duplicate packages",
                "aar advanced attach build_project dex duplicate formats jar libraries list_local_libraries "
                        + "local never packages pipeline rule scan_dependencies validate",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 44_conflict_resolution.json
    private static void seed_conflict_resolution(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "conflict_resolution",
                // ADVANCED: CONFLICT RESOLUTION
                "section: ADVANCED; category: CONFLICT RESOLUTION; conflicts: duplicate classes, version "
                        + "mismatch, androidx mismatch, material mismatch; rule: Prefer newest stable version, never keep "
                        + "two versions",
                "advanced androidx classes conflict conflicts duplicate keep material mismatch never newest "
                        + "prefer resolution rule stable two version versions",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 45_consistency_validation.json
    private static void seed_consistency_validation(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "consistency_validation",
                // ADVANCED: CONSISTENCY VALIDATION
                "section: ADVANCED; category: CONSISTENCY VALIDATION; validates: Java ↔ Layout IDs, Java ↔ "
                        + "Resources, Resources ↔ Manifest, Manifest ↔ Libraries, Libraries ↔ Build; automatic check: Run "
                        + "after all major edits before build",
                "advanced after all automatic_check before build consistency edits ids java layout libraries "
                        + "major manifest resources run validates validation",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 46_error_recovery.json
    private static void seed_error_recovery(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "error_recovery",
                // ADVANCED: ERROR RECOVERY
                "section: ADVANCED; category: ERROR RECOVERY; error types: Compile errors (AAPT, Missing "
                        + "resources, Java syntax), Runtime crashes (NPE, IndexOOB, ClassCastException), Provider errors "
                        + "(API balance, rate limit, timeout), Build errors (OOM, interrupted, workspace missing); "
                        + "strategy: Identify root cause, fix only that, rebuild, repeat",
                "aapt advanced api balance build cause classcastexception compile crashes error error_types "
                        + "errors fix identify indexoob interrupted java limit missing npe only oom provider rate rebuild "
                        + "recovery repeat resources root runtime strategy syntax that timeout workspace",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 50_context_management.json
    private static void seed_context_management(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "context_management",
                // AI SPECIFIC: CONTEXT MANAGEMENT
                "section: AI SPECIFIC; category: CONTEXT MANAGEMENT; rules: Retrieve only required context, "
                        + "Never inject full project, Prefer summaries over raw data, Use semantic search, Cache repeated "
                        + "retrievals, Budget tokens before prompt, Never exceed model context limit",
                "ai before budget cache context data exceed full inject limit management model never only over "
                        + "prefer project prompt raw repeated required retrievals retrieve rules search semantic specific "
                        + "summaries tokens use",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 51_token_optimization.json
    private static void seed_token_optimization(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "token_optimization",
                // AI SPECIFIC: TOKEN OPTIMIZATION
                "section: AI SPECIFIC; category: TOKEN OPTIMIZATION; strategies: Load essential tools first, "
                        + "Discover remaining tools lazily, Compress repeated sentences, Remove unused examples, Prefer "
                        + "IDs over full names, Reference instead of copy; budget targets: system rules: 500; critical: "
                        + "1500; per category: 800; advanced: 2000; total typical: 8000",
                "advanced ai budget_targets compress copy critical discover essential examples first full ids "
                        + "instead lazily load names of optimization over per_category prefer reference remaining remove "
                        + "repeated sentences specific strategies system_rules token tools total_typical unused",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 52_memory_rules.json
    private static void seed_memory_rules(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "memory_rules",
                // AI SPECIFIC: MEMORY RULES
                "section: AI SPECIFIC; category: MEMORY RULES; project facts: Persistent across conversation; "
                        + "conversation facts: Temporary, session-only; rules: Never duplicate stored memory, Prefer "
                        + "update over recreate, Invalidate stale cache, Refresh after build",
                "across after ai build cache conversation conversation_facts duplicate invalidate memory never "
                        + "only over persistent prefer project_facts recreate refresh rules session specific stale stored "
                        + "temporary update",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 53_tool_selection.json
    private static void seed_tool_selection(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "tool_selection",
                // AI SPECIFIC: TOOL SELECTION
                "section: AI SPECIFIC; category: TOOL SELECTION; rules: Prefer smallest tool, Prefer targeted "
                        + "tool, Avoid expensive tool, Never read full project if search is enough, Never build before "
                        + "validation, Never search twice for same info",
                "ai avoid before build enough expensive for full if info is never prefer project read rules same "
                        + "search selection smallest specific targeted tool twice validation",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 54_provider_routing.json
    private static void seed_provider_routing(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "provider_routing",
                // AI SPECIFIC: PROVIDER ROUTING
                "section: AI SPECIFIC; category: PROVIDER ROUTING; providers: OpenAI, Claude, DeepSeek, Google "
                        + "AI, Local LiteRT; strategy: Prefer online → fallback local → suggest alternative",
                "ai alternative claude deepseek fallback google litert local online openai prefer provider "
                        + "providers routing specific strategy suggest",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 55_local_model_rules.json
    private static void seed_local_model_rules(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "local_model_rules",
                // AI SPECIFIC: LOCAL MODEL RULES
                "section: AI SPECIFIC; category: LOCAL MODEL RULES; models: LiteRT, Gemma, Qwen, DeepSeek; "
                        + "rules: Prefer local when capable, Respect context budget strictly, Avoid unnecessary tool "
                        + "schemas, Use essential tools first, Fallback online when local cannot complete",
                "ai avoid budget cannot capable complete context deepseek essential fallback first gemma litert "
                        + "local model models online prefer qwen respect rules schemas specific strictly tool tools "
                        + "unnecessary use when",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 60_tool_registry.json
    private static void seed_tool_registry(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "tool_registry",
                // REFERENCE: TOOL REGISTRY
                "section: REFERENCE; category: TOOL REGISTRY; tools count: 40+; categories: UI, Logic, Java, "
                        + "Resources, Projects, Libraries, Build, Error, Files, Search, Snapshots; note: All tools "
                        + "referenced by category files",
                "40 all build by categories error files java libraries logic note projects reference referenced "
                        + "registry resources search snapshots tool tools tools_count ui",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 61_block_opcode_reference.json
    private static void seed_block_opcode_reference(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "block_opcode_reference",
                // REFERENCE: BLOCK OPCODE REFERENCE
                "section: REFERENCE; category: BLOCK OPCODE REFERENCE; common opcodes: control: ifElse, repeat, "
                        + "forever, doWhile; ui: showToast, setText, setImage, setEnabled; navigation: startActivity, "
                        + "finish; variables: setVarString, setVarInt, setVarBoolean; network: httpGet, httpPost; custom: "
                        + "addSourceDirectly",
                "addsourcedirectly block common_opcodes control custom dowhile finish forever httpget httppost "
                        + "ifelse navigation network opcode reference repeat setenabled setimage settext setvarboolean "
                        + "setvarint setvarstring showtoast startactivity ui variables",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 62_error_catalog.json
    private static void seed_error_catalog(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "error_catalog",
                // REFERENCE: ERROR CATALOG
                "section: REFERENCE; category: ERROR CATALOG; errors: cannot find symbol: Missing import or "
                        + "wrong name; incompatible types: Type mismatch or missing cast; duplicate definition: "
                        + "Variable/method already exists; unreachable statement: Code after return/throw; nullpointer: "
                        + "Null layout ID or uninitialized reference",
                "after already cannot_find_symbol cast catalog code duplicate_definition error errors exists id "
                        + "import incompatible_types layout method mismatch missing name null nullpointer or reference "
                        + "return throw type uninitialized unreachable_statement variable wrong",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 63_examples_and_patterns.json
    private static void seed_examples_and_patterns(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "examples_and_patterns",
                // REFERENCE: EXAMPLES & PATTERNS
                "section: REFERENCE; category: EXAMPLES & PATTERNS; patterns: Add View, Add Event Handler, Add "
                        + "String Resource, Add Library, Fix Compile Error",
                "add compile error event examples fix handler library patterns reference resource string view",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: 64_master_pipelines.json
    private static void seed_master_pipelines(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "master_pipelines",
                // REFERENCE: MASTER PIPELINES
                "section: REFERENCE; category: MASTER PIPELINES; pipelines: name: BUILD FULL APP FROM SCRATCH; "
                        + "steps: PLAN: List screens, data, libraries, PROJECT: create_project, SCREENS: create_activity, "
                        + "generate_layout per screen, LOGIC: add_block for interactions, RESOURCES: "
                        + "add_string_resource/add_color_resource, LIBRARIES: only if needed, BUILD: build_project, fix "
                        + "errors, verify",
                "add_block add_color_resource add_string_resource app build build_project create_activity "
                        + "create_project data errors fix for from full generate_layout if interactions libraries list "
                        + "logic master name needed only per pipelines plan project reference resources scratch screen "
                        + "screens steps verify",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: _BLOCK_MANAGER_COMPREHENSIVE.json
    private static void seed_block_manager_comprehensive(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "_BLOCK_MANAGER_COMPREHENSIVE",
                //  BLOCK MANAGER COMPREHENSIVE
                "block operations: add block: method: add_block(sc_id, activity, event, opcode, position, "
                        + "params); position 0: Insert at start; position minus 1: Insert at end; position n: Insert after "
                        + "block N; params format: List matching opcode specification; modify block: method: "
                        + "modify_block(sc_id, activity, event, block_id, new_params); must read first: "
                        + "get_event_blocks(sc_id, activity, event); verify after: get_event_blocks() to confirm change; "
                        + "delete block: confirmation: Always ask user 'Delete block [id: opcode]?'; verify after: "
                        + "get_event_blocks() to verify chain intact; common opcodes: control flow: ifElse: spec: if "
                        + "%b.condition then; repeat: spec: repeat %n.count times; params: 1; forever: spec: repeat "
                        + "forever; doWhile: spec: repeat while %b.condition; ui operations: showToast: spec: show toast "
                        + "%s.text; params: Hello; setText: spec: set text of %m.view to %s.text; params: view, text; "
                        + "setImage: spec: set image of %m.view to %m.drawable; params: view, drawable; navigation: "
                        + "startActivity: spec: start activity %m.activity; params: activityName; finish: spec: finish; "
                        + "variables: setVarString: spec: set %s.name = %s.value; params: varName, val; setVarInt: spec: "
                        + "set %s.name = %n.value; params: varName, 0",
                "activity activityname add_block after always ask at block block_id block_operations chain "
                        + "change common_opcodes condition confirm confirmation control_flow count delete delete_block "
                        + "dowhile drawable end event finish forever get_event_blocks hello id if ifelse image insert "
                        + "intact list matching method modify_block must_read_first name navigation new_params of opcode "
                        + "params params_format position position_0 position_minus_1 position_n repeat sc_id set setimage "
                        + "settext setvarint setvarstring show showtoast spec specification start startactivity text then "
                        + "times to toast ui_operations user val value variables varname verify verify_after view while",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: _BUILD_COMPREHENSIVE.json
    private static void seed_build_comprehensive(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "_BUILD_COMPREHENSIVE",
                //  BUILD COMPREHENSIVE
                "build process: phase 1: ECJ (Eclipse Compiler for Java) — Java → .class files; phase 2: D8/R8 "
                        + "Dexer — .class files → .dex files; phase 3: AAPT/AAPT2 — Compile resources, generate R.java; "
                        + "phase 4: APK Packager — Create APK from dex + resources; compiler options: d8 default: name: "
                        + "D8; description: Modern dexer, no shrinking; use: Most projects; r8 advanced: name: R8; "
                        + "description: ProGuard+R8, shrink+minify, smallest APK; use: Large projects; dx legacy: name: "
                        + "Dx; description: Legacy dexer; use: Very old Sketchware projects only; java versions: 8, 11, "
                        + "15, 16, 17, 20; failure handling: step 1: Never call build_project when known errors exist; "
                        + "step 2: After failure: call get_compile_logs immediately; step 3: Follow error-fix routing "
                        + "table (Stage 1 → 6); step 4: Fix one error, rebuild, repeat; step 5: After 3 failed builds "
                        + "without fixing errors: STOP, ask user for guidance",
                "aapt aapt2 after apk ask build_process build_project builds call class compile compiler "
                        + "compiler_options create d8 d8_default description dex dexer dx dx_legacy ecj eclipse error "
                        + "errors exist failed failure failure_handling files fix fixing follow for from generate "
                        + "get_compile_logs guidance immediately java java_versions known large legacy minify modern most "
                        + "name never no old one only packager phase_1 phase_2 phase_3 phase_4 proguard projects r8 "
                        + "r8_advanced rebuild repeat resources routing shrink shrinking sketchware smallest stage step_1 "
                        + "step_2 step_3 step_4 step_5 stop table use user very when without",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: _DETAILED_PIPELINES.json
    private static void seed_detailed_pipelines(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "_DETAILED_PIPELINES",
                //  DETAILED PIPELINES
                "pipelines: edit layout: name: Edit Existing Layout; steps: step: 1; action: "
                        + "describe_layout(sc_id, activity); purpose: READ current state (MANDATORY), step: 2; action: "
                        + "add_view_xml(sc_id, activity, xml, parent_id); purpose: ADD one view only, step: 3; action: "
                        + "describe_layout(sc_id, activity); purpose: VERIFY insertion, step: 4; action: "
                        + "build_project(sc_id); purpose: VERIFY compilation; forbidden: full_rewrite, change_id, "
                        + "duplicate_id; add event: name: Add Event Handler; steps: step: 1; action: "
                        + "get_activity_events(sc_id, activity), step: 2; action: get_event_blocks(sc_id, activity, "
                        + "event), step: 3; action: add_block(sc_id, activity, event, opcode, position, params), step: 4; "
                        + "action: get_event_blocks(sc_id, activity, event); purpose: VERIFY; edit java: name: Edit Java "
                        + "Code; steps: step: 1; action: get_screen_source(sc_id, activity); purpose: READ (MANDATORY), "
                        + "step: 2; action: search_in_file(path, 'method'), step: 3; action: patch_file(path, old_str, "
                        + "new_str); note: old_str must be unique, step: 4; action: build_project(sc_id); purpose: VERIFY, "
                        + "step: 5; action: get_compile_logs(sc_id); note: only if build failed; critical: Never rewrite "
                        + "entire class; add library: name: Add Maven Library; steps: step: 1; action: "
                        + "list_libraries(sc_id); purpose: Check not attached, step: 2; action: "
                        + "search_maven(library_name); purpose: Find latest STABLE, step: 3; action: "
                        + "validate_libraries(sc_id, new_lib); purpose: Check conflicts, step: 4; action: "
                        + "download_dependency(sc_id, groupId:artifactId:version), step: 5; action: build_project(sc_id); "
                        + "purpose: VERIFY; fix compile error: name: Fix Compile Error; steps: step: 1; action: "
                        + "get_compile_logs(sc_id); purpose: READ full output, step: 2; action: Find FIRST error; format: "
                        + "filename:line:col: error: message, step: 3; action: read_file_range(path, line-5, line+5); "
                        + "purpose: Context, step: 4; action: Diagnose root cause; examples: cannot find symbol = missing "
                        + "import, incompatible types = type mismatch, step: 5; action: patch_file(path, error_context, "
                        + "fix); purpose: Fix ROOT CAUSE only, step: 6; action: build_project(sc_id); purpose: VERIFY, "
                        + "step: 7; note: Repeat from step 1 for NEXT error; critical: NEVER fix 2 errors simultaneously; "
                        + "build full app: name: Build Full App From Text Description; steps: step: 0; action: PLAN (text "
                        + "only); details: List screens, data per screen, libraries needed, step: 1; action: "
                        + "create_project(app_name, package_name), step: 2; action: Per screen: create_activity → "
                        + "describe_layout → generate_layout → describe_layout VERIFY, step: 3; action: Per screen needing "
                        + "logic: get_activity_events → add_block → get_event_blocks VERIFY, step: 4; action: Add "
                        + "resources: add_string_resource / add_color_resource, step: 5; action: If needed: follow "
                        + "add-library pipeline, step: 6; action: build_project(sc_id), fix errors one at a time, rebuild "
                        + "after each; critical: Never jump to build before every screen exists",
                "action activity add add_block add_color_resource add_event add_library add_string_resource "
                        + "add_view_xml after app app_name artifactid at attached be before build build_full_app "
                        + "build_project cannot cause change_id check class code col compilation compile conflicts context "
                        + "create_activity create_project critical current data describe_layout description details "
                        + "diagnose download_dependency duplicate_id each edit edit_java edit_layout entire error "
                        + "error_context errors event every examples existing exists failed filename find first fix "
                        + "fix_compile_error follow for forbidden format from full full_rewrite generate_layout "
                        + "get_activity_events get_compile_logs get_event_blocks get_screen_source groupid handler if "
                        + "import incompatible insertion java jump latest layout libraries library library_name l",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: _ERROR_ROUTING_TABLE.json
    private static void seed_error_routing_table(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "_ERROR_ROUTING_TABLE",
                //  ERROR ROUTING TABLE
                "error categories: stage 1 aapt: name: AAPT/XML Errors; errors: Malformed XML, Invalid "
                        + "namespace, Unclosed tag; fix tool: patch_file on XML, validate XML syntax; rebuild: immediate; "
                        + "stage 2 resources: name: Missing Resources; errors: string/xxx not found → "
                        + "add_string_resource(sc_id, name, value), color/xxx not found → add_color_resource(sc_id, name, "
                        + "#AARRGGBB), drawable/xxx not found → create drawable XML, layout/xxx not found → create layout "
                        + "XML, @id/ should be @+id/ → patch_file layout XML; routing: By resource type; stage 3 material: "
                        + "name: Material Attributes; error: R.attr.colorPrimary not found; fix: Change to "
                        + "com.google.android.material.R.attr.colorPrimary; tool: patch_file or add import; stage 4 "
                        + "imports: name: Import/Package Errors; errors: cannot find symbol → missing import or wrong "
                        + "type, package does not exist → missing library; fix: patch_file add import or add_library; "
                        + "stage 5 java: name: Java Compilation Errors; errors: incompatible types → type mismatch or "
                        + "missing cast, already defined → duplicate variable/method, unreachable statement → code after "
                        + "return; fix: patch_file Java code; stage 6 runtime: name: Runtime Crashes; errors: "
                        + "NullPointerException → null check missing, IndexOutOfBoundsException → bounds check missing, "
                        + "NetworkOnMainThreadException → move to background; tool: get_recent_logs, patch_file; fix "
                        + "order: Always follow: Stage 1 → 2 → 3 → 4 → 5 → 6. Never skip or reorder.",
                "aapt aarrggbb add add_color_resource add_library add_string_resource after already always "
                        + "android attr attributes background be bounds by cannot cast change check code color "
                        + "colorprimary com compilation crashes create defined does drawable duplicate error "
                        + "error_categories errors exist find fix fix_order fix_tool follow found get_recent_logs google "
                        + "id immediate import incompatible invalid java layout library malformed material method mismatch "
                        + "missing move name namespace never not null nullpointerexception on or package patch_file "
                        + "rebuild reorder resource resources return routing runtime sc_id should skip stage stage_1_aapt "
                        + "stage_2_resources stage_3_material stage_4_imports stage_5_java stage_6_runtime statement "
                        + "string symbol syntax tag to tool type types unclosed unreachable validate value ",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: _JAVA_COMPREHENSIVE.json
    private static void seed_java_comprehensive(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "_JAVA_COMPREHENSIVE",
                //  JAVA COMPREHENSIVE
                "editing rules: read requirement: MANDATORY — get_screen_source(sc_id, activity) before ANY "
                        + "patch; patch strategy: Minimal targeted change using patch_file; old str requirement: Must be "
                        + "UNIQUE in file (use 3-5 lines context if needed); write last resort: Only for new files or >60% "
                        + "changes; viewbinding: Use binding.viewId not findViewById(R.id.viewId); java version 17: "
                        + "allowed: records, switch expressions, text blocks, sealed classes; forbidden: Kotlin syntax, "
                        + "Compose syntax; imports: rule: Always add import when adding new API usage; remove unused: "
                        + "Tools can auto-remove unused imports after build; main thread: forbidden: Network calls, File "
                        + "I/O, Database queries; solution: Use Handler, Thread, or Coroutines (if available)",
                "60 activity add adding after allowed always any api auto available be before binding blocks "
                        + "build calls can change changes classes compose context coroutines database editing_rules "
                        + "expressions file files findviewbyid for forbidden get_screen_source handler id if import "
                        + "imports in java_version_17 kotlin lines main_thread mandatory minimal must needed network new "
                        + "not old_str_requirement only or patch patch_file patch_strategy queries read_requirement "
                        + "records remove remove_unused rule sc_id sealed solution switch syntax targeted text thread "
                        + "tools unique unused usage use using viewbinding viewid when write_last_resort",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: _LIBRARY_COMPREHENSIVE.json
    private static void seed_library_comprehensive(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "_LIBRARY_COMPREHENSIVE",
                //  LIBRARY COMPREHENSIVE
                "maven libraries: search first: search_maven(library_name) to find latest STABLE; validate: "
                        + "validate_libraries(sc_id, new_lib) to detect conflicts; attach: download_dependency(sc_id, "
                        + "groupId:artifactId:version); verify: build_project(sc_id) to check compilation; local "
                        + "libraries: formats: AAR (recommended), JAR, DEX; attach: attach_local_library(sc_id, lib_path); "
                        + "rule: Never attach duplicate packages or two versions of same package; conflict detection: "
                        + "duplicate classes: Detected by validate_libraries; version conflicts: Multiple versions of same "
                        + "groupId; androidx conflicts: Mix of androidx and old support library; resolution: Always prefer "
                        + "newest stable version; removal procedure: list_libraries(sc_id) to see what's attached, "
                        + "global_search(sc_id, library_package) to find usages, Confirm 'Remove [lib]? Used in N files.', "
                        + "remove_library(sc_id, name), build_project(sc_id) to verify",
                "aar always and androidx androidx_conflicts artifactid attach attach_local_library attached "
                        + "build_project by check compilation confirm conflict_detection conflicts detect detected dex "
                        + "download_dependency duplicate duplicate_classes files find formats global_search groupid in jar "
                        + "latest lib lib_path library library_name library_package list_libraries local_libraries "
                        + "maven_libraries mix multiple name never new_lib newest of old or package packages prefer "
                        + "recommended removal_procedure remove remove_library resolution rule same sc_id search_first "
                        + "search_maven see stable support to two usages used validate validate_libraries verify version "
                        + "version_conflicts versions what",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: _MANIFEST_COMPREHENSIVE.json
    private static void seed_manifest_comprehensive(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "_MANIFEST_COMPREHENSIVE",
                //  MANIFEST COMPREHENSIVE
                "manifest operations: add permission: method: add_permission(sc_id, permission_name); example: "
                        + "android.permission.INTERNET; rule: Never add duplicate permissions; add activity: method: "
                        + "create_activity(sc_id, activity_name); manifest update: Automatic by tool; rule: Activity name "
                        + "is PascalCase in manifest; add service: method: AndroidManifest edit via patch_file; structure: "
                        + "<service android:name=\".PackageName\"/>; add receiver: method: Edit manifest via patch_file; "
                        + "rule: Set android:exported appropriately; update min sdk: method: Edit manifest via patch_file; "
                        + "rule: Must match library requirements; manifest rules: Never delete <queries> on API 30+, "
                        + "Always set android:exported on components API 31+, Keep minSdk, targetSdk, compileSdk in sync",
                "30 31 activity activity_name add add_activity add_permission add_receiver add_service always "
                        + "android androidmanifest api appropriately automatic by compilesdk components create_activity "
                        + "delete duplicate edit example exported in internet is keep library manifest manifest_operations "
                        + "manifest_rules manifest_update match method minsdk must name never on packagename pascalcase "
                        + "patch_file permission permission_name permissions queries requirements rule sc_id service set "
                        + "structure sync targetsdk tool update_min_sdk via",
                KnowledgeStore.Priority.NORMAL);
    }

    // Source: _RESOURCES_COMPREHENSIVE.json
    private static void seed_resources_comprehensive(KnowledgeStore store) {
        store.upsert(KnowledgeStore.Category.TOOL, "_RESOURCES_COMPREHENSIVE",
                //  RESOURCES COMPREHENSIVE
                "string resources: file: files/resource/values/strings.xml; method: add_string_resource(sc_id, "
                        + "name, value); naming: snake_case, no spaces; reference xml: @string/name; reference java: "
                        + "R.string.name; localization: create_locale_strings(sc_id, 'ar', {key: 'Arabic value'}); color "
                        + "resources: file: files/resource/values/colors.xml; method: add_color_resource(sc_id, name, "
                        + "#AARRGGBB); preference: Use ?attr/colorPrimary over hardcoded #FFFFFF; material palette: "
                        + "colorPrimary, colorSecondary, colorSurface, colorError; drawable resources: formats: vector "
                        + "XML, shape XML, bitmap PNG, selector XML; method: create_drawable(sc_id, name, xml_content); "
                        + "rule: Prefer vector drawable for scalability; layout resources: file: files/resource/layouts/; "
                        + "method: add_view_xml(sc_id, activity, xml_snippet); naming: activity_name.xml; critical: Always "
                        + "use @+id/ for new views; style resources: file: files/resource/values/styles.xml; method: "
                        + "write_raw_resource_file(path, content); rule: Extend existing style, never duplicate names; "
                        + "theme resources: file: files/resource/values/themes.xml, values-night/themes.xml; rule: ALWAYS "
                        + "update both light and dark themes",
                "aarrggbb activity activity_name add_color_resource add_string_resource add_view_xml always and "
                        + "ar arabic attr bitmap both color_resources colorerror colorprimary colors colorsecondary "
                        + "colorsurface content create_drawable create_locale_strings critical dark drawable "
                        + "drawable_resources duplicate existing extend ffffff file files for formats hardcoded id key "
                        + "layout_resources layouts light localization material_palette method name names naming never new "
                        + "night no over path png prefer preference reference_java reference_xml resource rule sc_id "
                        + "scalability selector shape snake_case spaces string string_resources strings style "
                        + "style_resources styles theme_resources themes update use value values vector views "
                        + "write_raw_resource_file xml xml_content xml_snippet",
                KnowledgeStore.Priority.NORMAL);
    }

}