package pro.sketchware.activities.projecttools;

import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.jC;
import a.a.a.lC;
import a.a.a.yB;
import mod.hilal.saif.android_manifest.AndroidManifestInjector;
import pro.sketchware.util.SketchwareFileDecryptor;
import pro.sketchware.util.SketchwareFileEncryptor;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Manages activities: import from another project, clone, rename.
 * Also updates AndroidManifest launcher + activity-component injections on rename.
 */
public class ActivityManagerActivity extends BaseAppCompatActivity {

    private String scId;
    private LinearLayout listContainer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        scId = getIntent().getStringExtra(ProjectToolsHubActivity.EXTRA_SC_ID);
        if (scId == null || scId.trim().isEmpty()) {
            SketchwareUtil.toastError("Project id missing");
            finish();
            return;
        }
        buildUi();
    }

    // ── UI ─────────────────────────────────────────────────────────────────────

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Activity Manager");
        toolbar.setSubtitle("Project " + scId);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, pad, pad, pad * 2);

        MaterialButton importBtn = new MaterialButton(this);
        importBtn.setText("Import Activity from Project");
        importBtn.setOnClickListener(v -> showPickProjectDialog());
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        importLp.setMargins(0, 0, 0, dp(16));
        content.addView(importBtn, importLp);

        TextView sectionLabel = new TextView(this);
        sectionLabel.setText("Existing Activities");
        sectionLabel.setTextSize(13f);
        sectionLabel.setAlpha(0.65f);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.setMargins(0, 0, 0, dp(6));
        content.addView(sectionLabel, labelLp);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer);

        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        loadAndDisplayActivities();
    }

    // ── Activity list ───────────────────────────────────────────────────────────

    private void loadAndDisplayActivities() {
        listContainer.removeAllViews();
        try {
            ArrayList<ProjectFileBean> beans = jC.b(scId).b();
            if (beans == null || beans.isEmpty()) { addEmptyLabel("No activities found."); return; }
            boolean found = false;
            for (ProjectFileBean bean : beans) {
                if (bean.fileType != ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) continue;
                found = true;
                listContainer.addView(buildActivityCard(bean.fileName));
            }
            if (!found) addEmptyLabel("No activities found.");
        } catch (Exception e) {
            SketchwareUtil.toastError("Load failed: " + e.getMessage());
        }
    }

    private void addEmptyLabel(String msg) {
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextSize(14f);
        tv.setAlpha(0.55f);
        listContainer.addView(tv);
    }

    private MaterialCardView buildActivityCard(String fileName) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(lp);
        card.setCardElevation(dp(1));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView nameView = new TextView(this);
        nameView.setText(ProjectFileBean.getActivityName(fileName));
        nameView.setTextSize(15f);
        nameView.setTypeface(nameView.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(nameView);

        TextView infoView = new TextView(this);
        infoView.setText(fileName + ".xml  •  " + ProjectFileBean.getJavaName(fileName));
        infoView.setTextSize(12f);
        infoView.setAlpha(0.6f);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.setMargins(0, dp(2), 0, dp(8));
        box.addView(infoView, infoLp);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton cloneBtn = new MaterialButton(
                this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cloneBtn.setText("Clone");
        cloneBtn.setTextSize(13f);
        LinearLayout.LayoutParams cloneLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cloneLp.setMarginEnd(dp(4));
        cloneBtn.setLayoutParams(cloneLp);
        cloneBtn.setOnClickListener(v -> showCloneDialog(fileName));

        MaterialButton renameBtn = new MaterialButton(
                this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        renameBtn.setText("Rename");
        renameBtn.setTextSize(13f);
        LinearLayout.LayoutParams renameLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        renameLp.setMarginStart(dp(4));
        renameBtn.setLayoutParams(renameLp);
        renameBtn.setOnClickListener(v -> showRenameDialog(fileName));

        if ("main".equals(fileName)) {
            renameBtn.setEnabled(false);
            renameBtn.setAlpha(0.38f);
        }

        btnRow.addView(cloneBtn);
        btnRow.addView(renameBtn);
        box.addView(btnRow);
        card.addView(box);
        return card;
    }

    // ── Import: step 1 — pick project ─────────────────────────────────────────

    private void showPickProjectDialog() {
        ArrayList<HashMap<String, Object>> projects = lC.a();
        if (projects == null || projects.isEmpty()) {
            SketchwareUtil.toastError("No other projects found.");
            return;
        }
        // Build display list (exclude current project)
        ArrayList<HashMap<String, Object>> filtered = new ArrayList<>();
        for (HashMap<String, Object> p : projects) {
            if (!scId.equals(yB.c(p, "sc_id"))) filtered.add(p);
        }
        if (filtered.isEmpty()) {
            SketchwareUtil.toastError("No other projects to import from.");
            return;
        }

        String[] names = new String[filtered.size()];
        for (int i = 0; i < filtered.size(); i++) {
            String wsName = yB.c(filtered.get(i), "my_ws_name");
            String appName = yB.c(filtered.get(i), "my_app_name");
            String id = yB.c(filtered.get(i), "sc_id");
            names[i] = wsName + (appName.isEmpty() ? "" : " – " + appName) + "  [" + id + "]";
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Source Project")
                .setItems(names, (d, which) -> {
                    String srcScId = yB.c(filtered.get(which), "sc_id");
                    showPickActivityDialog(srcScId, names[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Import: step 2 — pick activity ────────────────────────────────────────

    private void showPickActivityDialog(String srcScId, String projectLabel) {
        executor.execute(() -> {
            ArrayList<ProjectFileBean> srcBeans;
            try {
                srcBeans = jC.b(srcScId, true).b();
                // Restore current project immediately
                jC.b(scId, true);
            } catch (Exception e) {
                jC.b(scId, true);
                runOnUiThread(() -> SketchwareUtil.toastError("Could not load source project: " + e.getMessage()));
                return;
            }

            ArrayList<ProjectFileBean> activities = new ArrayList<>();
            if (srcBeans != null) {
                for (ProjectFileBean b : srcBeans) {
                    if (b.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) activities.add(b);
                }
            }
            if (activities.isEmpty()) {
                runOnUiThread(() -> SketchwareUtil.toastError("No activities in that project."));
                return;
            }

            String[] names = new String[activities.size()];
            for (int i = 0; i < activities.size(); i++) {
                names[i] = ProjectFileBean.getActivityName(activities.get(i).fileName)
                        + "  (" + activities.get(i).fileName + ".xml)";
            }

            runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                    .setTitle("Pick Activity from\n" + projectLabel)
                    .setItems(names, (d, which) ->
                            showImportNameDialog(srcScId, activities.get(which)))
                    .setNegativeButton("Back", null)
                    .show());
        });
    }

    // ── Import: step 3 — confirm name ─────────────────────────────────────────

    private void showImportNameDialog(String srcScId, ProjectFileBean srcBean) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(srcBean.fileName);
        input.selectAll();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int p = dp(20);
        input.setPadding(p, p / 2, p, p / 2);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Import as…")
                .setMessage("Activity to import: " + ProjectFileBean.getActivityName(srcBean.fileName)
                        + "\n\nEnter the name for this activity in your project (base name, no 'Activity' suffix).")
                .setView(input)
                .setPositiveButton("Import", (d, w) -> {
                    String name = sanitize(input.getText() == null ? "" : input.getText().toString());
                    if (!isValidName(name)) { SketchwareUtil.toastError("Invalid name."); return; }
                    importActivity(srcScId, srcBean, name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Clone / Rename dialogs ─────────────────────────────────────────────────

    private void showCloneDialog(String src) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(src + "_copy");
        input.selectAll();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int p = dp(20);
        input.setPadding(p, p / 2, p, p / 2);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Clone: " + ProjectFileBean.getActivityName(src))
                .setMessage("Enter the base name for the cloned activity.")
                .setView(input)
                .setPositiveButton("Clone", (d, w) -> {
                    String name = sanitize(input.getText() == null ? "" : input.getText().toString());
                    if (!isValidName(name)) { SketchwareUtil.toastError("Invalid name."); return; }
                    cloneActivity(src, name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRenameDialog(String oldName) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(oldName);
        input.selectAll();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int p = dp(20);
        input.setPadding(p, p / 2, p, p / 2);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Rename: " + ProjectFileBean.getActivityName(oldName))
                .setMessage("Enter the new base name.")
                .setView(input)
                .setPositiveButton("Rename", (d, w) -> {
                    String name = sanitize(input.getText() == null ? "" : input.getText().toString());
                    if (!isValidName(name)) { SketchwareUtil.toastError("Invalid name."); return; }
                    if (name.equals(oldName)) { SketchwareUtil.toast("Name unchanged."); return; }
                    renameActivity(oldName, name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Operations ─────────────────────────────────────────────────────────────

    /**
     * Imports an activity from another project (srcScId) into this project.
     * Copies file-metadata bean, view sections, and logic sections.
     */
    private void importActivity(String srcScId, ProjectFileBean srcBean, String dstName) {
        executor.execute(() -> {
            try {
                if (isDuplicate(dstName)) {
                    runOnUiThread(() -> SketchwareUtil.toastError(
                            "'" + ProjectFileBean.getActivityName(dstName) + "' already exists."));
                    return;
                }

                // 1. Add the activity bean to the current project's file metadata
                ProjectFileBean dstBean = new ProjectFileBean(
                        ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY, dstName,
                        srcBean.orientation, srcBean.keyboardSetting, srcBean.options);
                dstBean.presetName = srcBean.presetName;
                jC.b(scId).a(dstBean);
                jC.b(scId).j();
                jC.b(scId).l();

                // 2. Copy view sections from source project's view file
                copyViewSectionsAcrossProjects(srcScId, srcBean.fileName + ".xml", dstName + ".xml");

                // 3. Copy logic sections from source project's logic file
                copyLogicSectionsAcrossProjects(srcScId, srcBean.fileName + ".java_", dstName + ".java_");

                runOnUiThread(() -> {
                    SketchwareUtil.toast("Imported as " + ProjectFileBean.getActivityName(dstName));
                    loadAndDisplayActivities();
                });
            } catch (Exception e) {
                runOnUiThread(() -> SketchwareUtil.toastError("Import failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Clones an activity within the current project:
     * file-metadata bean, view layout sections, and logic sections.
     */
    private void cloneActivity(String src, String dst) {
        executor.execute(() -> {
            try {
                if (isDuplicate(dst)) {
                    runOnUiThread(() -> SketchwareUtil.toastError(
                            "'" + ProjectFileBean.getActivityName(dst) + "' already exists."));
                    return;
                }

                ArrayList<ProjectFileBean> beans = jC.b(scId).b();
                ProjectFileBean srcBean = null;
                if (beans != null) {
                    for (ProjectFileBean b : beans) {
                        if (src.equals(b.fileName)) { srcBean = b; break; }
                    }
                }
                if (srcBean == null) {
                    runOnUiThread(() -> SketchwareUtil.toastError("Source activity not found."));
                    return;
                }

                ProjectFileBean dstBean = new ProjectFileBean(
                        srcBean.fileType, dst, srcBean.orientation,
                        srcBean.keyboardSetting, srcBean.options);
                dstBean.presetName = srcBean.presetName;
                jC.b(scId).a(dstBean);
                jC.b(scId).j();
                jC.b(scId).l();

                cloneViewSections(src + ".xml", dst + ".xml");
                cloneLogicSections(src + ".java_", dst + ".java_");

                runOnUiThread(() -> {
                    SketchwareUtil.toast("Cloned as " + ProjectFileBean.getActivityName(dst));
                    loadAndDisplayActivities();
                });
            } catch (Exception e) {
                runOnUiThread(() -> SketchwareUtil.toastError("Clone failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Renames an activity: updates file-metadata bean, view/logic section headers,
     * and AndroidManifest injections (launcher activity + activity attributes).
     */
    private void renameActivity(String oldName, String newName) {
        executor.execute(() -> {
            try {
                if (isDuplicate(newName)) {
                    runOnUiThread(() -> SketchwareUtil.toastError(
                            "'" + ProjectFileBean.getActivityName(newName) + "' already exists."));
                    return;
                }

                // 1. Update file-metadata bean
                ArrayList<ProjectFileBean> beans = jC.b(scId).b();
                if (beans != null) {
                    for (ProjectFileBean b : beans) {
                        if (oldName.equals(b.fileName)) { b.fileName = newName; break; }
                    }
                }
                jC.b(scId).j();
                jC.b(scId).l();

                // 2. Update view / logic files
                renameViewSections(oldName + ".xml", newName + ".xml");
                renameLogicSections(oldName + ".java_", newName + ".java_");

                // 3. Update AndroidManifest injections
                updateManifestOnRename(oldName, newName);

                runOnUiThread(() -> {
                    SketchwareUtil.toast("Renamed to " + ProjectFileBean.getActivityName(newName));
                    loadAndDisplayActivities();
                });
            } catch (Exception e) {
                runOnUiThread(() -> SketchwareUtil.toastError("Rename failed: " + e.getMessage()));
            }
        });
    }

    // ── Manifest update on rename ──────────────────────────────────────────────

    /**
     * Updates any AndroidManifest injections that reference the old activity name.
     *
     * Files updated:
     * - activity_launcher.txt        (if launcher was the renamed activity)
     * - activities_components.json   (custom activity XML attributes)
     * - attributes.json              (attribute injections keyed by activity name)
     */
    private void updateManifestOnRename(String oldFileName, String newFileName) {
        String oldActivityName = ProjectFileBean.getActivityName(oldFileName);
        String newActivityName = ProjectFileBean.getActivityName(newFileName);

        // Launcher activity
        String launcher = AndroidManifestInjector.getLauncherActivity(scId);
        if (oldFileName.equals(launcher)) {
            AndroidManifestInjector.setLauncherActivity(scId, newFileName);
        }

        // activities_components.json: [{"name": ".OldActivity", "value": "..."}, ...]
        File activitiesComponentsFile =
                AndroidManifestInjector.getPathAndroidManifestActivitiesComponents(scId);
        if (activitiesComponentsFile.exists()) {
            try {
                String json = FileUtil.readFile(activitiesComponentsFile.getAbsolutePath());
                if (json != null && !json.isEmpty()) {
                    // Replace all occurrences of the old activity class name in the JSON
                    String updated = json
                            .replace("\"." + oldActivityName + "\"", "\"." + newActivityName + "\"")
                            .replace("\"" + oldActivityName + "\"", "\"" + newActivityName + "\"");
                    if (!updated.equals(json)) {
                        FileUtil.writeFile(activitiesComponentsFile.getAbsolutePath(), updated);
                    }
                }
            } catch (Exception ignored) {}
        }

        // attributes.json: [{"name": "...", "value": "..."}, ...]
        File attributesFile =
                AndroidManifestInjector.getPathAndroidManifestAttributeInjection(scId);
        if (attributesFile.exists()) {
            try {
                String json = FileUtil.readFile(attributesFile.getAbsolutePath());
                if (json != null && !json.isEmpty()) {
                    String updated = json
                            .replace("\"." + oldActivityName + "\"", "\"." + newActivityName + "\"")
                            .replace("\"" + oldActivityName + "\"", "\"" + newActivityName + "\"");
                    if (!updated.equals(json)) {
                        FileUtil.writeFile(attributesFile.getAbsolutePath(), updated);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    // ── Cross-project view/logic copy ─────────────────────────────────────────

    private void copyViewSectionsAcrossProjects(String srcScId, String srcSection, String dstSection) {
        String srcContent = SketchwareFileDecryptor.decryptFile(srcScId, "view");
        if (srcContent == null || srcContent.isEmpty()) return;

        StringBuilder copied = extractSection(srcContent, srcSection);
        if (copied.length() == 0) return;

        String dstContent = SketchwareFileDecryptor.decryptFile(scId, "view");
        String base = (dstContent == null || dstContent.isEmpty()) ? "" : dstContent.trim();
        SketchwareFileEncryptor.encryptAndSaveFile(scId, "view",
                base + "\n@" + dstSection + "\n" + copied);
    }

    private void copyLogicSectionsAcrossProjects(String srcScId, String srcPrefix, String dstPrefix) {
        String srcContent = SketchwareFileDecryptor.decryptFile(srcScId, "logic");
        if (srcContent == null || srcContent.isEmpty()) return;

        StringBuilder copied = extractSectionsWithPrefix(srcContent, srcPrefix, dstPrefix);
        if (copied.length() == 0) return;

        String dstContent = SketchwareFileDecryptor.decryptFile(scId, "logic");
        String base = (dstContent == null || dstContent.isEmpty()) ? "" : dstContent.trim();
        SketchwareFileEncryptor.encryptAndSaveFile(scId, "logic", base + "\n" + copied);
    }

    // ── Same-project view/logic clone ─────────────────────────────────────────

    private void cloneViewSections(String srcSection, String dstSection) {
        String content = SketchwareFileDecryptor.decryptFile(scId, "view");
        if (content == null || content.isEmpty()) return;
        StringBuilder copied = extractSection(content, srcSection);
        if (copied.length() == 0) return;
        String appended = content.trim() + "\n@" + dstSection + "\n" + copied;
        SketchwareFileEncryptor.encryptAndSaveFile(scId, "view", appended);
    }

    private void cloneLogicSections(String srcPrefix, String dstPrefix) {
        String content = SketchwareFileDecryptor.decryptFile(scId, "logic");
        if (content == null || content.isEmpty()) return;
        StringBuilder copied = extractSectionsWithPrefix(content, srcPrefix, dstPrefix);
        if (copied.length() == 0) return;
        String appended = content.trim() + "\n" + copied;
        SketchwareFileEncryptor.encryptAndSaveFile(scId, "logic", appended);
    }

    // ── Same-project view/logic rename ────────────────────────────────────────

    private void renameViewSections(String oldSection, String newSection) {
        String content = SketchwareFileDecryptor.decryptFile(scId, "view");
        if (content == null || content.isEmpty()) return;
        String updated = content.replace("@" + oldSection, "@" + newSection);
        if (!updated.equals(content)) SketchwareFileEncryptor.encryptAndSaveFile(scId, "view", updated);
    }

    private void renameLogicSections(String oldPrefix, String newPrefix) {
        String content = SketchwareFileDecryptor.decryptFile(scId, "logic");
        if (content == null || content.isEmpty()) return;
        String updated = content.replace("@" + oldPrefix, "@" + newPrefix);
        if (!updated.equals(content)) SketchwareFileEncryptor.encryptAndSaveFile(scId, "logic", updated);
    }

    // ── Section parsing helpers ────────────────────────────────────────────────

    /**
     * Extracts all lines belonging to the section headed by {@code @sectionName}
     * from a decrypted view file.
     */
    private StringBuilder extractSection(String content, String sectionName) {
        StringBuilder out = new StringBuilder();
        boolean inSection = false;
        for (String line : content.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("@")) {
                inSection = trimmed.equals("@" + sectionName);
                continue;
            }
            if (inSection && !trimmed.isEmpty()) {
                out.append(line).append("\n");
            }
        }
        return out;
    }

    /**
     * Extracts all sections from {@code content} whose {@code @header} starts with
     * {@code srcPrefix}, renaming each header to use {@code dstPrefix} instead.
     */
    private StringBuilder extractSectionsWithPrefix(String content, String srcPrefix, String dstPrefix) {
        StringBuilder out = new StringBuilder();
        boolean inSection = false;
        String pendingHeader = null;

        for (String line : content.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("@")) {
                String header = trimmed.substring(1);
                if (header.startsWith(srcPrefix)) {
                    inSection = true;
                    pendingHeader = "@" + dstPrefix + header.substring(srcPrefix.length());
                } else {
                    inSection = false;
                    pendingHeader = null;
                }
                continue;
            }
            if (inSection) {
                if (pendingHeader != null) {
                    out.append(pendingHeader).append("\n");
                    pendingHeader = null;
                }
                out.append(line).append("\n");
            }
        }
        return out;
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    private boolean isDuplicate(String name) {
        try {
            ArrayList<ProjectFileBean> beans = jC.b(scId).b();
            if (beans == null) return false;
            for (ProjectFileBean b : beans) {
                if (name.equals(b.fileName)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String sanitize(String raw) {
        String name = raw.trim();
        if (name.endsWith("Activity") && name.length() > "Activity".length()) {
            name = name.substring(0, name.length() - "Activity".length());
        }
        return name;
    }

    private boolean isValidName(String name) {
        return !name.isEmpty() && name.matches("[a-zA-Z][a-zA-Z0-9_]*");
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
