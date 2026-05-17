package pro.sketchware.activities.projecttools;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import mod.jbk.diagnostic.CompileErrorSaver;
import pro.sketchware.compiler.support.BuildCacheKey;
import pro.sketchware.compiler.support.KotlinCompileSupport;
import pro.sketchware.compiler.support.LibDexCache;
import pro.sketchware.compiler.support.R8CompileSupport;
import pro.sketchware.compiler.support.ViewBindingSupport;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.diagnostics.CompileDiagnostic;
import pro.sketchware.utility.diagnostics.CompileErrorCapture;
import pro.sketchware.utility.diagnostics.ErrorFixHelper;
import pro.sketchware.utility.diagnostics.MemoryUtil;
import pro.sketchware.utility.io.SafeFileOps;

public class BuildDiagnosticsActivity extends BaseAppCompatActivity {
    private String scId;
    private TextView outputView;
    @Override public void onCreate(Bundle savedInstanceState) { enableEdgeToEdgeNoContrast(); super.onCreate(savedInstanceState); scId = getIntent().getStringExtra(ProjectToolsHubActivity.EXTRA_SC_ID); if (scId == null || scId.trim().isEmpty()) { SketchwareUtil.toastError("Project id missing"); finish(); return; } LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle("Compiler Diagnostics"); toolbar.setSubtitle("Project " + scId); toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material); toolbar.setNavigationOnClickListener(v -> finish()); root.addView(toolbar); ScrollView scrollView = new ScrollView(this); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); int pad = dp(16); content.setPadding(pad, pad, pad, pad * 2); scrollView.addView(content); root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)); MaterialButton refresh = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle); refresh.setText("Refresh diagnostics"); refresh.setOnClickListener(v -> refreshDiagnostics()); content.addView(refresh); outputView = new TextView(this); outputView.setTextIsSelectable(true); outputView.setGravity(Gravity.START); outputView.setPadding(0, dp(12), 0, 0); content.addView(outputView); setContentView(root); refreshDiagnostics(); }
    private void refreshDiagnostics() { try { StringBuilder out = new StringBuilder(); File dataDir = ProjectToolPaths.getProjectDataDir(scId); File generatedApp = ProjectToolPaths.getProjectGeneratedAppDir(scId); File generatedJava = ProjectToolPaths.getProjectGeneratedJavaDir(scId); File generatedRes = ProjectToolPaths.getProjectGeneratedResDir(scId); out.append("Project data: ").append(dataDir.getAbsolutePath()).append('\n'); out.append("Generated app: ").append(generatedApp.getAbsolutePath()).append('\n'); out.append("Generated app exists: ").append(generatedApp.exists()).append("\n\n"); List<String> kotlinSources = new ArrayList<>(); kotlinSources.addAll(KotlinCompileSupport.sourcePaths(dataDir)); if (generatedApp.exists()) kotlinSources.addAll(KotlinCompileSupport.sourcePaths(generatedApp)); out.append("Kotlin source files: ").append(kotlinSources.size()).append('\n'); for (int i = 0; i < Math.min(30, kotlinSources.size()); i++) out.append("• ").append(kotlinSources.get(i)).append('\n'); if (kotlinSources.size() > 30) out.append("…").append(kotlinSources.size() - 30).append(" more\n"); out.append('\n'); List<File> cacheInputs = new ArrayList<>(); collectIfFile(cacheInputs, ProjectToolPaths.getProjectGeneratedManifestFile(scId)); collectIfFile(cacheInputs, ProjectToolPaths.getProjectGeneratedAppGradleFile(scId)); collectIfFile(cacheInputs, ProjectToolPaths.getProjectGeneratedProjectGradleFile(scId)); collectIfFile(cacheInputs, ProjectToolPaths.getProjectGeneratedGradlePropertiesFile(scId)); if (generatedJava.exists()) cacheInputs.addAll(SafeFileOps.listFilesRecursively(generatedJava)); out.append("Build cache input files: ").append(cacheInputs.size()).append('\n'); if (!cacheInputs.isEmpty()) out.append("Cache key: ").append(BuildCacheKey.forFiles(cacheInputs)).append('\n'); out.append('\n'); File layoutDir = new File(generatedRes, "layout"); List<File> layouts = layoutDir.exists() ? SafeFileOps.listFilesRecursively(layoutDir) : new ArrayList<>(); out.append("ViewBinding classes from generated layouts: ").append(layouts.size()).append('\n'); for (File layout : layouts) if (layout.getName().endsWith(".xml")) out.append("• ").append(layout.getName()).append(" → ").append(ViewBindingSupport.bindingClassName(layout)).append('\n'); out.append('\n'); File inputJar = new File(generatedApp, "build/intermediates/classes.jar"); File outputZip = new File(generatedApp, "build/outputs/r8/classes.zip"); File androidJar = new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware/android.jar"); File rules = new File(generatedApp, "proguard-rules.pro"); out.append("R8 command preview:\n"); for (String arg : R8CompileSupport.command(inputJar, outputZip, androidJar, rules.exists() ? rules : null)) out.append("  ").append(arg).append('\n'); out.append('\n'); MemoryUtil.Snapshot memory = MemoryUtil.snapshot(); out.append("Runtime memory: ").append(memory.toString()).append("\n\n"); CompileErrorSaver saver = new CompileErrorSaver(scId); String lastLog = saver.getLogsFromFile(); List<CompileDiagnostic> diagnostics = CompileErrorCapture.parse(lastLog); out.append("Saved compile diagnostics parsed: ").append(diagnostics.size()).append("\n"); for (int i = 0; i < Math.min(20, diagnostics.size()); i++) { CompileDiagnostic diagnostic = diagnostics.get(i); out.append("• ").append(diagnostic.toString()).append("\n"); for (String suggestion : ErrorFixHelper.suggestionsFor(diagnostic)) out.append("  - ").append(suggestion).append("\n"); } if (diagnostics.size() > 20) out.append("…").append(diagnostics.size() - 20).append(" more diagnostics\n"); out.append('\n'); LibDexCache dexCache = new LibDexCache(ProjectToolPaths.getProjectLibDexCacheDir(scId)); out.append("Library dex cache: ").append(ProjectToolPaths.getProjectLibDexCacheDir(scId).getAbsolutePath()).append('\n'); File localLibRoot = new File(dataDir, "local_libraries"); if (localLibRoot.exists()) for (File file : SafeFileOps.listFilesRecursively(localLibRoot)) if (file.getName().endsWith(".jar") || file.getName().endsWith(".aar")) out.append("• ").append(file.getName()).append(" valid cache: ").append(dexCache.isValid(file)).append('\n'); outputView.setText(out.toString()); } catch (Exception e) { outputView.setText("Diagnostics failed: " + e.getMessage()); } }
    private void collectIfFile(List<File> files, File file) { if (file != null && file.isFile()) files.add(file); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
