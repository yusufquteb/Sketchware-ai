package pro.sketchware.activities.library.extras;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Objects;

import a.a.a.jC;
import com.besome.sketch.beans.ProjectLibraryBean;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.databinding.ActivityLibraryExtrasBinding;
import pro.sketchware.settings.LibraryExtrasSettings;
import pro.sketchware.settings.ProjectSettingsStore;

public class LibraryExtrasActivity extends AppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    private String scId;
    private ActivityLibraryExtrasBinding binding;
    private LibraryExtrasSettings settings;
    private boolean appCompatEnabledOverride = false;
    private boolean hasOverride = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scId = getIntent().getStringExtra(EXTRA_SC_ID);
        if (scId == null || scId.trim().isEmpty()) {
            finish();
            return;
        }
        if (getIntent().hasExtra("app_compat_enabled")) {
            appCompatEnabledOverride = getIntent().getBooleanExtra("app_compat_enabled", false);
            hasOverride = true;
        }
        EdgeToEdge.enable(this);
        binding = ActivityLibraryExtrasBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        settings = new LibraryExtrasSettings(new ProjectSettingsStore(this, scId));
        initialize();
    }

    private void initialize() {
        // ── Restore saved states ─────────────────────────────────────────────
        binding.swWorkmanager.setChecked(settings.isForceWorkManager());
        binding.swMedia3.setChecked(settings.isUseMedia3());
        binding.swBrowser.setChecked(settings.isUseBrowser());
        binding.swCredentialManager.setChecked(settings.isUseCredentialManager());
        binding.swGlideTransforms.setChecked(settings.isUseGlideTransformations());
        binding.swShizuku.setChecked(settings.isUseShizuku());

        // ── Listeners ────────────────────────────────────────────────────────
        binding.swWorkmanager.setOnCheckedChangeListener((b, checked) ->
                settings.setForceWorkManager(checked));
        binding.swMedia3.setOnCheckedChangeListener((b, checked) ->
                settings.setUseMedia3(checked));
        binding.swBrowser.setOnCheckedChangeListener((b, checked) ->
                settings.setUseBrowser(checked));
        binding.swCredentialManager.setOnCheckedChangeListener((b, checked) ->
                settings.setUseCredentialManager(checked));
        binding.swGlideTransforms.setOnCheckedChangeListener((b, checked) ->
                settings.setUseGlideTransformations(checked));
        binding.swShizuku.setOnCheckedChangeListener((b, checked) ->
                settings.setUseShizuku(checked));

        // ── Row click toggles ────────────────────────────────────────────────
        binding.lnWorkmanager.setOnClickListener(v -> binding.swWorkmanager.toggle());
        binding.lnMedia3.setOnClickListener(v -> binding.swMedia3.toggle());
        binding.lnBrowser.setOnClickListener(v -> binding.swBrowser.toggle());
        binding.lnCredentialManager.setOnClickListener(v -> binding.swCredentialManager.toggle());
        binding.lnGlideTransforms.setOnClickListener(v -> binding.swGlideTransforms.toggle());
        binding.lnShizuku.setOnClickListener(v -> binding.swShizuku.toggle());

        // ── Availability guards ──────────────────────────────────────────────
        initializeWorkManager();
        initializeMedia3();
        initializeBrowser();
        initializeCredentialManager();
        initializeShizuku();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isAppCompatEnabled() {
        if (hasOverride) return appCompatEnabledOverride;
        ProjectLibraryBean compat = jC.c(scId).c();
        return compat != null && ProjectLibraryBean.LIB_USE_Y.equals(compat.useYn);
    }

    private boolean isJava7() {
        BuildSettings bs = new BuildSettings(scId);
        return BuildSettings.SETTING_JAVA_VERSION_1_7.equals(
                bs.getValue(BuildSettings.SETTING_JAVA_VERSION,
                        BuildSettings.SETTING_JAVA_VERSION_1_8));
    }

    private int getMinSdk() {
        return new ProjectSettings(scId).getMinSdkVersion();
    }

    // ── Guards ────────────────────────────────────────────────────────────────

    private void initializeWorkManager() {
        if (!isAppCompatEnabled()) {
            binding.tvWorkmanagerNote.setText("To use, enable AppCompat. "
                    + binding.tvWorkmanagerNote.getText());
            setRowEnabled(binding.lnWorkmanager, false);
        }
    }

    private void initializeMedia3() {
        boolean ok = true;
        if (getMinSdk() < 24) {
            ok = false;
            binding.tvMedia3Note.setText("To use, min SDK required is 24 or newer (Android 7+). "
                    + binding.tvMedia3Note.getText());
        } else if (!isAppCompatEnabled()) {
            ok = false;
            binding.tvMedia3Note.setText("To use, enable AppCompat. "
                    + binding.tvMedia3Note.getText());
        } else if (isJava7()) {
            ok = false;
            binding.tvMedia3Note.setText("To use, use a newer version of Java. "
                    + binding.tvMedia3Note.getText());
        }
        setRowEnabled(binding.lnMedia3, ok);
    }

    private void initializeBrowser() {
        if (!isAppCompatEnabled()) {
            binding.tvBrowserNote.setText("To use, enable AppCompat. "
                    + binding.tvBrowserNote.getText());
            setRowEnabled(binding.lnBrowser, false);
        }
    }

    private void initializeCredentialManager() {
        boolean ok = true;
        if (getMinSdk() < 24) {
            ok = false;
            binding.tvCredentialManagerNote.setText("To use, min SDK required is 24 or newer (Android 7+). "
                    + binding.tvCredentialManagerNote.getText());
        } else if (!isAppCompatEnabled()) {
            ok = false;
            binding.tvCredentialManagerNote.setText("To use, enable AppCompat. "
                    + binding.tvCredentialManagerNote.getText());
        } else if (isJava7()) {
            ok = false;
            binding.tvCredentialManagerNote.setText("To use, use a newer version of Java. "
                    + binding.tvCredentialManagerNote.getText());
        }
        setRowEnabled(binding.lnCredentialManager, ok);
    }

    private void initializeShizuku() {
        boolean ok = true;
        if (getMinSdk() < 24) {
            ok = false;
            binding.tvShizukuNote.setText("To use, min SDK required is 24 or newer (Android 7+). "
                    + binding.tvShizukuNote.getText());
        } else if (!isAppCompatEnabled()) {
            ok = false;
            binding.tvShizukuNote.setText("To use, enable AppCompat. "
                    + binding.tvShizukuNote.getText());
        }
        setRowEnabled(binding.lnShizuku, ok);
    }

    private void setRowEnabled(android.view.View row, boolean enabled) {
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1f : 0.5f);
    }
}
