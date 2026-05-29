package pro.sketchware.activities.projecttools;

import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.jC;
import pro.sketchware.util.SketchwareFileDecryptor;
import pro.sketchware.util.SketchwareFileEncryptor;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Manages activities in a Sketchware project: Import (add), Clone, Rename.
 *
 * Uses the jC.b(scId) API for file-metadata operations and
 * SketchwareFileDecryptor / SketchwareFileEncryptor for view/logic files.
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

        MaterialButton addBtn = new MaterialButton(this);
        addBtn.setText("+ Import / Add Activity");
        addBtn.setOnClickListener(v -> showImportActivityDialog());
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addLp.setMargins(0, 0, 0, dp(16));
        content.addView(addBtn, addLp);

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

    // ── List activities via jC API ─────────────────────────────────────────────

    private void loadAndDisplayActivities() {
        listContainer.removeAllViews();
        try {
            ArrayList<ProjectFileBean> beans = jC.b(scId).b();
            if (beans == null || beans.isEmpty()) {
                addEmptyLabel("No activities found.");
                return;
            }
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
        infoView.setText("Layout: " + fileName + ".xml  •  ID: " + fileName);
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
        cloneBtn.setOnClickListener(v -> showCloneActivityDialog(fileName));

        MaterialButton renameBtn = new MaterialButton(
                this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        renameBtn.setText("Rename");
        renameBtn.setTextSize(13f);
        LinearLayout.LayoutParams renameLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        renameLp.setMarginStart(dp(4));
        renameBtn.setLayoutParams(renameLp);
        renameBtn.setOnClickListener(v -> showRenameActivityDialog(fileName));

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

    // ── Dialogs ─────────────────────────────────────────────────────────────────

    private void showImportActivityDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("e.g. settings, about, profile");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int p = dp(20);
        input.setPadding(p, p / 2, p, p / 2);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Add / Import Activity")
                .setMessage("Enter the base name without 'Activity' suffix.\nExample: 'settings' → SettingsActivity")
                .setView(input)
                .setPositiveButton("Add", (d, w) -> {
                    String name = sanitize(input.getText() == null ? "" : input.getText().toString());
                    if (!isValidName(name)) {
                        SketchwareUtil.toastError("Invalid name. Must start with a letter.");
                        return;
                    }
                    createActivity(name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCloneActivityDialog(String src) {
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

    private void showRenameActivityDialog(String oldName) {
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

    /** Creates a blank activity using the jC API (handles encryption transparently). */
    private void createActivity(String name) {
        executor.execute(() -> {
            try {
                if (isDuplicate(name)) {
                    runOnUiThread(() -> SketchwareUtil.toastError("'" + ProjectFileBean.getActivityName(name) + "' already exists."));
                    return;
                }
                ProjectFileBean bean = new ProjectFileBean(
                        ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY, name,
                        ProjectFileBean.ORIENTATION_PORTRAIT,
                        ProjectFileBean.KEYBOARD_STATE_UNSPECIFIED,
                        ProjectFileBean.OPTION_ACTIVITY_TOOLBAR);
                jC.b(scId).a(bean);
                jC.b(scId).j();
                jC.b(scId).l();
                runOnUiThread(() -> {
                    SketchwareUtil.toast("Created " + ProjectFileBean.getActivityName(name));
                    loadAndDisplayActivities();
                });
            } catch (Exception e) {
                runOnUiThread(() -> SketchwareUtil.toastError("Failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Clones an activity: copies its file metadata, view layout sections,
     * and logic sections using Sketchware's native encryption/decryption.
     */
    private void cloneActivity(String src, String dst) {
        executor.execute(() -> {
            try {
                if (isDuplicate(dst)) {
                    runOnUiThread(() -> SketchwareUtil.toastError("'" + ProjectFileBean.getActivityName(dst) + "' already exists."));
                    return;
                }

                // 1. Find source bean and add clone to file metadata
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

                // 2. Clone view sections (@src.xml → @dst.xml)
                cloneViewSections(src + ".xml", dst + ".xml");

                // 3. Clone logic sections (@src.java_* → @dst.java_*)
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
     * Renames an activity: updates its file metadata bean, view layout section
     * headers, and logic section headers.
     */
    private void renameActivity(String oldName, String newName) {
        executor.execute(() -> {
            try {
                if (isDuplicate(newName)) {
                    runOnUiThread(() -> SketchwareUtil.toastError("'" + ProjectFileBean.getActivityName(newName) + "' already exists."));
                    return;
                }

                // 1. Update file metadata bean directly (mutable field)
                ArrayList<ProjectFileBean> beans = jC.b(scId).b();
                if (beans != null) {
                    for (ProjectFileBean b : beans) {
                        if (oldName.equals(b.fileName)) { b.fileName = newName; break; }
                    }
                }
                jC.b(scId).j();
                jC.b(scId).l();

                // 2. Rename view sections (@old.xml → @new.xml)
                renameViewSections(oldName + ".xml", newName + ".xml");

                // 3. Rename logic sections (@old.java_* → @new.java_*)
                renameLogicSections(oldName + ".java_", newName + ".java_");

                runOnUiThread(() -> {
                    SketchwareUtil.toast("Renamed to " + ProjectFileBean.getActivityName(newName));
                    loadAndDisplayActivities();
                });
            } catch (Exception e) {
                runOnUiThread(() -> SketchwareUtil.toastError("Rename failed: " + e.getMessage()));
            }
        });
    }

    // ── Encrypted section helpers ───────────────────────────────────────────────

    /**
     * Reads the decrypted view file, copies all lines belonging to {@code srcSection}
     * under a new header {@code dstSection}, appends them, and writes back encrypted.
     *
     * View file format (decrypted):
     *   @main.xml
     *   {viewBeanJson}
     *   @settings.xml
     *   {viewBeanJson}
     */
    private void cloneViewSections(String srcSection, String dstSection) {
        String content = SketchwareFileDecryptor.decryptFile(scId, "view");
        if (content == null || content.isEmpty()) return;

        String[] lines = content.split("\n", -1);
        StringBuilder copied = new StringBuilder();
        boolean inSrc = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("@")) {
                inSrc = trimmed.equals("@" + srcSection);
                if (inSrc) continue; // we'll re-add under new header
            }
            if (inSrc && !trimmed.isEmpty()) {
                copied.append(line).append("\n");
            }
        }

        if (copied.length() > 0) {
            String appended = content.trim() + "\n@" + dstSection + "\n" + copied;
            SketchwareFileEncryptor.encryptAndSaveFile(scId, "view", appended);
        }
    }

    /**
     * Reads the decrypted view file and renames all section headers from
     * {@code oldSection} to {@code newSection}.
     */
    private void renameViewSections(String oldSection, String newSection) {
        String content = SketchwareFileDecryptor.decryptFile(scId, "view");
        if (content == null || content.isEmpty()) return;

        String updated = content.replace("@" + oldSection, "@" + newSection);
        if (!updated.equals(content)) {
            SketchwareFileEncryptor.encryptAndSaveFile(scId, "view", updated);
        }
    }

    /**
     * Reads the decrypted logic file, copies all sections whose header starts
     * with {@code srcPrefix} under the new prefix {@code dstPrefix}, and writes back.
     *
     * Logic file format (decrypted):
     *   @main.java_onCreate
     *   {blockJson}
     *   @main.java_onClick_btn1
     *   {blockJson}
     */
    private void cloneLogicSections(String srcPrefix, String dstPrefix) {
        String content = SketchwareFileDecryptor.decryptFile(scId, "logic");
        if (content == null || content.isEmpty()) return;

        String[] lines = content.split("\n", -1);
        StringBuilder copied = new StringBuilder();
        String currentHeader = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("@")) {
                if (trimmed.substring(1).startsWith(srcPrefix)) {
                    String suffix = trimmed.substring(1 + srcPrefix.length());
                    currentHeader = "@" + dstPrefix + suffix;
                } else {
                    currentHeader = null;
                }
                continue;
            }
            if (currentHeader != null && !trimmed.isEmpty()) {
                if (copied.length() == 0 || !copied.toString().endsWith("\n" + currentHeader + "\n")) {
                    copied.append(currentHeader).append("\n");
                    currentHeader = null; // header appended, clear it
                }
                copied.append(line).append("\n");
            }
        }
        // Re-do: simpler pass
        copied.setLength(0);
        boolean inSection = false;
        String pendingHeader = null;
        for (String line : lines) {
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
                    copied.append(pendingHeader).append("\n");
                    pendingHeader = null;
                }
                copied.append(line).append("\n");
            }
        }

        if (copied.length() > 0) {
            String appended = content.trim() + "\n" + copied;
            SketchwareFileEncryptor.encryptAndSaveFile(scId, "logic", appended);
        }
    }

    /**
     * Reads the decrypted logic file and renames all section headers from
     * {@code oldPrefix} to {@code newPrefix}.
     */
    private void renameLogicSections(String oldPrefix, String newPrefix) {
        String content = SketchwareFileDecryptor.decryptFile(scId, "logic");
        if (content == null || content.isEmpty()) return;

        String updated = content.replace("@" + oldPrefix, "@" + newPrefix);
        if (!updated.equals(content)) {
            SketchwareFileEncryptor.encryptAndSaveFile(scId, "logic", updated);
        }
    }

    // ── Validation helpers ──────────────────────────────────────────────────────

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
