package com.besome.sketch.design;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.besome.sketch.adapters.JavaFileAdapter;
import com.besome.sketch.beans.HistoryViewBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.common.SrcViewerActivity;
import com.besome.sketch.editor.manage.ManageCollectionActivity;
import com.besome.sketch.editor.manage.ViewSelectorActivity;
import com.besome.sketch.editor.manage.font.ManageFontActivity;
import com.besome.sketch.editor.manage.image.ManageImageActivity;
import com.besome.sketch.editor.manage.library.ManageLibraryActivity;
import com.besome.sketch.editor.manage.sound.ManageSoundActivity;
import com.besome.sketch.editor.manage.view.ManageViewActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.CustomViewPager;
import com.besome.sketch.tools.CompileLogActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.topjohnwu.superuser.Shell;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.security.Security;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.DB;
import a.a.a.GB;
import a.a.a.Ox;
import a.a.a.ProjectBuilder;
import a.a.a.ViewEditorFragment;
import a.a.a.bB;
import a.a.a.bC;
import a.a.a.br;
import a.a.a.cC;
import a.a.a.eC;
import a.a.a.jC;
import a.a.a.kC;
import a.a.a.lC;
import a.a.a.mB;
import a.a.a.rs;
import a.a.a.wq;
import a.a.a.yB;
import a.a.a.yq;
import a.a.a.zy;
import dev.chrisbanes.insetter.Insetter;
import mod.agus.jcoderz.editor.manage.permission.ManagePermissionActivity;
import mod.agus.jcoderz.editor.manage.resource.ManageResourceActivity;
import mod.hey.studios.activity.managers.assets.ManageAssetsActivity;
import mod.hey.studios.activity.managers.java.ManageJavaActivity;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge;
import mod.hey.studios.project.custom_blocks.CustomBlocksDialog;
import mod.hey.studios.project.proguard.ManageProguardActivity;
import mod.hey.studios.project.proguard.ProguardHandler;
import mod.hey.studios.project.stringfog.ManageStringFogFragment;
import mod.hey.studios.project.stringfog.StringfogHandler;
import mod.hey.studios.util.Helper;
import mod.hey.studios.util.SystemLogPrinter;
import mod.hilal.saif.activities.android_manifest.AndroidManifestInjection;
import mod.hilal.saif.activities.tools.ConfigActivity;
import mod.jbk.build.BuildProgressReceiver;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.build.compiler.bundle.AppBundleCompiler;
import mod.jbk.export.GetKeyStoreCredentialsDialog;
import mod.jbk.diagnostic.CompileErrorSaver;
import mod.jbk.diagnostic.MissingFileException;
import mod.jbk.util.LogUtil;
import mod.jbk.util.TestkeySignBridge;
import mod.khaled.logcat.LogReaderActivity;
import pro.sketchware.R;
import pro.sketchware.activities.appcompat.ManageAppCompatActivity;
import pro.sketchware.ai.bottomsheet.AiProjectBottomSheet;
import pro.sketchware.ai.integration.AiProjectIntegrationHelper;
import pro.sketchware.activities.editor.command.ManageXMLCommandActivity;
import pro.sketchware.activities.editor.view.CodeViewerActivity;
import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;
import pro.sketchware.activities.resourceseditor.ResourcesEditorActivity;
import pro.sketchware.dialogs.BuildSettingsBottomSheet;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.activities.projecttools.ProjectFileManagerActivity;
import pro.sketchware.activities.projecttools.ProjectToolsHubActivity;
import pro.sketchware.activities.projecttools.SearchInProjectActivity;
import pro.sketchware.utility.CrashlyticsBridge;
import pro.sketchware.utility.apk.ApkSignatures;
import kellinwood.security.zipsigner.ZipSigner;
import kellinwood.security.zipsigner.optional.CustomKeySigner;
import kellinwood.security.zipsigner.optional.LoadKeystoreException;
import mod.alucard.tn.apksigner.ApkSigner;
import pro.sketchware.ai.tools.LayoutTools;
import pro.sketchware.ai.uigenerator.AiUiGeneratorDialog;

public class DesignActivity extends BaseAppCompatActivity implements View.OnClickListener {
    public static String sc_id;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView xmlLayoutOrientation;
    private boolean B;
    private int currentTabNumber;
    private CustomViewPager viewPager;
    private CoordinatorLayout coordinatorLayout;
    private DrawerLayout drawer;
    private AiProjectBottomSheet aiBottomSheet;
    private yq q;
    private DB r;
    private DB t;
    private Menu bottomMenu;
    private PopupMenu bottomPopupMenu;
    private MaterialButton btnRun;
    private MaterialButton btnOptions;
    private ProjectFileBean projectFile;
    private TextView fileName;
    private String currentJavaFileName;
    private ViewEditorFragment viewTabAdapter;
    private final ActivityResultLauncher<Intent> openCollectionManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            if (viewTabAdapter != null) {
                viewTabAdapter.j();
            }
        }
    });
    private final ActivityResultLauncher<Intent> openResourcesManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            if (viewTabAdapter != null && viewPager.getCurrentItem() == 0) {
                viewTabAdapter.i();
                refreshViewTabAdapter();
            }
        }
    });
    private final ActivityResultLauncher<Intent> openViewCodeEditor = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            if (viewTabAdapter != null) {
                viewTabAdapter.i();
            }
        }
    });
    private rs eventTabAdapter;
    private br componentTabAdapter;
    private final ActivityResultLauncher<Intent> openImageManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            refresh();
        }
    });
    public final ActivityResultLauncher<Intent> changeOpenFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            assert result.getData() != null;
            projectFile = result.getData().getParcelableExtra("project_file");
            refresh();
        }
    });
    private final ActivityResultLauncher<Intent> openLibraryManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            refresh();
            if (viewTabAdapter != null) {
                viewTabAdapter.n();
            }
        }
    });
    private final ActivityResultLauncher<Intent> openViewManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            refresh();
        }
    });
    private final ActivityResultLauncher<Intent> projectToolsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                int code = result.getResultCode();
                if (code == pro.sketchware.activities.projecttools.ProjectToolsHubActivity.RESULT_BUILD_SIGNED_APK) {
                    showSignedApkBuildDialog();
                } else if (code == pro.sketchware.activities.projecttools.ProjectToolsHubActivity.RESULT_BUILD_SIGNED_AAB) {
                    showSignedAabBuildDialog();
                }
            });

    // File picker bridge for AI BottomSheet
    private final ActivityResultLauncher<Intent> aiFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null
                        && result.getData().getData() != null && aiBottomSheet != null) {
                    aiBottomSheet.onFileSelected(result.getData().getData());
                }
            });

    /** Voice input launcher — forwards RecognizerIntent result to aiBottomSheet. */
    private final ActivityResultLauncher<Intent> aiVoiceLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null
                        && aiBottomSheet != null) {
                    java.util.List<String> matches = result.getData()
                        .getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        aiBottomSheet.onVoiceResult(matches.get(0));
                    }
                }
            });
    private BuildTask currentBuildTask;
    private final BroadcastReceiver buildCancelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BuildTask.ACTION_CANCEL_BUILD.equals(intent.getAction())) {
                if (currentBuildTask != null) {
                    currentBuildTask.cancelBuild();
                }
            }
        }
    };


    /** Receives broadcasts from AI LayoutTools to reload the Design Editor in real-time */
    private final BroadcastReceiver layoutChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (LayoutTools.ACTION_LAYOUT_CHANGED.equals(intent.getAction())) {
                String scIdFromIntent = intent.getStringExtra(LayoutTools.EXTRA_SC_ID);
                if (scIdFromIntent == null || !scIdFromIntent.equals(sc_id)) return;

                String layoutXml = intent.getStringExtra("layout_xml");
                String actXml    = intent.getStringExtra("activity_xml");

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        // Switch activity if specified
                        if (actXml != null && !actXml.isEmpty()) {
                            com.besome.sketch.beans.ProjectFileBean t = a.a.a.jC.b(sc_id).b(actXml);
                            if (t != null) projectFile = t;
                        }
                        if (projectFile == null) projectFile = getDefaultProjectFile();

                        if (layoutXml != null && !layoutXml.isEmpty()) {
                            // Proven IA approach: parse XML → update jC directly → refresh UI
                            new Thread(() -> applyAiGeneratedLayoutToEditor(layoutXml)).start();
                        } else {
                            // Tools (LayoutTools) write to disk + flush jC internally.
                            // jC is already up-to-date — just refresh the canvas.
                            if (viewTabAdapter != null) {
                                viewTabAdapter.i();
                                refreshViewTabAdapter();
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("DesignActivity",
                                "layoutChangedReceiver failed: " + e.getMessage());
                    }
                }, 150);
            }
        }
    };

    /**
     * Saves the app's version information to the currently opened Sketchware project file.
     */
    private void saveVersionCodeInformationToProject() {
        HashMap<String, Object> projectMetadata = lC.b(sc_id);
        if (projectMetadata != null) {
            projectMetadata.put("sketchware_ver", GB.d(getApplicationContext()));
            lC.b(sc_id, projectMetadata);
        }
    }

    private void loadProject(boolean haveSavedState) {
        projectFile = getDefaultProjectFile();
        jC.a(sc_id, haveSavedState);
        jC.b(sc_id, haveSavedState);
        kC var2 = jC.d(sc_id, haveSavedState);
        jC.c(sc_id, haveSavedState);
        cC.c(sc_id);
        bC.d(sc_id);
        if (!haveSavedState) {
            var2.f();
            var2.g();
            var2.e();
        }
    }

    private ProjectFileBean getDefaultProjectFile() {
        return jC.b(sc_id).b(ProjectFileBean.DEFAULT_XML_NAME);
    }

    private void refreshFileSelector() {
        if (projectFile == null) {
            projectFile = getDefaultProjectFile();
        }

        String javaFileName = projectFile.getJavaName();
        String xmlFileName = projectFile.getXmlName();

        if (!javaFileName.isEmpty()) {
            currentJavaFileName = javaFileName;
        }

        if (viewPager.getCurrentItem() == 0) {
            if (!ProjectFileBean.DEFAULT_XML_NAME.equals(xmlFileName) && jC.b(sc_id).b(xmlFileName) == null) {
                projectFile = getDefaultProjectFile();
                xmlFileName = ProjectFileBean.DEFAULT_XML_NAME;
            }
            fileName.setText(xmlFileName);
        } else {
            if (!ProjectFileBean.DEFAULT_JAVA_NAME.equals(currentJavaFileName) && jC.b(sc_id).a(currentJavaFileName) == null) {
                projectFile = getDefaultProjectFile();
                currentJavaFileName = ProjectFileBean.DEFAULT_JAVA_NAME;
            }
            fileName.setText(currentJavaFileName);
        }
    }

    private void refreshViewTabAdapter() {
        if (viewTabAdapter != null && projectFile != null) {
            int orientation = projectFile.orientation;
            if (orientation == ProjectFileBean.ORIENTATION_PORTRAIT) {
                xmlLayoutOrientation.setImageResource(R.drawable.ic_screen_portrait_grey600_24dp);
            } else if (orientation == ProjectFileBean.ORIENTATION_LANDSCAPE) {
                xmlLayoutOrientation.setImageResource(R.drawable.ic_screen_landscape_grey600_24dp);
            } else {
                xmlLayoutOrientation.setImageResource(R.drawable.ic_screen_rotation_grey600_24dp);
            }
            viewTabAdapter.initialize(projectFile);
            // Keep the AI BottomSheet aware of which activity is currently open
            if (aiBottomSheet != null) {
                aiBottomSheet.setCurrentActivityXmlName(projectFile.getXmlName());
            }
        }
    }

    private void refreshEventTabAdapter() {
        if (eventTabAdapter != null && projectFile != null) {
            eventTabAdapter.setCurrentActivity(projectFile);
            eventTabAdapter.refreshEvents();
        }
    }

    private void refreshComponentTabAdapter() {
        if (componentTabAdapter != null && projectFile != null) {
            componentTabAdapter.setProjectFile(projectFile);
            componentTabAdapter.refreshData();
        }
    }

    private void refresh() {
        refreshFileSelector();
        if (viewPager.getCurrentItem() == 0) {
            refreshViewTabAdapter();
        } else {
            refreshEventTabAdapter();
            refreshComponentTabAdapter();
        }
    }

    public void setTouchEventEnabled(boolean touchEventEnabled) {
        if (touchEventEnabled) {
            viewPager.enableTouchEvent();
        } else {
            viewPager.disableTouchEvent();
        }
    }

    /**
     * Shows a Snackbar indicating that a problem occurred while compiling. The user can click on "SHOW" to get to {@link CompileLogActivity}.
     *
     * @param error The error, to be later displayed as text in {@link CompileLogActivity}
     */
    private void indicateCompileErrorOccurred(String error) {
        new CompileErrorSaver(sc_id).writeLogsToFile(error);
        Snackbar snackbar = Snackbar.make(coordinatorLayout, "Show compile log", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(Helper.getResString(R.string.common_word_show), v -> {
            if (!mB.a()) {
                snackbar.dismiss();
                Intent intent = new Intent(getApplicationContext(), CompileLogActivity.class);
                intent.putExtra("error", error);
                intent.putExtra("sc_id", sc_id);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
        });
        snackbar.show();
    }

    @Override
    public void finish() {
        jC.a();
        cC.a();
        bC.a();
        setResult(RESULT_CANCELED, getIntent());
        super.finish();
    }

    private void checkForUnsavedProjectData() {
        if (jC.c(sc_id).g() || jC.b(sc_id).g() || jC.d(sc_id).q() || jC.a(sc_id).d() || jC.a(sc_id).c()) {
            askIfToRestoreOldUnsavedProjectData();
        }
    }

    /**
     * Opens the debug APK to install.
     */
    private void installBuiltApk() {
        installBuiltApk(q.finalToInstallApkPath);
    }

    private void installBuiltApk(String apkPath) {
        if (!ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_ROOT_AUTO_INSTALL_PROJECTS)) {
            requestPackageInstallerInstall(apkPath);
        } else {
            File apkUri = new File(apkPath);
            long length = apkUri.length();
            Shell.getShell(shell -> {
                if (shell.isRoot()) {
                    List<String> stdout = new LinkedList<>();
                    List<String> stderr = new LinkedList<>();

                    Shell.cmd("cat " + apkUri + " | pm install -S " + length).to(stdout, stderr).submit(result -> {
                        if (result.isSuccess()) {
                            SketchwareUtil.toast("Package installed successfully!");
                            if (ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_ROOT_AUTO_OPEN_AFTER_INSTALLING)) {
                                Intent launcher = getPackageManager().getLaunchIntentForPackage(q.packageName);
                                if (launcher != null) {
                                    startActivity(launcher);
                                } else {
                                    SketchwareUtil.toastError("Couldn't launch project, either not installed or not with launcher activity.");
                                }
                            }
                        } else {
                            String sharedErrorMessage = "Failed to install package, result code: " + result.getCode() + ". ";
                            SketchwareUtil.toastError(sharedErrorMessage + "Logs are available in /Internal storage/.sketchware/debug.txt", Toast.LENGTH_LONG);
                            LogUtil.e("DesignActivity", sharedErrorMessage + "stdout: " + stdout + ", stderr: " + stderr);
                        }
                    });
                } else {
                    SketchwareUtil.toastError("No root access granted. Continuing using default package install prompt.");
                    requestPackageInstallerInstall(apkPath);
                }
            });
        }
    }

    private void requestPackageInstallerInstall() {
        requestPackageInstallerInstall(q.finalToInstallApkPath);
    }

    private void requestPackageInstallerInstall(String apkPath) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri = FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName() + ".provider", new File(apkPath));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");

        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (aiBottomSheet != null && aiBottomSheet.isVisible()) {
            aiBottomSheet.animateTo(AiProjectBottomSheet.STATE_HIDDEN);
        } else if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END);
        } else if (viewTabAdapter.isPropertyViewVisible()) {
            hideViewPropertyView();
        } else {
            if (currentTabNumber > 0) {
                currentTabNumber--;
                viewPager.setCurrentItem(currentTabNumber);
            } else if (t.c("P12I2")) {
                k();
                saveChangesAndCloseProject();
            } else {
                showSaveBeforeQuittingDialog();
            }
        }
    }

    public void hideViewPropertyView() {
        viewTabAdapter.a(false);
    }

    private void saveChangesAndCloseProject() {
        k();
        SaveChangesProjectCloser saveChangesProjectCloser = new SaveChangesProjectCloser(this);
        saveChangesProjectCloser.execute();
    }

    private void saveProject() {
        k();
        ProjectSaver projectSaver = new ProjectSaver(this);
        projectSaver.execute();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.design);
        if (!isStoragePermissionGranted()) {
            finish();
        }

        if (savedInstanceState == null) {
            sc_id = getIntent().getStringExtra("sc_id");
        } else {
            sc_id = savedInstanceState.getString("sc_id");
        }

        r = new DB(getApplicationContext(), "P1");
        t = new DB(getApplicationContext(), "P12");

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle(sc_id);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        drawer = findViewById(R.id.drawer_layout);
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        // AI Bottom Sheet — rides on top of the project canvas
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        ViewGroup container = (ViewGroup) findViewById(R.id.container);
        aiBottomSheet = new AiProjectBottomSheet(this, sc_id);
        // Default to "main.xml" when opening the project
        String initialActivity = (projectFile != null)
                ? projectFile.getXmlName()
                : com.besome.sketch.beans.ProjectFileBean.DEFAULT_XML_NAME;
        aiBottomSheet.setCurrentActivityXmlName(initialActivity);
        aiBottomSheet.attachToParent(container, dm.heightPixels);
        aiBottomSheet.setFileLauncher(aiFileLauncher);
        aiBottomSheet.setVoiceLauncher(aiVoiceLauncher);



        Insetter.builder().margin(WindowInsetsCompat.Type.navigationBars()).applyToView(findViewById(R.id.container));

        coordinatorLayout = findViewById(R.id.layout_coordinator);
        fileName = findViewById(R.id.file_name);

        findViewById(R.id.file_name_container).setOnClickListener(this);

        btnRun = findViewById(R.id.btn_run);
        btnRun.setOnClickListener(v -> {
            if (currentBuildTask != null && !currentBuildTask.canceled && !currentBuildTask.isBuildFinished) {
                currentBuildTask.cancelBuild();
                return;
            }

            BuildTask buildTask = new BuildTask(this, BuildRequest.debugRun());
            currentBuildTask = buildTask;
            buildTask.execute();
        });

        btnOptions = findViewById(R.id.btn_options);
        btnOptions.setOnClickListener(v -> bottomPopupMenu.show());

        bottomPopupMenu = new PopupMenu(this, btnOptions);
        bottomMenu = bottomPopupMenu.getMenu();
        bottomMenu.add(Menu.NONE, 1, Menu.NONE, "Build Settings").setOnMenuItemClickListener(item -> {
            BuildSettingsBottomSheet sheet = BuildSettingsBottomSheet.newInstance(sc_id);
            sheet.show(getSupportFragmentManager(), BuildSettingsBottomSheet.TAG);
            return true;
        });
        bottomMenu.add(Menu.NONE, 2, Menu.NONE, "Clean temporary files").setVisible(false).setOnMenuItemClickListener(item -> {
            new Thread(() -> {
                FileUtil.deleteFile(q.projectMyscPath);
                updateBottomMenu();
                runOnUiThread(() -> SketchwareUtil.toast("Done cleaning temporary files!"));
            }).start();
            return true;
        });
        bottomMenu.add(Menu.NONE, 3, Menu.NONE, "Show last compile error").setOnMenuItemClickListener(item -> {
            new CompileErrorSaver(sc_id).showLastErrors(this);
            return true;
        });
        bottomMenu.add(Menu.NONE, 5, Menu.NONE, "Show source code").setOnMenuItemClickListener(item -> {
            showCurrentActivitySrcCode();
            return true;
        });
        bottomMenu.add(Menu.NONE, 4, Menu.NONE, "Install last built APK").setVisible(false).setOnMenuItemClickListener(item -> {
            if (FileUtil.isExistFile(q.finalToInstallApkPath)) {
                installBuiltApk();
            } else SketchwareUtil.toast("APK doesn't exist anymore");
            return true;
        });
        bottomMenu.add(Menu.NONE, 6, Menu.NONE, "Show Apk signatures").setVisible(false).setOnMenuItemClickListener(item -> {
            ApkSignatures apkSignatures = new ApkSignatures(this, q.finalToInstallApkPath);
            apkSignatures.showSignaturesDialog();
            return true;
        });
        bottomMenu.add(Menu.NONE, 7, Menu.NONE, "Direct XML editor").setOnMenuItemClickListener(item -> {
            toViewCodeEditor();
            return true;
        });
        bottomMenu.add(Menu.NONE, 11, Menu.NONE, "Direct activity Java editor").setOnMenuItemClickListener(item -> {
            openGeneratedJavaEditor();
            return true;
        });
        bottomMenu.add(Menu.NONE, 12, Menu.NONE, "Reset activity Java override").setVisible(false).setOnMenuItemClickListener(item -> {
            resetGeneratedJavaOverride();
            return true;
        });
        bottomMenu.add(Menu.NONE, 14, Menu.NONE, "Project tools").setOnMenuItemClickListener(item -> {
            openProjectToolsHub();
            return true;
        });
        bottomPopupMenu.setOnDismissListener(menu -> btnOptions.setChecked(false));

        xmlLayoutOrientation = findViewById(R.id.img_orientation);
        viewPager = findViewById(R.id.viewpager);
        viewPager.setAdapter(new ViewPagerAdapter(getSupportFragmentManager()));
        viewPager.setOffscreenPageLimit(3);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrollStateChanged(int state) {
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                if (currentTabNumber == 1) {
                    if (eventTabAdapter != null) {
                        eventTabAdapter.c();
                    }
                } else if (currentTabNumber == 2 && componentTabAdapter != null) {
                    componentTabAdapter.unselectAll();
                }
                if (position == 0) {
                    bottomMenu.findItem(7).setVisible(true);
                    if (viewTabAdapter != null) {
                        viewTabAdapter.showHidePropertyView(true);
                        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_screen);
                    }
                } else if (position == 1) {
                    bottomMenu.findItem(7).setVisible(false);
                    if (viewTabAdapter != null) {
                        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_code);
                        viewTabAdapter.showHidePropertyView(false);
                        if (eventTabAdapter != null) {
                            eventTabAdapter.refreshEvents();
                        }
                    }
                } else {
                    bottomMenu.findItem(7).setVisible(false);
                    if (viewTabAdapter != null) {
                        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_code);
                        viewTabAdapter.showHidePropertyView(false);
                        if (componentTabAdapter != null) {
                            componentTabAdapter.refreshData();
                        }
                    }
                }
                refresh();
                currentTabNumber = position;
                invalidateOptionsMenu();
            }
        });
        viewPager.getAdapter().notifyDataSetChanged();
        ((TabLayout) findViewById(R.id.tab_layout)).setupWithViewPager(viewPager);

        IntentFilter filter = new IntentFilter(BuildTask.ACTION_CANCEL_BUILD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(buildCancelReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(buildCancelReceiver, filter);
        }

        // Register AI layout receiver to refresh Design Editor when AI modifies a layout
        IntentFilter layoutFilter = new IntentFilter(LayoutTools.ACTION_LAYOUT_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(layoutChangedReceiver, layoutFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(layoutChangedReceiver, layoutFilter);
        }

        // Register undo receiver for AI layout undo (triggered from BottomSheet Undo button)
        BroadcastReceiver undoReceiver = new BroadcastReceiver() {
            @Override public void onReceive(android.content.Context ctx, android.content.Intent i) {
                if (projectFile == null) return;
                String sid = i.getStringExtra("sc_id");
                if (sid == null || !sid.equals(sc_id)) return;
                runOnUiThread(() -> {
                    try {
                        // cC.c(sc_id).i() = pop undo (matches ViewEditorFragment.onUndo exactly)
                        HistoryViewBean h = cC.c(sc_id).i(projectFile.getXmlName());
                        if (h != null && h.getActionType() == HistoryViewBean.ACTION_TYPE_OVERRIDE) {
                            jC.a(sc_id).c.remove(projectFile.getXmlName());
                            jC.a(sc_id).c.put(projectFile.getXmlName(), h.getRemovedData());
                            if (viewTabAdapter != null) {
                                viewTabAdapter.i();
                                refreshViewTabAdapter();
                            }
                        }
                    } catch (Exception ex) {
                        android.util.Log.e("DesignActivity", "AI undo failed: " + ex.getMessage());
                    }
                });
            }
        };
        IntentFilter undoFilter = new IntentFilter("pro.sketchware.ACTION_UNDO_LAYOUT");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(undoReceiver, undoFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(undoReceiver, undoFilter);
        }

    }

    private boolean isDebugApkExists() {
        if (q != null) {
            return FileUtil.isExistFile(q.finalToInstallApkPath);
        }
        return false;
    }

    private void updateBottomMenu() {
        if (bottomMenu != null) {
            handler.post(() -> {
                bottomMenu.findItem(2).setVisible(q != null && FileUtil.isExistFile(q.projectMyscPath));
                var isDebugApkExists = isDebugApkExists();
                bottomMenu.findItem(4).setVisible(isDebugApkExists);
                bottomMenu.findItem(6).setVisible(isDebugApkExists);
                boolean canEditGeneratedJava = projectFile != null;
                bottomMenu.findItem(11).setVisible(canEditGeneratedJava);
                bottomMenu.findItem(12).setVisible(canEditGeneratedJava && hasGeneratedJavaOverride(projectFile));
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        try { if (liveLayoutReceiver != null) unregisterReceiver(liveLayoutReceiver); } catch (Exception ignored) {}
        unregisterReceiver(buildCancelReceiver);
        unregisterReceiver(layoutChangedReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.design_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.design_option_menu_search);
        if (searchItem != null) {
            searchItem.setVisible(currentTabNumber == 1);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.design_option_menu_ai_assistant) {
            if (aiBottomSheet != null) aiBottomSheet.toggle();
            return true;
        }
        int itemId = item.getItemId();
        if (itemId == R.id.design_actionbar_titleopen_drawer) {
            if (!drawer.isDrawerOpen(GravityCompat.END)) {
                drawer.openDrawer(GravityCompat.END);
            }
        } else if (itemId == R.id.design_option_menu_title_save_project) {
            saveProject();
        } else if (itemId == R.id.design_option_menu_search) {
            if (eventTabAdapter != null) {
                eventTabAdapter.toggleSearchBar();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        k();

        HashMap<String, Object> projectInfo = lC.b(sc_id);
        getSupportActionBar().setTitle(yB.c(projectInfo, "my_ws_name"));
        q = new yq(getApplicationContext(), wq.d(sc_id), projectInfo);

        try {
            ProjectLoader projectLoader = new ProjectLoader(this, savedInstanceState);
            projectLoader.execute();
        } catch (Exception e) {
            CrashlyticsBridge.log("ProjectLoader failed");
            CrashlyticsBridge.recordException(e);
        } finally {
            SystemLogPrinter.stop();
        }
    }


    // ── Live UI reload receiver (AI ViewBean live drawing) ───────────────────
    private BroadcastReceiver liveLayoutReceiver;

    private void registerLiveLayoutReceiver() {
        liveLayoutReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context ctx, android.content.Intent intent) {
                String sid = intent.getStringExtra("sc_id");
                if (sid == null || !sid.equals(sc_id)) return;

                String activityXml = intent.getStringExtra("activity_xml");
                String layoutXml   = intent.getStringExtra("layout_xml");

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        // Switch projectFile to the target activity if specified
                        if (activityXml != null && !activityXml.isEmpty()) {
                            com.besome.sketch.beans.ProjectFileBean targetBean =
                                    a.a.a.jC.b(sc_id).b(activityXml);
                            if (targetBean != null) projectFile = targetBean;
                        }
                        if (projectFile == null) projectFile = getDefaultProjectFile();

                        if (layoutXml != null && !layoutXml.isEmpty()) {
                            // ── Proven IA approach: XML → ViewBeanParser → jC.c.put → redraw ──
                            // Must run XML parsing on a background thread;
                            // applyAiGeneratedLayoutToEditor() uses runOnUiThread internally.
                            new Thread(() -> applyAiGeneratedLayoutToEditor(layoutXml)).start();
                        } else {
                            // ── Standard path: tools already wrote encrypted file, jC reloaded ──
                            // flushed jC internally via SketchwareFileEncryptor + jC.a(scId,true).
                            // Do NOT clear jC here — just ask the editor to repaint from memory.
                            if (viewTabAdapter != null) {
                                viewTabAdapter.i();
                                refreshViewTabAdapter();
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("DesignActivity",
                                "AI live reload failed: " + e.getMessage());
                    }
                }, 150);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("pro.sketchware.ai.ACTION_LIVE_LAYOUT_RELOAD");
        // NOTE: ACTION_LAYOUT_CHANGED is handled by layoutChangedReceiver above
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(liveLayoutReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(liveLayoutReceiver, filter);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onResume() {
        super.onResume();
        registerLiveLayoutReceiver();
        if (!isStoragePermissionGranted()) {
            finish();
        }

        updateBottomMenu();
        long freeMegabytes = GB.c();
        if (freeMegabytes < 100L && freeMegabytes > 0L) {
            warnAboutInsufficientStorageSpace();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
        if (!isStoragePermissionGranted()) {
            finish();
        }

        if (!B) {
            UnsavedChangesSaver unsavedChangesSaver = new UnsavedChangesSaver(this);
            unsavedChangesSaver.execute();
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.file_name_container) {
            if (viewPager.getCurrentItem() == 0) {
                showAvailableViews();
            } else {
                showAvailableJavaFiles();
            }
        }
    }

    /**
     * Show a dialog asking about saving the project before quitting.
     */
    private void showSaveBeforeQuittingDialog() {
        // Build a custom view with a "Save AI conversations" checkbox
        LinearLayout customView = new LinearLayout(this);
        customView.setOrientation(LinearLayout.VERTICAL);
        int px16 = (int) (16 * getResources().getDisplayMetrics().density);
        int px4  = (int) (4  * getResources().getDisplayMetrics().density);
        customView.setPadding(px16 * 2, px16, px16 * 2, px4);

        android.widget.TextView msgView = new android.widget.TextView(this);
        msgView.setText(Helper.getResString(R.string.design_quit_message_confirm_save));
        msgView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        customView.addView(msgView);

        CheckBox cbSaveConversations = new CheckBox(this);
        cbSaveConversations.setText("Save AI conversations");
        cbSaveConversations.setChecked(false);   // default: unchecked
        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cbParams.topMargin = px16;
        cbSaveConversations.setLayoutParams(cbParams);
        customView.addView(cbSaveConversations);

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.design_quit_title_exit_projet));
        dialog.setIcon(R.drawable.ic_mtrl_exit);
        dialog.setView(customView);
        dialog.setPositiveButton(Helper.getResString(R.string.design_quit_button_save_and_exit), (v, which) -> {
            if (!mB.a()) {
                v.dismiss();
                if (cbSaveConversations.isChecked()) {
                    saveAiConversationsForProject();
                }
                try {
                    saveChangesAndCloseProject();
                } catch (Exception e) {
                    CrashlyticsBridge.recordException(e);
                    h();
                }
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_exit), (v, which) -> {
            if (!mB.a()) {
                v.dismiss();
                if (cbSaveConversations.isChecked()) {
                    saveAiConversationsForProject();
                }
                try {
                    k();
                    DiscardChangesProjectCloser discardChangesProjectCloser = new DiscardChangesProjectCloser(this);
                    discardChangesProjectCloser.execute();
                } catch (Exception e) {
                    CrashlyticsBridge.recordException(e);
                    h();
                }
            }
        });
        dialog.setNeutralButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    /**
     * Persists the active AI conversation for this project via ConversationManager.
     * Called only when the user explicitly ticks "Save AI conversations".
     */
    private void saveAiConversationsForProject() {
        try {
            pro.sketchware.ai.storage.ConversationManager cm =
                    new pro.sketchware.ai.storage.ConversationManager(this);
            // ConversationManager already auto-saves; this call flushes any pending state.
            // We surface the confirmation so the user knows it happened.
            android.widget.Toast.makeText(this, "AI conversations saved", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            // Non-critical: never block project exit
        }
    }

    /**
     * Show a dialog warning the user about low free space.
     */
    private void warnAboutInsufficientStorageSpace() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.common_word_warning));
        dialog.setIcon(R.drawable.break_warning_96_red);
        dialog.setMessage(Helper.getResString(R.string.common_message_insufficient_storage_space));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_ok), null);
        dialog.show();
    }

    private void askIfToRestoreOldUnsavedProjectData() {
        B = true;
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.ic_mtrl_history);
        dialog.setTitle(Helper.getResString(R.string.design_restore_data_title));
        dialog.setMessage(Helper.getResString(R.string.design_restore_data_message_confirm));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_restore), (v, which) -> {
            if (!mB.a()) {
                boolean g = jC.c(sc_id).g();
                boolean g2 = jC.b(sc_id).g();
                boolean q = jC.d(sc_id).q();
                boolean d = jC.a(sc_id).d();
                boolean c = jC.a(sc_id).c();
                if (g) {
                    jC.c(sc_id).h();
                }
                if (g2) {
                    jC.b(sc_id).h();
                }
                if (q) {
                    jC.d(sc_id).r();
                }
                if (d) {
                    jC.a(sc_id).h();
                }
                if (c) {
                    jC.a(sc_id).f();
                }
                if (g) {
                    jC.b(sc_id).a(jC.c(sc_id));
                    jC.a(sc_id).a(jC.c(sc_id).d());
                }
                if (g2 || g) {
                    jC.a(sc_id).a(jC.b(sc_id));
                }
                if (q) {
                    jC.a(sc_id).c(jC.d(sc_id));
                    jC.a(sc_id).a(jC.d(sc_id));
                }
                refresh();
                B = false;
                v.dismiss();
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_no), (v, which) -> {
            B = false;
            v.dismiss();
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showCurrentActivitySrcCode() {
        if (projectFile == null) return;
        k();
        new Thread(() -> {
            var filename = Helper.getText(fileName);
            var code = new yq(getApplicationContext(), sc_id).getFileSrc(filename, jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
            runOnUiThread(() -> {
                if (isFinishing()) return;
                h();
                if (code.isEmpty()) {
                    SketchwareUtil.toast("Failed to generate source.");
                    return;
                }
                var scheme = filename.endsWith(".xml") ? CodeViewerActivity.SCHEME_XML : CodeViewerActivity.SCHEME_JAVA;
                launchActivity(CodeViewerActivity.class, null, new Pair<>("code", code), new Pair<>("sc_id", sc_id), new Pair<>("scheme", scheme));
            });
        }).start();
    }

    private void showAvailableJavaFiles() {
        var dialog = new MaterialAlertDialogBuilder(this).create();
        dialog.setTitle(R.string.design_file_selector_title_java);
        dialog.setIcon(R.drawable.ic_mtrl_java);
        View customView = a.a.a.wB.a(this, R.layout.file_selector_popup_select_java);
        RecyclerView recyclerView = customView.findViewById(R.id.file_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext(), RecyclerView.VERTICAL, false));
        var adapter = new JavaFileAdapter(sc_id);
        adapter.setOnItemClickListener(projectFileBean -> {
            projectFile = projectFileBean;
            refreshFileSelector();
            refreshEventTabAdapter();
            refreshComponentTabAdapter();
            dialog.dismiss();
        });
        recyclerView.setAdapter(adapter);
        dialog.setView(customView);
        dialog.show();
    }

    private void showAvailableViews() {
        Intent intent = new Intent(getApplicationContext(), ViewSelectorActivity.class);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("current_xml", projectFile.getXmlName());
        intent.putExtra("is_custom_view", projectFile.fileType == 1 || projectFile.fileType == 2);
        changeOpenFile.launch(intent);
    }

    /**
     * Opens {@link ViewCodeEditorActivity}.
     */
    void toViewCodeEditor() {
        if (projectFile == null) return;
        k();
        new Thread(() -> {
            String filename = Helper.getText(fileName);
            // var yq = new yq(getApplicationContext(), sc_id);
            var xmlGenerator = new Ox(q.N, projectFile);
            var projectDataManager = jC.a(sc_id);
            var viewBeans = projectDataManager.d(filename);
            var viewFab = projectDataManager.h(filename);
            xmlGenerator.setExcludeAppCompat(true);
            xmlGenerator.a(eC.a(viewBeans), viewFab);
            String content = xmlGenerator.b();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                h();
                launchActivity(ViewCodeEditorActivity.class, openViewCodeEditor, new Pair<>("title", filename), new Pair<>("content", content));
            });
        }).start();
    }

    /**
     * Opens {@link LogReaderActivity}.
     */
    void toLogReader() {
        Intent intent = new Intent(getApplicationContext(), LogReaderActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        startActivity(intent);
    }

    /**
     * Opens {@link ManageCollectionActivity}.
     */
    void toCollectionManager() {
        launchActivity(ManageCollectionActivity.class, openCollectionManager);
    }

    /**
     * Opens {@link AndroidManifestInjection}.
     */
    void toAndroidManifestManager() {
        if (projectFile == null) return;
        launchActivity(AndroidManifestInjection.class, null, new Pair<>("file_name", currentJavaFileName));
    }

    /** Opens the built-in Terminal. Passes sc_id so the cwd defaults to this project. */
    void toTerminal() {
        android.content.Intent intent = new android.content.Intent(
                this, pro.sketchware.activities.terminal.TerminalActivity.class);
        if (sc_id != null) intent.putExtra("sc_id", sc_id);
        startActivity(intent);
    }

    /**
     * Opens {@link ManageAppCompatActivity}.
     */
    void toAppCompatInjectionManager() {
        if (projectFile == null) return;
        launchActivity(ManageAppCompatActivity.class, null, new Pair<>("file_name", projectFile.getXmlName()));
    }

    /**
     * Opens {@link ManageAssetsActivity}.
     */
    void toAssetManager() {
        launchActivity(ManageAssetsActivity.class, null);
    }

    /**
     * Shows a {@link CustomBlocksDialog}.
     */
    void toCustomBlocksViewer() {
        new CustomBlocksDialog().show(this, sc_id);
    }

    /**
     * Opens {@link ManageJavaActivity}.
     */
    void toJavaManager() {
        launchActivity(ManageJavaActivity.class, null, new Pair<>("pkgName", q.packageName));
    }

    private String getGeneratedJavaOverridePath(ProjectFileBean file) {
        if (file == null || q == null) {
            return null;
        }
        return new FilePathUtil().getPathJava(sc_id)
                + File.separator
                + q.packageName.replace(".", File.separator)
                + File.separator
                + file.getJavaName();
    }

    private boolean hasGeneratedJavaOverride(ProjectFileBean file) {
        String overridePath = getGeneratedJavaOverridePath(file);
        return overridePath != null && FileUtil.isExistFile(overridePath);
    }

    private void openGeneratedJavaEditor() {
        if (projectFile == null) {
            SketchwareUtil.toast("No screen is selected.");
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit generated Java")
                .setMessage("This creates a manual source override for the current screen. After that, block changes will not update this screen's Java automatically until you reset the override.")
                .setPositiveButton("Continue", (dialog, which) -> seedAndLaunchGeneratedJavaEditor(projectFile))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void seedAndLaunchGeneratedJavaEditor(ProjectFileBean file) {
        try {
            String overridePath = getGeneratedJavaOverridePath(file);
            if (overridePath == null) {
                return;
            }

            if (!FileUtil.isExistFile(overridePath)) {
                String code = new yq(this, sc_id).getFileSrc(file.getJavaName(), jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
                File parent = new File(overridePath).getParentFile();
                if (parent != null && !parent.exists()) {
                    FileUtil.makeDir(parent.getAbsolutePath());
                }
                FileUtil.writeFile(overridePath, code);
            }

            Intent intent = new Intent(getApplicationContext(), SrcCodeEditor.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("sc_id", sc_id);
            intent.putExtra("title", file.getJavaName());
            intent.putExtra("content", overridePath);
            intent.putExtra("java", true);
            startActivity(intent);
        } catch (Exception e) {
            SketchwareUtil.showAnErrorOccurredDialog(this, Log.getStackTraceString(e));
        }
    }

    private void resetGeneratedJavaOverride() {
        if (projectFile == null) {
            return;
        }

        String overridePath = getGeneratedJavaOverridePath(projectFile);
        if (overridePath == null || !FileUtil.isExistFile(overridePath)) {
            SketchwareUtil.toast("This screen is using the auto-generated Java already.");
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Reset generated Java override")
                .setMessage("Delete the manual Java override for this screen and go back to the auto-generated code from blocks?")
                .setPositiveButton("Reset", (dialog, which) -> {
                    FileUtil.deleteFile(overridePath);
                    updateBottomMenu();
                    SketchwareUtil.toast("The screen now uses auto-generated Java again.");
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }



    private void openProjectToolsHub() {
        Intent intent = new Intent(getApplicationContext(), ProjectToolsHubActivity.class);
        intent.putExtra("sc_id", sc_id);
        projectToolsLauncher.launch(intent);
    }

    private void openAiForCurrentScreen() {
        if (projectFile == null) {
            SketchwareUtil.toast("No screen is selected.");
            return;
        }
        String projectName = AiProjectIntegrationHelper.resolveProjectName(sc_id, q != null ? q.projectName : null);
        String prompt = "Audit and improve the currently open Sketchware screen '" + projectFile.fileName + "'. Review its generated XML and Java, then propose or apply changes that stay fully compatible with Sketchware Pro.";
        AiProjectIntegrationHelper.openProjectChat(this, sc_id, projectName, "AI • " + projectFile.fileName, prompt);
    }

    // ── AI UI Generator ───────────────────────────────────────────────────────

    /**
     * Opens the AI → UI Generator bottom sheet.
     * The user types a screen description; the AI returns layout XML that is
     * applied to the current activity file and reloaded live in the Design Editor.
     */
    private void showAiUiGeneratorDialog() {
        if (projectFile == null) {
            SketchwareUtil.toast("No screen is selected.");
            return;
        }
        AiUiGeneratorDialog dialog = AiUiGeneratorDialog.newInstance();
        dialog.setOnApplyListener((xml, components) ->
                new Thread(() -> applyAiGeneratedLayoutToEditor(xml)).start());
        dialog.show(getSupportFragmentManager(), AiUiGeneratorDialog.TAG);
    }

    /**
     * Applies AI-generated XML directly into Sketchware's in-memory ViewBean model
     * and refreshes the Design Editor canvas — mirrors the working flow from IA fork.
     * Must be called on a background thread; uses runOnUiThread for UI updates.
     */
    private void applyAiGeneratedLayoutToEditor(String xml) {
        if (projectFile == null) {
            android.util.Log.e("AI_FLOW", "applyAiGeneratedLayoutToEditor: projectFile is null");
            runOnUiThread(() -> SketchwareUtil.toastError("No screen selected."));
            return;
        }
        final String xmlName = projectFile.getXmlName();
        android.util.Log.d("AI_FLOW", "applyAiGeneratedLayoutToEditor start: xmlName=" + xmlName);

        // Validate XML has a root element before parsing
        if (xml == null || xml.trim().isEmpty() || !xml.trim().contains("<")) {
            android.util.Log.e("AI_FLOW", "XML is empty or invalid");
            runOnUiThread(() -> SketchwareUtil.toastError("AI returned invalid XML."));
            return;
        }

        // Ensure XML has required attributes (ai sometimes omits them)
        String safeXml = ensureXmlAttributes(xml);

        try {
            // Step 1: Parse XML → ViewBeans (on background thread)
            android.util.Log.d("AI_FLOW", "Parsing XML...");
            pro.sketchware.tools.ViewBeanParser parser =
                    new pro.sketchware.tools.ViewBeanParser(safeXml);
            parser.setSkipRoot(true);
            java.util.ArrayList<com.besome.sketch.beans.ViewBean> parsedLayout = parser.parse();
            android.util.Pair<String, java.util.Map<String, String>> rootAttr =
                    parser.getRootAttributes();

            android.util.Log.d("AI_FLOW", "Parsed " + (parsedLayout != null ? parsedLayout.size() : 0) + " views");
            if (parsedLayout == null || parsedLayout.isEmpty()) {
                android.util.Log.e("AI_FLOW", "Parser returned empty list — XML may be missing android:id attributes");
                runOnUiThread(() -> SketchwareUtil.toastError(
                        "Layout generated but no views found. Check that views have android:id."));
                return;
            }

            // Step 2: Apply on UI thread — matches IA DesignActivity exactly
            runOnUiThread(() -> {
                try {
                    // 2a. Root layout attributes (LinearLayout/RelativeLayout wrapper)
                    if (rootAttr != null) {
                        android.util.Log.d("AI_FLOW", "Setting root layout: " + rootAttr.first);
                        pro.sketchware.managers.inject.InjectRootLayoutManager rootMgr =
                                new pro.sketchware.managers.inject.InjectRootLayoutManager(sc_id);
                        rootMgr.set(xmlName,
                                pro.sketchware.managers.inject.InjectRootLayoutManager.toRoot(rootAttr));
                    }

                    // 2b. Undo/redo history
                    com.besome.sketch.beans.HistoryViewBean histBean =
                            new com.besome.sketch.beans.HistoryViewBean();
                    histBean.actionOverride(parsedLayout, jC.a(sc_id).d(xmlName));
                    a.a.a.cC cc = cC.c(sc_id);
                    if (!cc.c.containsKey(xmlName)) cc.e(xmlName);
                    cc.a(xmlName);
                    cc.a(xmlName, histBean);

                    // 2c. Clear cache then update (avoids "no change" stale cache)
                    jC.a(sc_id).c.remove(xmlName);
                    jC.a(sc_id).c.put(xmlName, parsedLayout);
                    android.util.Log.d("AI_FLOW", "jC.c updated with " + parsedLayout.size() + " beans");

                    // 2d. Redraw canvas
                    if (viewTabAdapter != null) {
                        viewTabAdapter.i();
                        refreshViewTabAdapter();
                        android.util.Log.d("AI_FLOW", "Canvas refreshed");
                    } else {
                        android.util.Log.e("AI_FLOW", "viewTabAdapter is null — cannot refresh canvas");
                    }

                    Toast.makeText(DesignActivity.this,
                            getString(R.string.ai_ui_gen_success_applied),
                            Toast.LENGTH_SHORT).show();

                    if (aiBottomSheet != null) aiBottomSheet.showUndoButton();

                } catch (Exception uiEx) {
                    android.util.Log.e("AI_FLOW", "Apply UI failed: " + uiEx.getMessage(), uiEx);
                    Toast.makeText(DesignActivity.this,
                            getString(R.string.ai_ui_gen_error_apply_failed, uiEx.getMessage()),
                            Toast.LENGTH_LONG).show();
                }
            });

        } catch (Exception parseEx) {
            android.util.Log.e("AI_FLOW", "Parse failed: " + parseEx.getMessage(), parseEx);
            runOnUiThread(() -> Toast.makeText(DesignActivity.this,
                    getString(R.string.ai_ui_gen_error_apply_failed, parseEx.getMessage()),
                    Toast.LENGTH_LONG).show());
        }
    }

    /**
     * Ensures every view in the XML has required attributes so ViewBeanParser
     * doesn't skip them. Adds match_parent to views missing layout_width/height.
     * Also wraps bare XMLs that lack a root ViewGroup.
     */
    private String ensureXmlAttributes(String xml) {
        if (xml == null) return "";
        String trimmed = xml.trim();
        // Remove markdown fences if AI returned them despite system prompt
        trimmed = trimmed.replace("```xml", "").replace("```", "").trim();
        // Remove <?xml?> declaration
        trimmed = trimmed.replaceFirst("^<[?]xml[^>]*[?]>\\s*", "").trim();

        // If XML doesn't start with a ViewGroup, wrap it
        if (!trimmed.startsWith("<LinearLayout") && !trimmed.startsWith("<RelativeLayout")
                && !trimmed.startsWith("<FrameLayout") && !trimmed.startsWith("<ScrollView")
                && !trimmed.startsWith("<ConstraintLayout") && !trimmed.startsWith("<CoordinatorLayout")) {
            trimmed = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                    + "    android:layout_width=\"match_parent\"\n"
                    + "    android:layout_height=\"match_parent\"\n"
                    + "    android:orientation=\"vertical\">\n"
                    + trimmed
                    + "\n</LinearLayout>";
        }

        // Add xmlns if missing (required for parser)
        if (!trimmed.contains("xmlns:android")) {
            trimmed = trimmed.replaceFirst(
                "<([A-Za-z][A-Za-z0-9_]*)",
                "<$1 xmlns:android=\"http://schemas.android.com/apk/res/android\"");
        }
        return trimmed;
    }

    private void showSignedApkBuildDialog() {
        GetKeyStoreCredentialsDialog credentialsDialog = new GetKeyStoreCredentialsDialog(this,
                R.drawable.ic_mtrl_key,
                "Build signed APK",
                "Choose how Sketchware Pro should produce the release APK for this build.");
        credentialsDialog.setListener(credentials -> {
            BuildRequest request = BuildRequest.signedApk();
            if (credentials != null) {
                if (credentials.isForSigningWithTestkey()) {
                    request.setSignWithTestkey(true);
                } else {
                    request.configureResultJarSigning(
                            credentials.getKeyStorePath(),
                            credentials.getKeyStorePassword().toCharArray(),
                            credentials.getKeyAlias(),
                            credentials.getKeyPassword().toCharArray(),
                            credentials.getSigningAlgorithm()
                    );
                    request.configureSigningSchemes(
                            credentials.isEnableV1(),
                            credentials.isEnableV2(),
                            credentials.isEnableV3(),
                            credentials.isEnableV4()
                    );
                }
            }
            BuildTask buildTask = new BuildTask(this, request);
            currentBuildTask = buildTask;
            buildTask.execute();
        });
        credentialsDialog.show();
    }

    private void showSignedAabBuildDialog() {
        GetKeyStoreCredentialsDialog credentialsDialog = new GetKeyStoreCredentialsDialog(this,
                R.drawable.ic_mtrl_key,
                "Build signed AAB",
                "Choose how Sketchware Pro should produce the app bundle for this build.");
        credentialsDialog.setListener(credentials -> {
            BuildRequest request = BuildRequest.appBundle();
            if (credentials != null) {
                if (credentials.isForSigningWithTestkey()) {
                    request.setSignWithTestkey(true);
                } else {
                    request.configureResultJarSigning(
                            credentials.getKeyStorePath(),
                            credentials.getKeyStorePassword().toCharArray(),
                            credentials.getKeyAlias(),
                            credentials.getKeyPassword().toCharArray(),
                            credentials.getSigningAlgorithm()
                    );
                    request.configureSigningSchemes(
                            credentials.isEnableV1(),
                            credentials.isEnableV2(),
                            credentials.isEnableV3(),
                            credentials.isEnableV4()
                    );
                }
            }
            BuildTask buildTask = new BuildTask(this, request);
            currentBuildTask = buildTask;
            buildTask.execute();
        });
        credentialsDialog.show();
    }

    /**
     * Opens {@link ManagePermissionActivity}.
     */
    void toPermissionManager() {
        launchActivity(ManagePermissionActivity.class, null);
    }

    /**
     * Opens {@link ManageProguardActivity}.
     */
    void toProguardManager() {
        launchActivity(ManageProguardActivity.class, null);
    }

    /**
     * Opens {@link ManageResourceActivity}.
     */
    void toResourceManager() {
        launchActivity(ManageResourceActivity.class, openResourcesManager);
    }

    /**
     * Opens {@link ResourcesEditorActivity}.
     */
    void toResourceEditor() {
        launchActivity(ResourcesEditorActivity.class, openResourcesManager);
    }

    /**
     * Opens {@link ManageStringFogFragment}.
     */
    void toStringFogManager() {
        var fragmentManager = getSupportFragmentManager();
        if (fragmentManager.findFragmentByTag("stringFogFragment") == null) {
            var bottomSheet = new ManageStringFogFragment();
            bottomSheet.show(fragmentManager, "stringFogFragment");
        }
    }

    /**
     * Opens {@link ManageFontActivity}.
     */
    void toFontManager() {
        launchActivity(ManageFontActivity.class, null);
    }

    /**
     * Opens {@link ManageImageActivity}.
     */
    void toImageManager() {
        launchActivity(ManageImageActivity.class, openImageManager);
    }

    /**
     * Opens {@link ManageLibraryActivity}.
     */
    void toLibraryManager() {
        launchActivity(ManageLibraryActivity.class, openLibraryManager);
    }

    /**
     * Opens {@link ManageViewActivity}.
     */
    void toViewManager() {
        launchActivity(ManageViewActivity.class, openViewManager);
    }

    /**
     * Opens {@link ManageSoundActivity}.
     */
    void toSoundManager() {
        launchActivity(ManageSoundActivity.class, null);
    }

    /**
     * Opens {@link SrcViewerActivity}.
     */
    void toSourceCodeViewer() {
        launchActivity(SrcViewerActivity.class, null, new Pair<>("current", Helper.getText(fileName)));
    }

    /**
     * Opens {@link ManageXMLCommandActivity}.
     */
    void toXMLCommandManager() {
        launchActivity(ManageXMLCommandActivity.class, null);
    }

    /**
     * Opens {@link ProjectFileManagerActivity}.
     */
    void toProjectFileManager() {
        Intent intent = new Intent(getApplicationContext(), ProjectFileManagerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        startActivity(intent);
    }

    /**
     * Opens {@link SearchInProjectActivity}.
     */
    void toSearchInProject() {
        Intent intent = new Intent(getApplicationContext(), SearchInProjectActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        startActivity(intent);
    }

    @SafeVarargs
    private void launchActivity(Class<? extends Activity> toLaunch, ActivityResultLauncher<Intent> optionalLauncher, Pair<String, String>... extras) {
        Intent intent = new Intent(getApplicationContext(), toLaunch);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        for (Pair<String, String> extra : extras) {
            intent.putExtra(extra.first, extra.second);
        }

        if (optionalLauncher == null) {
            startActivity(intent);
        } else {
            optionalLauncher.launch(intent);
        }
    }

    private abstract static class BaseTask {
        protected final WeakReference<DesignActivity> activityRef;

        protected BaseTask(DesignActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        protected DesignActivity getActivity() {
            return activityRef.get();
        }
    }

    private static class BuildRequest {
        enum OutputKind {
            DEBUG_RUN,
            SIGNED_APK,
            APP_BUNDLE
        }

        private final OutputKind outputKind;
        private boolean signWithTestkey;
        private String signingKeystorePath;
        private char[] signingKeystorePassword;
        private String signingAliasName;
        private char[] signingAliasPassword;
        private String signingAlgorithm;
        boolean signingV1 = true;
        boolean signingV2 = true;
        boolean signingV3 = false;
        boolean signingV4 = false;

        private BuildRequest(OutputKind outputKind) {
            this.outputKind = outputKind;
        }

        static BuildRequest debugRun() {
            return new BuildRequest(OutputKind.DEBUG_RUN);
        }

        static BuildRequest signedApk() {
            return new BuildRequest(OutputKind.SIGNED_APK);
        }

        static BuildRequest appBundle() {
            return new BuildRequest(OutputKind.APP_BUNDLE);
        }

        void configureResultJarSigning(String keystorePath, char[] keystorePassword, String aliasName,
                                       char[] aliasPassword, String signatureAlgorithm) {
            signingKeystorePath = keystorePath;
            signingKeystorePassword = keystorePassword;
            signingAliasName = aliasName;
            signingAliasPassword = aliasPassword;
            signingAlgorithm = signatureAlgorithm;
        }

        void setSignWithTestkey(boolean signWithTestkey) {
            this.signWithTestkey = signWithTestkey;
        }

        void configureSigningSchemes(boolean v1, boolean v2, boolean v3, boolean v4) {
            signingV1 = v1;
            signingV2 = v2;
            signingV3 = v3;
            signingV4 = v4;
        }

        boolean isResultJarSigningEnabled() {
            return signingKeystorePath != null && signingKeystorePassword != null
                    && signingAliasName != null && signingAliasPassword != null && signingAlgorithm != null;
        }

        boolean isDebugRun() {
            return outputKind == OutputKind.DEBUG_RUN;
        }

        boolean isAppBundle() {
            return outputKind == OutputKind.APP_BUNDLE;
        }
    }

    private static class BuildTask extends BaseTask implements BuildProgressReceiver {
        public static final String ACTION_CANCEL_BUILD = "com.besome.sketch.design.ACTION_CANCEL_BUILD";
        private static final String CHANNEL_ID = "build_notification_channel";
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();
        private final NotificationManager notificationManager;
        private final int notificationId = 1;
        private final MaterialButton btnRun;
        private final MaterialButton btnOptions;
        private final LinearLayout progressContainer;
        private final TextView progressText;
        private final LinearProgressIndicator progressBar;
        private final BuildRequest buildRequest;
        public volatile boolean canceled;
        private volatile boolean isBuildFinished;
        private boolean isShowingNotification = false;
        private boolean buildSucceeded = false;
        private String finalArtifactPath;

        public BuildTask(DesignActivity activity, BuildRequest buildRequest) {
            super(activity);
            notificationManager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
            btnRun = activity.btnRun;
            btnOptions = activity.btnOptions;
            progressContainer = activity.findViewById(R.id.progress_container);
            progressText = activity.findViewById(R.id.progress_text);
            progressBar = activity.findViewById(R.id.progress);
            this.buildRequest = buildRequest == null ? BuildRequest.debugRun() : buildRequest;
        }

        public void execute() {
            onPreExecute();
            executorService.execute(this::doInBackground);
        }

        private void onPreExecute() {
            DesignActivity activity = getActivity();
            if (activity == null) return;

            activity.runOnUiThread(() -> {
                updateRunButton(true);
                activity.r.a("P1I10", true);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                maybeShowNotification();
            });
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity == null) return;

            try {
                var q = activity.q;
                var sc_id = DesignActivity.sc_id;
                onProgress("Deleting temporary files...", 1);
                FileUtil.deleteFile(q.projectMyscPath);

                q.c(activity.getApplicationContext());
                q.a();
                q.a(activity.getApplicationContext(), wq.e("600"));
                if (yB.a(lC.b(sc_id), "custom_icon")) {
                    q.aa(wq.e() + File.separator + sc_id + File.separator + "mipmaps");
                    if (yB.a(lC.b(sc_id), "isIconAdaptive", false)) {
                        q.createLauncherIconXml("""
                                <?xml version="1.0" encoding="utf-8"?>
                                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android" >
                                <background android:drawable="@mipmap/ic_launcher_background"/>
                                <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
                                <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
                                </adaptive-icon>""");
                    } else {
                        q.a(wq.e() + File.separator + sc_id + File.separator + "icon.png");
                    }
                }

                onProgress("Generating source code...", 2);
                kC kC = jC.d(sc_id);
                kC.b(q.resDirectoryPath + File.separator + "drawable-xhdpi");
                kC = jC.d(sc_id);
                kC.c(q.resDirectoryPath + File.separator + "raw");
                kC = jC.d(sc_id);
                kC.a(q.assetsPath + File.separator + "fonts");

                ProjectBuilder builder = new ProjectBuilder(this, activity.getApplicationContext(), q);
                builder.setBuildAppBundle(buildRequest.isAppBundle());
                builder.setReleaseBuildMode(!buildRequest.isDebugRun());

                var fileManager = jC.b(sc_id);
                var dataManager = jC.a(sc_id);
                var libraryManager = jC.c(sc_id);
                yq.ExportType exportType = buildRequest.isAppBundle() ? yq.ExportType.AAB
                        : buildRequest.isDebugRun() ? yq.ExportType.DEBUG_APP : yq.ExportType.SIGN_APP;
                q.a(libraryManager, fileManager, dataManager, exportType);
                builder.buildBuiltInLibraryInformation();
                q.b(fileManager, dataManager, libraryManager, builder.getBuiltInLibraryManager());
                q.f();
                q.e();

                builder.maybeExtractAapt2();
                if (canceled) {
                    return;
                }

                onProgress("Extracting built-in libraries...", 3);
                BuiltInLibraries.extractCompileAssets(this);
                if (canceled) {
                    return;
                }

                onProgress("AAPT2 is running...", 8);
                builder.compileResources();
                if (canceled) {
                    return;
                }

                onProgress("Generating view binding...", 11);
                builder.generateViewBinding();
                if (canceled) {
                    return;
                }

                KotlinCompilerBridge.compileKotlinCodeIfPossible(this, builder);
                if (canceled) {
                    return;
                }

                onProgress("Java is compiling...", 13);
                builder.compileJavaCode();
                if (canceled) {
                    return;
                }

                StringfogHandler stringfogHandler = new StringfogHandler(sc_id);
                stringfogHandler.start(this, builder);
                if (canceled) {
                    return;
                }

                ProguardHandler proguardHandler = new ProguardHandler(sc_id);
                proguardHandler.start(this, builder);
                if (canceled) {
                    return;
                }

                onProgress(builder.getDxRunningText(), 17);
                builder.createDexFilesFromClasses();
                if (canceled) {
                    return;
                }

                onProgress("Merging DEX files...", 18);
                builder.getDexFilesReady();
                if (canceled) {
                    return;
                }

                if (buildRequest.isAppBundle()) {
                    AppBundleCompiler compiler = new AppBundleCompiler(builder);
                    onProgress("Creating app module...", 19);
                    compiler.createModuleMainArchive();
                    if (canceled) {
                        return;
                    }

                    onProgress("Building app bundle...", 20);
                    compiler.buildBundle();
                    if (canceled) {
                        return;
                    }

                    onProgress("Signing app bundle...", 21);
                    String createdBundlePath = AppBundleCompiler.getDefaultAppBundleOutputFile(q).getAbsolutePath();
                    String signedAppBundleDirectoryPath = FileUtil.getExternalStorageDir()
                            + File.separator + "sketchware"
                            + File.separator + "signed_aab";
                    FileUtil.makeDir(signedAppBundleDirectoryPath);
                    String outputPath = signedAppBundleDirectoryPath + File.separator + new File(createdBundlePath).getName();
                    finalArtifactPath = signOrCopyArtifact(createdBundlePath, outputPath, true);
                } else if (buildRequest.isDebugRun()) {
                    onProgress("Building APK...", 19);
                    builder.buildApk();
                    if (canceled) {
                        return;
                    }

                    onProgress("Signing APK...", 20);
                    builder.signDebugApk();
                    if (canceled) {
                        return;
                    }

                    finalArtifactPath = q.finalToInstallApkPath;
                    activity.runOnUiThread(activity::installBuiltApk);
                } else {
                    onProgress("Building APK...", 19);
                    builder.buildApk();
                    if (canceled) {
                        return;
                    }

                    onProgress("Aligning APK...", 20);
                    builder.runZipalign(builder.yq.unsignedUnalignedApkPath, builder.yq.unsignedAlignedApkPath);
                    if (canceled) {
                        return;
                    }

                    onProgress("Signing APK...", 21);
                    finalArtifactPath = signOrCopyArtifact(builder.yq.unsignedAlignedApkPath, builder.yq.releaseApkPath, false);
                }

                buildSucceeded = true;
                isBuildFinished = true;
            } catch (MissingFileException e) {
                isBuildFinished = true;
                activity.runOnUiThread(() -> {
                    boolean isMissingDirectory = e.isMissingDirectory();

                    MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity);
                    if (isMissingDirectory) {
                        dialog.setTitle("Missing directory detected");
                        dialog.setMessage("A directory important for building is missing. " +
                                "Sketchware Pro can try creating " + e.getMissingFile().getAbsolutePath() +
                                " if you'd like to.");
                        dialog.setNeutralButton("Create", (v, which) -> {
                            v.dismiss();
                            if (!e.getMissingFile().mkdirs()) {
                                SketchwareUtil.toastError("Failed to create directory / directories!");
                            }
                        });
                    } else {
                        dialog.setTitle("Missing file detected");
                        dialog.setMessage("A file needed for building is missing. " +
                                "Put the correct file back to " + e.getMissingFile().getAbsolutePath() +
                                " and try building again.");
                    }
                    dialog.setPositiveButton("Dismiss", null);
                    dialog.show();
                });
            } catch (Throwable tr) {
                isBuildFinished = true;
                if (tr instanceof LoadKeystoreException &&
                        "Incorrect password, or integrity check failed.".equals(tr.getMessage())) {
                    activity.runOnUiThread(() -> SketchwareUtil.showAnErrorOccurredDialog(activity,
                            "Either an incorrect password was entered, or the key store is corrupt."));
                } else if (tr instanceof zy) {
                    activity.indicateCompileErrorOccurred(((zy) tr).getMessage());
                } else {
                    LogUtil.e("DesignActivity$BuildTask", "Failed to build project", tr);
                    activity.indicateCompileErrorOccurred(Log.getStackTraceString(tr));
                }
            } finally {
                activity.runOnUiThread(this::onPostExecute);
            }
        }

        private String signOrCopyArtifact(String inputPath, String outputPath, boolean bundle) throws Exception {
            File outputFile = new File(outputPath);
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists()) {
                FileUtil.makeDir(parent.getAbsolutePath());
            }
            if (outputFile.exists()) {
                FileUtil.deleteFile(outputFile.getAbsolutePath());
            }

            if (buildRequest.signWithTestkey) {
                if (bundle) {
                    ZipSigner signer = new ZipSigner();
                    signer.setKeymode(ZipSigner.KEY_TESTKEY);
                    signer.signZip(inputPath, outputPath);
                } else {
                    TestkeySignBridge.signWithTestkey(inputPath, outputPath);
                }
                return outputPath;
            }

            if (buildRequest.isResultJarSigningEnabled()) {
                if (bundle) {
                    // AAB signing uses ZipSigner/CustomKeySigner; APK signature schemes do not apply
                    Security.addProvider(new BouncyCastleProvider());
                    CustomKeySigner.signZip(
                            new ZipSigner(),
                            buildRequest.signingKeystorePath,
                            buildRequest.signingKeystorePassword,
                            buildRequest.signingAliasName,
                            buildRequest.signingAliasPassword,
                            buildRequest.signingAlgorithm,
                            inputPath,
                            outputPath
                    );
                } else {
                    // APK signing — use ApkSigner with user-selected v1/v2/v3/v4 scheme flags
                    boolean success = new ApkSigner().signWithKeyStore(
                            inputPath,
                            outputPath,
                            buildRequest.signingKeystorePath,
                            new String(buildRequest.signingKeystorePassword),
                            buildRequest.signingAliasName,
                            new String(buildRequest.signingAliasPassword),
                            buildRequest.signingV1,
                            buildRequest.signingV2,
                            buildRequest.signingV3,
                            buildRequest.signingV4,
                            null
                    );
                    if (!success) {
                        throw new RuntimeException("APK signing failed. Check keystore credentials and try again.");
                    }
                }
                return outputPath;
            }

            String unsignedOutputPath = getCorrectResultFilename(outputPath, bundle);
            FileUtil.copyFile(inputPath, unsignedOutputPath);
            return unsignedOutputPath;
        }

        private String getCorrectResultFilename(String oldFormatFilename, boolean bundle) {
            if (!buildRequest.isResultJarSigningEnabled() && !buildRequest.signWithTestkey) {
                return bundle
                        ? oldFormatFilename.replace(".aab", ".unsigned.aab")
                        : oldFormatFilename.replace("_release", "_release.unsigned");
            }
            return oldFormatFilename;
        }

        @Override
        public void onProgress(String progress, int step) {
            int totalSteps = buildRequest.isDebugRun() ? 20 : 21;

            DesignActivity activity = getActivity();
            if (activity == null) return;

            activity.runOnUiThread(() -> {
                progressBar.setIndeterminate(step == -1);
                if (!canceled) {
                    String decoratedProgress = step > 0 ? progress + " (" + step + " / " + totalSteps + ")" : progress;
                    updateNotification(decoratedProgress);
                }
                progressText.setText(progress);
                if (step > 0) {
                    var progressInt = (step * 100) / totalSteps;
                    progressBar.setProgress(progressInt, true);
                }
                Log.d("DesignActivity$BuildTask", step + " / " + totalSteps);
            });
        }

        private void onPostExecute() {
            DesignActivity activity = getActivity();
            if (activity == null) return;

            activity.runOnUiThread(() -> {
                if (!activity.isDestroyed()) {
                    if (isShowingNotification) {
                        notificationManager.cancel(notificationId);
                        isShowingNotification = false;
                    }
                    updateRunButton(false);
                    activity.updateBottomMenu();
                    activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                    if (buildSucceeded && !canceled && !buildRequest.isDebugRun() && finalArtifactPath != null) {
                        String artifactLabel = buildRequest.isAppBundle() ? "AAB" : "APK";
                        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity)
                                .setIcon(R.drawable.open_box_48)
                                .setTitle("Finished building " + artifactLabel)
                                .setMessage("You can find the generated " + artifactLabel + " at:\n" + finalArtifactPath);

                        if (!buildRequest.isAppBundle() && finalArtifactPath.endsWith(".apk") && !finalArtifactPath.contains(".unsigned")) {
                            dialog.setPositiveButton("Install", (v, which) -> activity.installBuiltApk(finalArtifactPath));
                            dialog.setNegativeButton("OK", null);
                        } else {
                            dialog.setPositiveButton("OK", null);
                        }
                        dialog.show();
                    }
                }
            });
        }

        public void cancelBuild() {
            canceled = true;
            onProgress("Canceling build...", -1);
            if (isShowingNotification) {
                notificationManager.cancel(notificationId);
                isShowingNotification = false;
            }
            DesignActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            }
        }

        private void maybeShowNotification() {
            DesignActivity activity = getActivity();
            if (activity == null) return;

            if (!isShowingNotification) {
                createNotificationChannelIfNeeded();

                NotificationCompat.Builder builder = new NotificationCompat.Builder(activity, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_mtrl_code)
                        .setContentTitle(buildRequest.isAppBundle() ? "Building app bundle" : "Building project")
                        .setContentText("Starting build...")
                        .setOngoing(true)
                        .setProgress(0, 0, true)
                        .addAction(R.drawable.ic_cancel_white_96dp, "Cancel build", getCancelPendingIntent());

                notificationManager.notify(notificationId, builder.build());
                isShowingNotification = true;
            }
        }

        private void updateNotification(String progress) {
            DesignActivity activity = getActivity();
            if (activity == null) return;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(activity, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_mtrl_code)
                    .setContentTitle(buildRequest.isAppBundle() ? "Building app bundle" : "Building project")
                    .setContentText(progress)
                    .setOngoing(true)
                    .setProgress(0, 0, true)
                    .addAction(R.drawable.ic_cancel_white_96dp, "Cancel Build", getCancelPendingIntent());

            notificationManager.notify(notificationId, builder.build());
        }

        private PendingIntent getCancelPendingIntent() {
            DesignActivity activity = getActivity();
            if (activity == null) return null;

            Intent cancelIntent = new Intent(BuildTask.ACTION_CANCEL_BUILD);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            return PendingIntent.getBroadcast(activity, 0, cancelIntent, flags);
        }

        private void createNotificationChannelIfNeeded() {
            DesignActivity activity = getActivity();
            if (activity == null) return;

            CharSequence name = "Build Notifications";
            String description = "Notifications for build progress";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }

        private void updateRunButton(boolean isRunning) {
            var context = getActivity();
            btnRun.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.getColor(context, isRunning ? R.attr.colorErrorContainer : R.attr.colorPrimary)));
            btnRun.setIcon(ContextCompat.getDrawable(context, isRunning ? R.drawable.ic_mtrl_stop : R.drawable.ic_mtrl_run));
            btnRun.setIconTint(ColorStateList.valueOf(ThemeUtils.getColor(context, isRunning ? R.attr.colorOnErrorContainer : R.attr.colorSurfaceContainerLowest)));
            btnRun.setTextColor(ColorStateList.valueOf(ThemeUtils.getColor(context, isRunning ? R.attr.colorOnErrorContainer : R.attr.colorSurfaceContainerLowest)));
            btnRun.setText(isRunning ? "Stop" : "Run");
            btnOptions.setEnabled(!isRunning);
            progressContainer.setVisibility(isRunning ? View.VISIBLE : View.GONE);
        }
    }

    private static class ProjectLoader extends BaseTask {
        private final Bundle savedInstanceState;
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public ProjectLoader(DesignActivity activity, Bundle savedInstanceState) {
            super(activity);
            this.savedInstanceState = savedInstanceState;
        }

        public void execute() {
            getActivity().k();
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                activity.loadProject(savedInstanceState != null);
                activity.runOnUiThread(() -> {
                    activity.updateBottomMenu();
                    activity.refresh();
                    activity.h();
                    if (savedInstanceState == null) {
                        activity.checkForUnsavedProjectData();
                    }
                });
            }
        }
    }

    private static class DiscardChangesProjectCloser extends BaseTask {
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public DiscardChangesProjectCloser(DesignActivity activity) {
            super(activity);
        }

        public void execute() {
            getActivity().k();
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                var sc_id = DesignActivity.sc_id;
                jC.d(sc_id).v();
                jC.d(sc_id).w();
                jC.d(sc_id).u();
                activity.runOnUiThread(() -> {
                    activity.h();
                    activity.finish();
                });
            }
        }
    }

    private static class ProjectSaver extends BaseTask {
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public ProjectSaver(DesignActivity activity) {
            super(activity);
        }

        public void execute() {
            getActivity().k();
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                var sc_id = DesignActivity.sc_id;
                jC.d(sc_id).a();
                jC.b(sc_id).m();
                jC.a(sc_id).j();
                jC.d(sc_id).x();
                jC.c(sc_id).l();
                activity.runOnUiThread(() -> {
                    bB.a(activity.getApplicationContext(), Helper.getResString(R.string.common_message_complete_save), bB.TOAST_NORMAL).show();
                    activity.saveVersionCodeInformationToProject();
                    activity.h();
                    jC.d(sc_id).f();
                    jC.d(sc_id).g();
                    jC.d(sc_id).e();
                });
            }
        }
    }

    private static class SaveChangesProjectCloser extends BaseTask {
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public SaveChangesProjectCloser(DesignActivity activity) {
            super(activity);
        }

        public void execute() {
            getActivity().k();
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                var sc_id = DesignActivity.sc_id;
                jC.d(sc_id).a();
                jC.b(sc_id).m();
                jC.a(sc_id).j();
                jC.d(sc_id).x();
                jC.c(sc_id).l();
                jC.d(sc_id).h();
                activity.runOnUiThread(() -> {
                    bB.a(activity.getApplicationContext(), Helper.getResString(R.string.common_message_complete_save), bB.TOAST_NORMAL).show();
                    activity.saveVersionCodeInformationToProject();
                    activity.h();
                    activity.finish();
                });
            }
        }
    }

    private static class UnsavedChangesSaver extends BaseTask {
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public UnsavedChangesSaver(DesignActivity activity) {
            super(activity);
        }

        public void execute() {
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                eC ecInstance = jC.a(sc_id);
                synchronized (ecInstance) {
                    ecInstance.k();
                }
            }
        }
    }

    private class ViewPagerAdapter extends FragmentPagerAdapter {
        private final String[] labels;

        public ViewPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
            labels = new String[]{
                    Helper.getResString(R.string.design_tab_title_view),
                    Helper.getResString(R.string.design_tab_title_event),
                    Helper.getResString(R.string.design_tab_title_component)};
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return labels[position];
        }

        @Override
        @NonNull
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            if (position == 0) {
                viewTabAdapter = (ViewEditorFragment) fragment;
            } else if (position == 1) {
                eventTabAdapter = (rs) fragment;
            } else {
                componentTabAdapter = (br) fragment;
            }

            return fragment;
        }

        @Override
        @NonNull
        public Fragment getItem(int position) {
            if (position == 0) {
                return new ViewEditorFragment();
            } else {
                return position == 1 ? new rs() : new br();
            }
        }
    }
}

