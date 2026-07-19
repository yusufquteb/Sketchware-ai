package pro.sketchware.ai.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.ai.api.ToolDefinition;
import pro.sketchware.ai.tools.audit.MaterialAuditTool;
import pro.sketchware.ai.tools.audit.RtlAuditTool;
import pro.sketchware.ai.tools.snapshot.CreateSnapshotTool;
import pro.sketchware.ai.tools.snapshot.ListSnapshotsTool;
import pro.sketchware.ai.tools.snapshot.RestoreSnapshotTool;

import pro.sketchware.ai.tools.project.AI_GitHub_Analyzer;
import pro.sketchware.ai.tools.project.ActivityContentSeedingTools;
import pro.sketchware.ai.tools.project.ActivityTools;
import pro.sketchware.ai.tools.project.ExportToAndroidStudioTool;
import pro.sketchware.ai.tools.project.ManifestTools;
import pro.sketchware.ai.tools.project.ProjectHealthTool;
import pro.sketchware.ai.tools.project.ProjectIndexerTool;
import pro.sketchware.ai.tools.project.ProjectTemplateTools;
import pro.sketchware.ai.tools.project.ProjectTools;

import pro.sketchware.ai.tools.code.CodeAnalysisTools;
import pro.sketchware.ai.tools.code.DesignXmlEditorTool;
import pro.sketchware.ai.tools.code.DevTools;
import pro.sketchware.ai.tools.code.DrawableTools;
import pro.sketchware.ai.tools.code.FileSearchTools;
import pro.sketchware.ai.tools.code.FileTools;
import pro.sketchware.ai.tools.code.LayoutTools;
import pro.sketchware.ai.tools.code.LintTools;
import pro.sketchware.ai.tools.code.LogicSyntaxCheckerTool;

import pro.sketchware.ai.tools.build.AdvancedBuildTool;
import pro.sketchware.ai.tools.build.BuildRepairTool;
import pro.sketchware.ai.tools.build.BuildTools;
import pro.sketchware.ai.tools.build.CompileTools;

import pro.sketchware.ai.tools.resource.ResourceTools;
import pro.sketchware.ai.tools.resource.UnusedResourcesTool;

import pro.sketchware.ai.tools.library.LibraryDiscoveryTools;
import pro.sketchware.ai.tools.library.LibraryTools;

/**
 * Registry that holds all available AI agent tools.
 *
 * Tool organization:
 *   - CodeAnalysisTools    → analyze_code, review_source_code, validate_rtl_layout
 *   - ProjectTemplateTools → create_from_template, add_locale_strings
 *   - LibraryDiscoveryTools→ search_maven, scan_dependencies
 *   - FileSearchTools      → search_in_file (grep-like, token-efficient)
 *   - DevTools             → web_search, logcat_filter, resource_optimizer
 */
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public void register(AgentTool tool) {
        if (tool == null) throw new IllegalArgumentException("Tool must not be null");
        if (tools.containsKey(tool.getName())) {
            android.util.Log.w("ToolRegistry", "Tool already registered: " + tool.getName());
            return;
        }
        tools.put(tool.getName(), tool);
    }

    public AgentTool getTool(String name) {
        return tools.get(name);
    }

    public List<AgentTool> getAllTools() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }

    public List<ToolDefinition> getToolDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            definitions.add(new ToolDefinition(
                    tool.getName(),
                    tool.getDescription(),
                    tool.getParametersSchema()
            ));
        }
        return definitions;
    }

    /**
     * Context-budget tiers for tool advertising. Previously every provider — a 4096-token
     * on-device model and a 200,000-token cloud model alike — received the exact same fixed
     * tool subset. That was wrong in both directions: it was the right size for nothing bigger
     * than the local model, so cloud providers with 30-50x the headroom still had to spend a
     * round trip on list_tools to reach github_search, git tools, snapshots, etc. — the same
     * "AI doesn't seem to know its own tools" complaint the tiny offline budget was supposedly
     * protecting against. Tying the tool set to {@link pro.sketchware.ai.core.ProviderCapabilities#maxContextTokens}
     * (already resolved once per request by ModelCapabilities.resolve — see AgentExecutor's
     * caller) fixes both: tiny-context providers stay inside their hard cap, and large-context
     * providers get the full catalog directly instead of gated behind discovery.
     *
     * Thresholds:
     *   - TINY   (&lt;= 4,096 tokens, e.g. LOCAL_LLM's hard 4096-token KV cache): the smallest
     *     safe set. list_tools is still always included so nothing is permanently unreachable.
     *   - MEDIUM (&lt;= 40,000 tokens, e.g. Cerebras/Mistral/Scaleway/HuggingFace/Hyperbolic/
     *     Morph/Novita/Fireworks — the 8K-32K-context providers): TINY plus the next most
     *     commonly needed tools (web/GitHub search, layout generation, resource/library basics),
     *     roughly 20 tools — still well short of the full catalog but no longer forcing a
     *     discovery round trip for everyday actions.
     *   - LARGE  (&gt; 40,000 tokens, e.g. OpenAI/Anthropic/Gemini/Groq/DeepSeek/OpenRouter/
     *     NVIDIA/Chutes/XAI — all comfortably &gt;= 64K context): the full 100+-tool catalog,
     *     sent directly. No list_tools indirection needed at all.
     */
    public static final int TINY_CONTEXT_THRESHOLD_TOKENS = 4_096;
    public static final int MEDIUM_CONTEXT_THRESHOLD_TOKENS = 40_000;

    /**
     * TINY tier — see class-level tier javadoc above. Kept intentionally small: file/project
     * orientation, one search tool, one build tool, one UI-read tool, plus discovery.
     *
     * Names verified exact against each tool's own getName() implementation:
     *   - get_project_info      → ProjectTools.GetProjectInfoTool
     *   - read_file              → FileTools.ReadFileTool
     *   - list_files             → FileTools.ListFilesTool
     *   - global_search          → FileTools.GlobalSearchTool
     *   - get_project_structure  → CompileTools.GetProjectStructureTool (folder/file tree in one
     *                              call — the direct alternative to guessing or asking the user
     *                              for java/res/assets paths; see also the static path block
     *                              AgentExecutor injects into the system prompt for the same
     *                              reason).
     *   - build_project          → BuildTools.BuildProjectTool
     *   - describe_layout        → DesignXmlEditorTool.DescribeLayoutTool
     *   - list_tools             → meta.ListToolsTool (always-advertised discovery entry point)
     */
    private static final List<String> TINY_TOOL_NAMES = java.util.Arrays.asList(
            "get_project_info",
            "read_file",
            "list_files",
            "global_search",
            "get_project_structure",
            "build_project",
            "describe_layout",
            "list_tools"
    );

    /**
     * MEDIUM tier — TINY plus the tools that were the specific subject of the "tool usage"
     * complaint this tiering was built to fix (internet search, GitHub search, UI generation),
     * plus the next-most-common project actions. Still far short of the full catalog; anything
     * not here stays reachable through list_tools.
     */
    private static final List<String> MEDIUM_TOOL_NAMES = java.util.Arrays.asList(
            "get_project_info", "read_file", "write_file", "list_files", "global_search",
            "get_project_structure", "build_project", "describe_layout", "list_tools",
            "web_search", "github_search", "generate_layout", "add_view_xml",
            "list_activities", "get_screen_source", "create_activity",
            "add_string_resource", "list_libraries", "add_library",
            "analyze_build_error", "check_project_health"
    );

    private AgentTool byName(String name) {
        return tools.get(name);
    }

    private List<ToolDefinition> definitionsFor(List<String> names) {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (String name : names) {
            AgentTool tool = byName(name);
            if (tool != null) {
                definitions.add(new ToolDefinition(
                        tool.getName(), tool.getDescription(), tool.getParametersSchema()));
            }
            // Missing name (e.g. tool renamed later) is skipped silently, not thrown — keeps
            // this safe against future tool renames without a hard crash.
        }
        return definitions;
    }

    /**
     * Picks the right tier for the given provider's actual context window. See the tier javadoc
     * above {@link #TINY_TOOL_NAMES} for the reasoning and thresholds.
     *
     * @param maxContextTokens the resolved provider/model context window
     *                         ({@code ProviderCapabilities.maxContextTokens}); 0/unknown is
     *                         treated as TINY (safest default — never assume headroom you
     *                         haven't confirmed).
     */
    public List<ToolDefinition> getToolsForContextBudget(int maxContextTokens) {
        if (maxContextTokens > MEDIUM_CONTEXT_THRESHOLD_TOKENS) {
            return getToolDefinitions(); // LARGE tier: full catalog, no discovery indirection.
        }
        if (maxContextTokens > TINY_CONTEXT_THRESHOLD_TOKENS) {
            return definitionsFor(MEDIUM_TOOL_NAMES);
        }
        return definitionsFor(TINY_TOOL_NAMES);
    }

    /**
     * @deprecated kept only so any other caller that hasn't been migrated to
     * {@link #getToolsForContextBudget(int)} still compiles and gets the safe TINY tier rather
     * than breaking outright. New/updated call sites should pass the resolved context window.
     */
    @Deprecated
    public List<ToolDefinition> getEssentialTools() {
        return definitionsFor(TINY_TOOL_NAMES);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Common tools — shared by both createGlobal() and createForProject().
    // Task 2: extracted to remove ~90 lines of duplicated registration code.
    // Order below matches the original createGlobal() ordering exactly, since
    // that was the more complete of the two (see CHANGES.md for the one
    // relative-order difference this introduces in createForProject()).
    // ════════════════════════════════════════════════════════════════════════

    private static void registerCommonTools(ToolRegistry registry) {
        // ── Meta / Discovery ─────────────────────────────────────────────────
        // Always-advertised entry point for discovering the rest of the registry
        // (see ToolRegistry.getEssentialTools() — the MVP context-budget fix).
        registry.register(new pro.sketchware.ai.tools.meta.ListToolsTool(registry));

        // ── Project Management (shared subset) ─────────────────────────────
        registry.register(new ProjectTools.GetProjectInfoTool());
        // Phase 4: single comprehensive audit in one call
        registry.register(new ProjectHealthTool.CheckProjectHealthTool());
        registry.register(new ProjectTools.AddPermissionTool());
        registry.register(new ProjectTools.AddActivityTool());

        // ── File Operations ───────────────────────────────────────────────
        registry.register(new FileTools.ReadFileTool());
        registry.register(new FileTools.WriteFileTool());
        registry.register(new FileTools.DeleteFileTool());
        registry.register(new FileTools.ListFilesTool());
        registry.register(new FileTools.CopyFileTool());
        registry.register(new FileTools.MoveFileTool());
        registry.register(new FileTools.GlobalSearchTool());
        registry.register(new FileTools.GetRecentLogsTool());

        // ── Surgical File Mutation (Executor Tools) ───────────────────────
        registry.register(new FileTools.PatchFileTool());
        registry.register(new FileTools.AppendCodeTool());
        registry.register(new FileTools.InsertCodeAtLineTool());
        registry.register(new FileTools.ReadFileRangeTool());

        // ── Smart File Search (grep-like, token-efficient) ────────────────
        registry.register(new FileSearchTools.SearchInFileTool());

        // ── Activities / Screens ──────────────────────────────────────────
        registry.register(new ActivityTools.ListActivitiesTool());
        registry.register(new ActivityTools.GetScreenSourceTool());
        registry.register(new ActivityTools.CreateActivityTool());
        registry.register(new ActivityTools.DeleteActivityTool());
        // Added Phase 3 — fills the gap left by CreateActivityTool (registers the
        // ProjectFileBean but writes no view/logic/layout content). See
        // ActivityContentSeedingTools' class javadoc for the full verified reasoning.
        registry.register(new ActivityContentSeedingTools.SeedBlankActivityContentTool());

        // ── UI Layout ─────────────────────────────────────────────────────
        registry.register(new DesignXmlEditorTool.AddViewTool());
        registry.register(new DesignXmlEditorTool.ModifyViewTool());
        registry.register(new DesignXmlEditorTool.RemoveViewTool());
        registry.register(new LayoutTools.GetLayoutTool());
        registry.register(new LayoutTools.EditLayoutTool());

        // ── Resources ────────────────────────────────────────────────────
        registry.register(new ResourceTools.AddStringResourceTool());
        registry.register(new ResourceTools.AddColorResourceTool());
        registry.register(new ResourceTools.ListResourcesTool());
        registry.register(new ResourceTools.ReadRawResourceFileTool());
        registry.register(new ResourceTools.WriteRawResourceFileTool());
        registry.register(new ResourceTools.ExtractStringsTool());
        registry.register(new ResourceTools.CreateLocaleStringsTool());
        // Unused resource scanner + cleaner
        registry.register(new UnusedResourcesTool.ScanUnusedResourcesTool());
        registry.register(new UnusedResourcesTool.DeleteUnusedResourcesTool());
        // Drawable creation
        registry.register(new DrawableTools.CreateDrawableTool());

        // ── Build & Compile ───────────────────────────────────────────────
        registry.register(new CompileTools.GetCompileLogsTool());
        registry.register(new CompileTools.GetProjectStructureTool());
        registry.register(new BuildTools.BuildProjectTool());
        // Enhanced build: R8 minification, parallel ECJ, dexer configuration
        registry.register(new AdvancedBuildTool.SetBuildCompilerTool());
        registry.register(new AdvancedBuildTool.BuildWithR8Tool());
        registry.register(new BuildRepairTool.AnalyzeBuildErrorTool());
        // Phase 4: install + launch + crash-on-launch check only (scope-limited,
        // see RunAndVerifyOnDeviceTool's class javadoc). Requires root; fails
        // explicitly rather than silently no-op-ing without it.
        registry.register(new pro.sketchware.ai.tools.build.RunAndVerifyOnDeviceTool.RunAndVerifyTool());

        // ── Library Management ────────────────────────────────────────────
        registry.register(new LibraryTools.ListLibrariesTool());
        registry.register(new LibraryTools.ValidateLibrariesTool());
        registry.register(new LibraryTools.AddLibraryTool());
        registry.register(new LibraryTools.RemoveLibraryTool());
        registry.register(new LibraryTools.AttachLocalLibraryTool());
        registry.register(new LibraryTools.DetachLocalLibraryTool());
        registry.register(new LibraryTools.DownloadDependencyTool());

        // ── Library Discovery ─────────────────────────────────────────────
        registry.register(new LibraryDiscoveryTools.SearchMavenTool());
        registry.register(new LibraryDiscoveryTools.DependencyScanTool());
        // Phase 3: Gradle injection validation
        registry.register(new LibraryDiscoveryTools.ValidateGradleDependencyTool());

        // ── Export ────────────────────────────────────────────────────────
        registry.register(new ExportToAndroidStudioTool());

        // ── Code Analysis & Quality ───────────────────────────────────────
        registry.register(new CodeAnalysisTools.AnalyzeCodeTool());
        registry.register(new CodeAnalysisTools.ReviewSourceCodeTool());
        registry.register(new CodeAnalysisTools.ValidateRtlLayoutTool());

        // ── Project Templates ─────────────────────────────────────────────
        registry.register(new ProjectTemplateTools.CreateFromTemplateTool());
        registry.register(new ProjectTemplateTools.AddLocaleStringsTool());

        // ── Block Logic API ───────────────────────────────────────────────
        registry.register(new pro.sketchware.ai.tools.blocks.BlockApiTools.GetActivityEventsTool());
        registry.register(new pro.sketchware.ai.tools.blocks.BlockApiTools.GetEventBlocksTool());
        // Phase 3: block graph serializer
        registry.register(new pro.sketchware.ai.tools.blocks.BlockApiTools.DescribeBlockLogicTool());
        registry.register(new pro.sketchware.ai.tools.blocks.BlockApiTools.AddBlockTool());
        registry.register(new pro.sketchware.ai.tools.blocks.BlockApiTools.ModifyBlockTool());
        registry.register(new pro.sketchware.ai.tools.blocks.BlockApiTools.DeleteBlockTool());
        registry.register(new pro.sketchware.ai.tools.blocks.BlockApiTools.GetMoreBlocksTool());
        registry.register(new pro.sketchware.ai.tools.blocks.BlockApiTools.CreateMoreBlockTool());
        registry.register(new pro.sketchware.ai.tools.blocks.BlockApiTools.DeleteMoreBlockTool());
        // Phase 4: bulk block replacement + undo
        registry.register(new pro.sketchware.ai.tools.blocks.BlockApiTools.SetEventLogicTool());
        registry.register(new pro.sketchware.ai.tools.blocks.BlockApiTools.UndoBlocksTool());
        // Phase 4: variable & component CRUD
        registry.register(new pro.sketchware.ai.tools.blocks.VariableAndComponentTools.GetVariablesTool());
        registry.register(new pro.sketchware.ai.tools.blocks.VariableAndComponentTools.AddVariableTool());
        registry.register(new pro.sketchware.ai.tools.blocks.VariableAndComponentTools.DeleteVariableTool());
        registry.register(new pro.sketchware.ai.tools.blocks.VariableAndComponentTools.GetComponentsTool());
        registry.register(new pro.sketchware.ai.tools.blocks.VariableAndComponentTools.AddComponentTool());
        registry.register(new pro.sketchware.ai.tools.blocks.VariableAndComponentTools.DeleteComponentTool());

        // ── UI Layout — describe, generate, add_xml, batch_patch, replace_subtree ──
        registry.register(new DesignXmlEditorTool.DescribeLayoutTool());
        registry.register(new DesignXmlEditorTool.AddViewXmlTool());
        registry.register(new DesignXmlEditorTool.GenerateLayoutTool());
        // Phase 3: XML Patch Engine
        registry.register(new DesignXmlEditorTool.BatchPatchViewsTool());
        registry.register(new DesignXmlEditorTool.ReplaceSubtreeTool());

        // ── Developer Utilities ───────────────────────────────────────────────
        registry.register(new DevTools.WebSearchTool());
        registry.register(new DevTools.LogcatFilterTool());
        registry.register(new DevTools.ResourceOptimizerTool());

        // ── Manifest Tools ───────────────────────────────────────────────────
        registry.register(new ManifestTools.ReadManifestTool());
        registry.register(new ManifestTools.EditManifestAttributeTool());
        registry.register(new ManifestTools.AddManifestTagTool());

        // ── Lint / Static Analysis ────────────────────────────────────────────
        registry.register(new LintTools.RunLintTool());

        // ── Logic Syntax Checker ──────────────────────────────────────────────
        registry.register(new LogicSyntaxCheckerTool());

        // ── GitHub Intelligence Tools ────────────────────────────────────────
        registry.register(new AI_GitHub_Analyzer.GitHubCompareTool());
        registry.register(new AI_GitHub_Analyzer.GitHubSearchTool());

        // ── Local Git Tools (Phase 3 — no remote required; see LocalGitTools
        //    class javadoc for why the existing pro.sketchware.git package
        //    didn't already cover this) ────────────────────────────────────
        registry.register(new pro.sketchware.ai.tools.git.LocalGitTools.GitInitTool());
        registry.register(new pro.sketchware.ai.tools.git.LocalGitTools.GitStatusTool());
        registry.register(new pro.sketchware.ai.tools.git.LocalGitTools.GitAddTool());
        registry.register(new pro.sketchware.ai.tools.git.LocalGitTools.GitCommitTool());

        // ── Audit Tools (deterministic, no LLM) ──────────────────────────────
        registry.register(new RtlAuditTool());
        registry.register(new MaterialAuditTool());

        // ── Snapshot Tools ────────────────────────────────────────────────────
        registry.register(new ListSnapshotsTool());
        registry.register(new CreateSnapshotTool());
        registry.register(new RestoreSnapshotTool());
    }

    // ════════════════════════════════════════════════════════════════════════
    // Global registry — all tools available in the global (multi-project) context
    // ════════════════════════════════════════════════════════════════════════

    public static ToolRegistry createGlobal() {
        ToolRegistry registry = new ToolRegistry();

        // ── Project Management — tools unique to the global (multi-project)
        //    context; not part of registerCommonTools() ──────────────────
        registry.register(new ProjectTools.ListProjectsTool());
        registry.register(new ProjectTools.CreateProjectTool());
        registry.register(new ProjectTools.DeleteProjectTool());
        registry.register(new ProjectTools.DuplicateProjectTool());

        // ── Project Indexer — unique to global context ────────────────────
        registry.register(new ProjectIndexerTool());

        // ── Everything shared with createForProject() ─────────────────────
        registerCommonTools(registry);

        return registry;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Per-project registry — subset of tools scoped to a single project
    // ════════════════════════════════════════════════════════════════════════

    public static ToolRegistry createForProject(String projectId) {
        ToolRegistry registry = new ToolRegistry();

        // Task 2: createForProject() has no tools unique to it — every tool it
        // registered was also registered by createGlobal(). All of it now
        // comes from the shared method.
        registerCommonTools(registry);

        return registry;
    }
}
