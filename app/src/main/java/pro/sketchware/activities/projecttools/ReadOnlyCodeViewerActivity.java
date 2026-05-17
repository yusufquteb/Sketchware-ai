package pro.sketchware.activities.projecttools;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import mod.hey.studios.code.SrcCodeEditor;
import mod.jbk.code.CodeEditorColorSchemes;
import mod.jbk.code.CodeEditorLanguages;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class ReadOnlyCodeViewerActivity extends BaseAppCompatActivity {

    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_TITLE = "title";

    private CodeEditor editor;
    private String filePath;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(getIntent().getStringExtra(EXTRA_TITLE));
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        editor = new CodeEditor(this);
        SrcCodeEditor.loadCESettings(this, editor, "act", true);
        editor.setEditable(false);
        root.addView(editor, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f));

        setContentView(root);

        filePath = getIntent().getStringExtra(EXTRA_PATH);
        if (TextUtils.isEmpty(filePath)) {
            SketchwareUtil.toastError("File path missing");
            finish();
            return;
        }
        loadContent();
    }

    private void loadContent() {
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null) {
            title = filePath;
        }
        String content = FileUtil.readFileIfExist(filePath);
        if (title.endsWith(".java")) {
            editor.setEditorLanguage(new JavaLanguage());
        } else if (title.endsWith(".kt")) {
            editor.setEditorLanguage(CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_KOTLIN));
            editor.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(CodeEditorColorSchemes.THEME_DRACULA));
        } else if (title.endsWith(".xml") || title.endsWith(".gradle") || title.endsWith(".kts") || title.endsWith(".properties")) {
            editor.setEditorLanguage(CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_XML));
        }
        editor.setText(content);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, "Copy path");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            android.content.ClipboardManager clipboardManager =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("path", filePath));
            SketchwareUtil.toast("Path copied");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
