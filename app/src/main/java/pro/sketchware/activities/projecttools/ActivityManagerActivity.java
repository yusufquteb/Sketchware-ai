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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.jC;
import pro.sketchware.utility.SketchwareUtil;

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

    private void loadAndDisplayActivities() {
        listContainer.removeAllViews();
        File fileFile = new File(ProjectToolPaths.getProjectDataDir(scId), "file");
        try {
            JsonArray arr = readJsonArray(fileFile);
            if (arr.size() == 0) {
                addEmptyLabel("No activities found.");
                return;
            }
            boolean found = false;
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                if (!obj.has("fileName")) continue;
                int fileType = obj.has("fileType") ? obj.get("fileType").getAsInt() : 0;
                if (fileType != ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) continue;
                found = true;
                listContainer.addView(buildActivityCard(obj.get("fileName").getAsString()));
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
        String javaName = Character.toUpperCase(fileName.charAt(0)) + fileName.substring(1) + "Activity";
        nameView.setText(javaName);
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
                .setTitle("Clone: " + src + "Activity")
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
                .setTitle("Rename: " + oldName + "Activity")
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

    private boolean isDuplicate(JsonArray arr, String name) {
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("fileName") && name.equals(obj.get("fileName").getAsString())) return true;
            }
        }
        return false;
    }

    private void createActivity(String name) {
        executor.execute(() -> {
            try {
                File fileFile = new File(ProjectToolPaths.getProjectDataDir(scId), "file");
                if (isDuplicate(readJsonArray(fileFile), name)) {
                    runOnUiThread(() -> SketchwareUtil.toastError("'" + name + "Activity' already exists."));
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
                    SketchwareUtil.toast("Created " + name + "Activity");
                    loadAndDisplayActivities();
                });
            } catch (Exception e) {
                runOnUiThread(() -> SketchwareUtil.toastError("Failed: " + e.getMessage()));
            }
        });
    }

    private void cloneActivity(String src, String dst) {
        executor.execute(() -> {
            try {
                File dataDir = ProjectToolPaths.getProjectDataDir(scId);
                File fileFile = new File(dataDir, "file");
                JsonArray arr = readJsonArray(fileFile);
                if (isDuplicate(arr, dst)) {
                    runOnUiThread(() -> SketchwareUtil.toastError("'" + dst + "Activity' already exists."));
                    return;
                }
                JsonObject srcEntry = null;
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        JsonObject o = el.getAsJsonObject();
                        if (o.has("fileName") && src.equals(o.get("fileName").getAsString())) {
                            srcEntry = o.deepCopy();
                            break;
                        }
                    }
                }
                if (srcEntry == null) {
                    runOnUiThread(() -> SketchwareUtil.toastError("Source activity not found."));
                    return;
                }
                srcEntry.addProperty("fileName", dst);
                arr.add(srcEntry);
                writeJsonArray(fileFile, arr);
                cloneArrayEntries(new File(dataDir, "view"), "id", src + ".xml", dst + ".xml");
                cloneLogicEntries(new File(dataDir, "logic"), src + ".java_", dst + ".java_");
                runOnUiThread(() -> {
                    SketchwareUtil.toast("Cloned as " + dst + "Activity");
                    loadAndDisplayActivities();
                });
            } catch (Exception e) {
                runOnUiThread(() -> SketchwareUtil.toastError("Clone failed: " + e.getMessage()));
            }
        });
    }

    private void renameActivity(String oldName, String newName) {
        executor.execute(() -> {
            try {
                File dataDir = ProjectToolPaths.getProjectDataDir(scId);
                File fileFile = new File(dataDir, "file");
                JsonArray arr = readJsonArray(fileFile);
                if (isDuplicate(arr, newName)) {
                    runOnUiThread(() -> SketchwareUtil.toastError("'" + newName + "Activity' already exists."));
                    return;
                }
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        JsonObject o = el.getAsJsonObject();
                        if (o.has("fileName") && oldName.equals(o.get("fileName").getAsString())) {
                            o.addProperty("fileName", newName);
                        }
                    }
                }
                writeJsonArray(fileFile, arr);
                renameArrayEntries(new File(dataDir, "view"), "id", oldName + ".xml", newName + ".xml");
                renameLogicEntries(new File(dataDir, "logic"), oldName + ".java_", newName + ".java_");
                runOnUiThread(() -> {
                    SketchwareUtil.toast("Renamed to " + newName + "Activity");
                    loadAndDisplayActivities();
                });
            } catch (Exception e) {
                runOnUiThread(() -> SketchwareUtil.toastError("Rename failed: " + e.getMessage()));
            }
        });
    }

    private void cloneArrayEntries(File f, String key, String oldVal, String newVal) throws IOException {
        if (!f.exists()) return;
        JsonArray arr = readJsonArray(f);
        JsonArray toAdd = new JsonArray();
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                if (o.has(key) && oldVal.equals(o.get(key).getAsString())) {
                    JsonObject copy = o.deepCopy();
                    copy.addProperty(key, newVal);
                    toAdd.add(copy);
                }
            }
        }
        for (JsonElement el : toAdd) arr.add(el);
        writeJsonArray(f, arr);
    }

    private void cloneLogicEntries(File f, String oldPfx, String newPfx) throws IOException {
        if (!f.exists()) return;
        JsonArray arr = readJsonArray(f);
        JsonArray toAdd = new JsonArray();
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                if (o.has("name") && o.get("name").getAsString().startsWith(oldPfx)) {
                    JsonObject copy = o.deepCopy();
                    copy.addProperty("name", newPfx + copy.get("name").getAsString().substring(oldPfx.length()));
                    toAdd.add(copy);
                }
            }
        }
        for (JsonElement el : toAdd) arr.add(el);
        writeJsonArray(f, arr);
    }

    private void renameArrayEntries(File f, String key, String oldVal, String newVal) throws IOException {
        if (!f.exists()) return;
        JsonArray arr = readJsonArray(f);
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                if (o.has(key) && oldVal.equals(o.get(key).getAsString())) o.addProperty(key, newVal);
            }
        }
        writeJsonArray(f, arr);
    }

    private void renameLogicEntries(File f, String oldPfx, String newPfx) throws IOException {
        if (!f.exists()) return;
        JsonArray arr = readJsonArray(f);
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                if (o.has("name") && o.get("name").getAsString().startsWith(oldPfx)) {
                    o.addProperty("name", newPfx + o.get("name").getAsString().substring(oldPfx.length()));
                }
            }
        }
        writeJsonArray(f, arr);
    }

    private JsonArray readJsonArray(File file) throws IOException {
        if (!file.exists()) return new JsonArray();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
        }
        String s = sb.toString();
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') s = s.substring(1);
        s = s.trim();
        if (s.isEmpty()) return new JsonArray();
        try {
            JsonElement el = JsonParser.parseString(s);
            return el.isJsonArray() ? el.getAsJsonArray() : new JsonArray();
        } catch (JsonSyntaxException e) {
            return new JsonArray();
        }
    }

    private void writeJsonArray(File file, JsonArray arr) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(arr.toString());
        }
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
