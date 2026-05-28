package pro.sketchware.activities.importproject;

import java.io.File;
import androidx.core.content.FileProvider;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import a.a.a.MA;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.importer.AndroidStudioProjectImporter;
import pro.sketchware.utility.SketchwareUtil;

public class ImportAndroidStudioProjectActivity extends BaseAppCompatActivity {
    private static final int REQUEST_PICK_ZIP = 9101;
    private static final int REQUEST_PICK_FOLDER = 9102;
    private static final String PREFS_IMPORTER = "import_android_studio_project";
    private static final String KEY_GITHUB_TOKEN = "github_token";

    /** Extra key: absolute path to a pre-exported ZIP (set by ExportToAndroidStudioTool) */
    public static final String EXTRA_PRELOADED_ZIP_PATH = "preloaded_zip_path";


    private Uri selectedZipUri;
    private TextView selectedZipText;
    private TextView statusText;
    private TextInputEditText folderPathInput;
    private TextInputEditText githubUrlInput;
    private TextInputEditText branchInput;
    private TextInputEditText tokenInput;
    private Button pickZipButton;
    private Button importZipButton;
    private Button browseFolderButton;
    private Button importFolderButton;
    private Button importGithubButton;
    private ImportProgressDialogController progressDialogController;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_android_studio_project);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("Import Android Studio / GitHub Project");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        selectedZipText = findViewById(R.id.tv_selected_zip);
        statusText = findViewById(R.id.tv_import_status);
        folderPathInput = findViewById(R.id.et_folder_path);
        githubUrlInput = findViewById(R.id.et_github_url);
        branchInput = findViewById(R.id.et_branch);
        tokenInput = findViewById(R.id.et_token);
        pickZipButton = findViewById(R.id.btn_pick_zip);
        importZipButton = findViewById(R.id.btn_import_zip);
        browseFolderButton = findViewById(R.id.btn_browse_folder);
        importFolderButton = findViewById(R.id.btn_import_folder);
        importGithubButton = findViewById(R.id.btn_import_github);
        progressDialogController = new ImportProgressDialogController();

        restoreSavedGithubToken();

        pickZipButton.setOnClickListener(v -> pickZip());
        browseFolderButton.setOnClickListener(v -> pickFolder());
        importZipButton.setOnClickListener(v -> {
            if (selectedZipUri == null) {
                SketchwareUtil.toastError("Choose an Android Studio ZIP archive first");
                return;
            }
            new ImportTask(ImportTask.MODE_ZIP).execute();
        });
        importFolderButton.setOnClickListener(v -> {
            if (TextUtils.isEmpty(Helper.getText(folderPathInput).trim())) {
                folderPathInput.setError("Enter a project folder path");
                return;
            }
            new ImportTask(ImportTask.MODE_FOLDER).execute();
        });
        importGithubButton.setOnClickListener(v -> {
            if (TextUtils.isEmpty(Helper.getText(githubUrlInput).trim())) {
                githubUrlInput.setError("Enter a GitHub repository URL");
                return;
            }
            persistGithubToken();
            new ImportTask(ImportTask.MODE_GITHUB).execute();
        });

        // Handle pre-exported ZIP from AI agent (ExportToAndroidStudioTool)
        handlePreloadedZip();
    }

    private void restoreSavedGithubToken() {
        String savedToken = getSharedPreferences(PREFS_IMPORTER, MODE_PRIVATE)
                .getString(KEY_GITHUB_TOKEN, "");
        if (!TextUtils.isEmpty(savedToken)) {
            tokenInput.setText(savedToken);
        }
    }

    private void persistGithubToken() {
        String token = Helper.getText(tokenInput).trim();
        getSharedPreferences(PREFS_IMPORTER, MODE_PRIVATE)
                .edit()
                .putString(KEY_GITHUB_TOKEN, token)
                .apply();
    }

    /**
     * If the activity was launched by ExportToAndroidStudioTool, a ZIP path is
     * passed as an extra. We pre-select it and show a confirmation dialog so
     * the user can tap "Import" with one tap.
     */
    private void handlePreloadedZip() {
        String zipPath = getIntent().getStringExtra(EXTRA_PRELOADED_ZIP_PATH);
        if (TextUtils.isEmpty(zipPath)) return;

        File zipFile = new File(zipPath);
        if (!zipFile.exists()) return;

        // Convert File → content URI via FileProvider so ImportTask can read it
        try {
            Uri zipUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    zipFile);
            selectedZipUri = zipUri;
            selectedZipText.setText(zipFile.getName()
                    + " (" + (zipFile.length() / 1024) + " KB) — ready to import");
            statusText.setText("AI-generated Android Studio project loaded. Tap 'Import ZIP' to add it to Sketchware.");
        } catch (Exception e) {
            // FileProvider may not be configured; fall back to file URI
            selectedZipUri = Uri.fromFile(zipFile);
            selectedZipText.setText(zipFile.getName()
                    + " (" + (zipFile.length() / 1024) + " KB) — ready to import");
            statusText.setText("AI-generated Android Studio project loaded. Tap 'Import ZIP' to add it to Sketchware.");
        }
    }

    private void pickZip() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        startActivityForResult(intent, REQUEST_PICK_ZIP);
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_PICK_FOLDER);
    }

    private String getRealPathFromTreeUri(Uri treeUri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            String[] parts = docId.split(":", 2);
            if (parts.length == 2) {
                String type = parts[0];
                String relativePath = parts[1];
                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + relativePath;
                }
                // SD card or other volume
                return "/storage/" + type + "/" + relativePath;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_ZIP && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedZipUri = data.getData();
            selectedZipText.setText(String.valueOf(selectedZipUri));
            try {
                int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(selectedZipUri, takeFlags);
            } catch (Exception ignored) {
            }
        } else if (requestCode == REQUEST_PICK_FOLDER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri folderUri = data.getData();
            String realPath = getRealPathFromTreeUri(folderUri);
            if (realPath != null) {
                folderPathInput.setText(realPath);
            } else {
                folderPathInput.setText(folderUri.toString());
                SketchwareUtil.toastError("Could not resolve a file-system path; paste the path manually if import fails.");
            }
        }
    }

    private void setImportUiEnabled(boolean enabled) {
        pickZipButton.setEnabled(enabled);
        importZipButton.setEnabled(enabled);
        browseFolderButton.setEnabled(enabled);
        importFolderButton.setEnabled(enabled);
        importGithubButton.setEnabled(enabled);
        folderPathInput.setEnabled(enabled);
        githubUrlInput.setEnabled(enabled);
        branchInput.setEnabled(enabled);
        tokenInput.setEnabled(enabled);
    }

    private void updateProgressUi(AndroidStudioProjectImporter.ImportProgress progress) {
        if (progress == null) {
            return;
        }
        progressDialogController.show();
        progressDialogController.update(progress);
    }

    private void showResult(AndroidStudioProjectImporter.ImportResult result) {
        String displayText = result.toDisplayText();
        if (isFinishing() || isDestroyed()) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Import completed — " + (result.projectName != null ? result.projectName : ""))
                .setMessage(displayText + "\n\nThe imported project is now available in your project list.")
                .setPositiveButton("Open project list", (d, w) -> {
                    // Let the user navigate to projects
                    setResult(RESULT_OK);
                    finish();
                })
                .setNegativeButton("Stay here", null)
                .show();
    }

    @Override
    public void onPause() {
        super.onPause();
        persistGithubToken();
    }

    @Override
    public void onDestroy() {
        if (progressDialogController != null) {
            progressDialogController.dismiss();
        }
        super.onDestroy();
    }

    private class ImportTask extends MA {
        private static final int MODE_ZIP = 1;
        private static final int MODE_FOLDER = 2;
        private static final int MODE_GITHUB = 3;

        private final int mode;
        private AndroidStudioProjectImporter.ImportResult result;
        private volatile AndroidStudioProjectImporter.ImportProgress latestProgress;

        public ImportTask(int mode) {
            super(ImportAndroidStudioProjectActivity.this);
            this.mode = mode;
            addTask(this);
            setImportUiEnabled(false);
            if (mode == MODE_ZIP) {
                updateProgressUi(new AndroidStudioProjectImporter.ImportProgress(
                        "Preparing ZIP import",
                        "Opening the selected Android Studio archive and validating that Sketchware can read it safely.",
                        0, 0, true, "Waiting for archive analysis"
                ));
            } else if (mode == MODE_FOLDER) {
                updateProgressUi(new AndroidStudioProjectImporter.ImportProgress(
                        "Preparing folder import",
                        "Scanning the selected project folder and validating the Android module structure before import.",
                        0, 0, true, "Waiting for folder analysis"
                ));
            } else {
                updateProgressUi(new AndroidStudioProjectImporter.ImportProgress(
                        "Preparing GitHub import",
                        "Connecting to GitHub, resolving the target branch, and downloading the repository archive.",
                        0, 0, true, "Connecting to GitHub"
                ));
            }
        }

        @Override
        public void a() {
            setImportUiEnabled(true);
            progressDialogController.dismiss();
            if (result != null) {
                showResult(result);
            }
        }

        @Override
        public void a(String errorMessage) {
            setImportUiEnabled(true);
            progressDialogController.dismiss();
            statusText.setText(errorMessage);
            SketchwareUtil.showAnErrorOccurredDialog(ImportAndroidStudioProjectActivity.this, errorMessage);
        }

        @Override
        protected void onCancelled() {
            setImportUiEnabled(true);
            progressDialogController.dismiss();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (latestProgress != null) {
                updateProgressUi(latestProgress);
            }
        }

        @Override
        public void b() throws a.a.a.By {
            try {
                AndroidStudioProjectImporter importer = new AndroidStudioProjectImporter(ImportAndroidStudioProjectActivity.this)
                        .setProgressListener(progress -> {
                            latestProgress = progress;
                            publishProgress(progress.getStatusLineOrDefault());
                        });
                if (mode == MODE_ZIP) {
                    result = importer.importFromZipUri(selectedZipUri);
                } else if (mode == MODE_FOLDER) {
                    result = importer.importFromFolder(
                            new File(Helper.getText(folderPathInput).trim()),
                            "folder",
                            null
                    );
                } else {
                    result = importer.importFromGitHub(
                            Helper.getText(githubUrlInput).trim(),
                            Helper.getText(branchInput).trim(),
                            Helper.getText(tokenInput).trim()
                    );
                }
            } catch (Exception e) {
                throw new a.a.a.By(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }
    }

    private final class ImportProgressDialogController {
        private AlertDialog dialog;
        private TextView titleView;
        private TextView statusView;
        private TextView stepView;
        private TextView percentView;
        private TextView detailView;
        private LinearProgressIndicator progressIndicator;

        void show() {
            if (dialog == null) {
                View view = LayoutInflater.from(ImportAndroidStudioProjectActivity.this)
                        .inflate(R.layout.dialog_import_progress, null, false);
                titleView = view.findViewById(R.id.tv_progress_title);
                statusView = view.findViewById(R.id.tv_progress_status);
                stepView = view.findViewById(R.id.tv_progress_step);
                percentView = view.findViewById(R.id.tv_progress_percent);
                detailView = view.findViewById(R.id.tv_progress_detail);
                progressIndicator = view.findViewById(R.id.progress_indicator);
                dialog = new MaterialAlertDialogBuilder(ImportAndroidStudioProjectActivity.this)
                        .setView(view)
                        .setCancelable(false)
                        .create();
            }
            if (!isFinishing() && !dialog.isShowing()) {
                dialog.show();
            }
        }

        void update(AndroidStudioProjectImporter.ImportProgress progress) {
            if (progress == null) {
                return;
            }
            show();
            titleView.setText(TextUtils.isEmpty(progress.title) ? "Importing project" : progress.title);
            statusView.setText(progress.getStatusLineOrDefault());
            detailView.setText(TextUtils.isEmpty(progress.detail)
                    ? "Analyzing the import source and preparing the Sketchware project."
                    : progress.detail);
            if (progress.indeterminate || progress.totalSteps <= 0) {
                progressIndicator.setIndeterminate(true);
                stepView.setText("Working...");
                percentView.setText("");
            } else {
                progressIndicator.setIndeterminate(false);
                progressIndicator.setProgressCompat(progress.getPercent(), true);
                stepView.setText("Step " + progress.currentStep + " of " + progress.totalSteps);
                percentView.setText(progress.getPercent() + "%");
            }
        }

        void dismiss() {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }
}
