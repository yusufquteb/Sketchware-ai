package pro.sketchware.ai.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.ai.api.ToolDefinition;

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

    // ════════════════════════════════════════════════════════════════════════
    // Global registry — all tools available in the global (multi-project) context
    // ════════════════════════════════════════════════════════════════════════

    public static ToolRegistry createGlobal() {
        ToolRegistry registry = new ToolRegistry();

        // ── Project Management ────────────────────────────────────────────
        registry.register(new ProjectTools.ListProjectsTool());
        registry.register(new ProjectTools.GetProjectInfoTool());
        // Phase 4: single comprehensive audit in one call
        registry.register(new ProjectHealthTool.CheckProjectHealthTool());
        registry.register(new ProjectTools.CreateProjectTool());
        registry.register(new ProjectTools.DeleteProjectTool());
        registry.register(new ProjectTools.DuplicateProjectTool());
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

        // ── Project Indexer ───────────────────────────────────────────────
        registry.register(new ProjectIndexerTool());

        // ── Activities / Screens ──────────────────────────────────────────
        registry.register(new ActivityTools.ListActivitiesTool());
        registry.register(new ActivityTools.GetScreenSourceTool());
        registry.register(new ActivityTools.CreateActivityTool());
        registry.register(new ActivityTools.DeleteActivityTool());

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

        // ── Library Management ────────────────────────────────────────────
        registry.register(new LibraryTools.ListLibrariesTool());
        registry.register(new LibraryTools.ValidateLibrariesTool());
        registry.register(new LibraryTools.AddLibraryTool());
        registry.register(new LibraryTools.RemoveLibraryTool());
        registry.register(new LibraryTools.AttachLocalLibraryTool());
        registry.register(new LibraryTools.DetachLocalLibraryTool());
        registry.register(new LibraryTools.DownloadDependencyTool());

        // ── Library Discovery (search + dependency scan) ──────────────────
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

        // ── Project Templates & Localization ─────────────────────────────
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

        // ── UI Layout — describe, generate, add_xml, batch_patch, replace_subtree ──
        registry.register(new DesignXmlEditorTool.DescribeLayoutTool());
        // Preferred: XML-based tools using ViewBeanParser (matches Sketchware-IA approach)
        registry.register(new DesignXmlEditorTool.AddViewXmlTool());
        registry.register(new DesignXmlEditorTool.GenerateLayoutTool());
        // Phase 3: XML Patch Engine — surgical multi-view edits in one cycle
        registry.register(new DesignXmlEditorTool.BatchPatchViewsTool());
        registry.register(new DesignXmlEditorTool.ReplaceSubtreeTool());

        // ── Developer Utilities (web search, shell, logcat, resource scan) ─

        registry.register(new DevTools.WebSearchTool());
        registry.register(new DevTools.LogcatFilterTool());
        registry.register(new DevTools.ResourceOptimizerTool());

        // ── GitHub Intelligence Tools ────────────────────────────────────────
        registry.register(new AI_GitHub_Analyzer.GitHubCompareTool());
        registry.register(new AI_GitHub_Analyzer.GitHubSearchTool());

        return registry;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Per-project registry — subset of tools scoped to a single project
    // ════════════════════════════════════════════════════════════════════════

    public static ToolRegistry createForProject(String projectId) {
        ToolRegistry registry = new ToolRegistry();

        // ── Project Management ────────────────────────────────────────────
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

        // ── Surgical File Mutation ────────────────────────────────────────
        registry.register(new FileTools.PatchFileTool());
        registry.register(new FileTools.AppendCodeTool());
        registry.register(new FileTools.InsertCodeAtLineTool());
        registry.register(new FileTools.ReadFileRangeTool());

        // ── Smart File Search ─────────────────────────────────────────────
        registry.register(new FileSearchTools.SearchInFileTool());

        // ── Activities / Screens ──────────────────────────────────────────
        registry.register(new ActivityTools.ListActivitiesTool());
        registry.register(new ActivityTools.GetScreenSourceTool());
        registry.register(new ActivityTools.CreateActivityTool());
        registry.register(new ActivityTools.DeleteActivityTool());

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

        // ── Code Analysis & Quality ───────────────────────────────────────
        registry.register(new CodeAnalysisTools.AnalyzeCodeTool());
        registry.register(new CodeAnalysisTools.ReviewSourceCodeTool());
        registry.register(new CodeAnalysisTools.ValidateRtlLayoutTool());

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

        // ── UI Layout — describe, generate, add_xml, batch_patch, replace_subtree ──
        registry.register(new DesignXmlEditorTool.DescribeLayoutTool());
        registry.register(new DesignXmlEditorTool.AddViewXmlTool());
        registry.register(new DesignXmlEditorTool.GenerateLayoutTool());
        // Phase 3: XML Patch Engine
        registry.register(new DesignXmlEditorTool.BatchPatchViewsTool());
        registry.register(new DesignXmlEditorTool.ReplaceSubtreeTool());

        // ── GitHub Intelligence Tools ────────────────────────────────────────
        registry.register(new AI_GitHub_Analyzer.GitHubCompareTool());
        registry.register(new AI_GitHub_Analyzer.GitHubSearchTool());

        return registry;
    }
}
