package pro.sketchware.activities.projecttools;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import com.google.android.material.tabs.TabLayout;

import io.github.rosemoe.sora.widget.CodeEditor;

import pro.sketchware.R;
import pro.sketchware.util.SketchwareFileDecryptor;
import pro.sketchware.util.SketchwareFileEncryptor;

public class ProjectFilesViewerActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";
    public static final String EXTRA_PROJECT_NAME = "project_name";

    private static final String[] FILE_NAMES = {"logic", "view"};
    private static final String[] TAB_LABELS = {"Logic", "View"};

    private String scId;
    private CodeEditor[] editors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_files_viewer);

        scId = getIntent().getStringExtra(EXTRA_SC_ID);
        String projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);
        if (scId == null) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(projectName != null ? projectName : "Project Files");
        }

        FrameLayout container = findViewById(R.id.editor_container);
        editors = new CodeEditor[FILE_NAMES.length];
        for (int i = 0; i < FILE_NAMES.length; i++) {
            CodeEditor editor = new CodeEditor(this);
            editor.setTextSize(13);
            editor.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            editor.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
            container.addView(editor);
            editors[i] = editor;
            loadFile(i);
        }

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        for (String label : TAB_LABELS) {
            tabLayout.addTab(tabLayout.newTab().setText(label));
        }
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                for (int i = 0; i < editors.length; i++) {
                    editors[i].setVisibility(i == tab.getPosition() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadFile(int index) {
        new Thread(() -> {
            String content = SketchwareFileDecryptor.decryptFile(scId, FILE_NAMES[index]);
            String display = (content == null || content.isEmpty()) ? "" : content;
            runOnUiThread(() -> {
                if (editors[index] != null) {
                    editors[index].setText(display);
                }
            });
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, "Save All")
                .setIcon(R.drawable.ic_mtrl_save)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        if (item.getItemId() == 1) {
            saveAll();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveAll() {
        boolean anyError = false;
        for (int i = 0; i < FILE_NAMES.length; i++) {
            if (editors[i] == null) continue;
            String content = editors[i].getText().toString();
            boolean ok = SketchwareFileEncryptor.encryptAndSaveFile(scId, FILE_NAMES[i], content);
            if (!ok) anyError = true;
        }
        Toast.makeText(this, anyError ? "Some files failed to save" : "All files saved",
                Toast.LENGTH_SHORT).show();
    }
}
