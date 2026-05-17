package pro.sketchware.activities.library.extras;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Objects;

import a.a.a.jC;
import com.besome.sketch.beans.ProjectLibraryBean;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.databinding.ActivityFirebaseExtrasBinding;
import pro.sketchware.settings.LibraryExtrasSettings;
import pro.sketchware.settings.OneSignalSettingsManager;
import pro.sketchware.settings.ProjectSettingsStore;

public class FirebaseExtrasActivity extends AppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    private String scId;
    private ActivityFirebaseExtrasBinding binding;
    private LibraryExtrasSettings settings;
    private OneSignalSettingsManager oneSignalSettings;
    // In-memory state passed from ManageLibraryActivity (may differ from saved disk state)
    private boolean firebaseEnabledOverride = false;
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
        // Read in-memory state passed from ManageLibraryActivity
        if (getIntent().hasExtra("firebase_enabled")) {
            firebaseEnabledOverride = getIntent().getBooleanExtra("firebase_enabled", false);
            appCompatEnabledOverride = getIntent().getBooleanExtra("app_compat_enabled", false);
            hasOverride = true;
        }
        EdgeToEdge.enable(this);
        binding = ActivityFirebaseExtrasBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        ProjectSettingsStore store = new ProjectSettingsStore(this, scId);
        settings = new LibraryExtrasSettings(store);
        oneSignalSettings = new OneSignalSettingsManager(store);
        initialize();
    }

    private void initialize() {
        // ── Restore saved states ────────────────────────────────────────────
        binding.swAnalytics.setChecked(settings.isUseGoogleAnalytics());
        binding.swBilling.setChecked(settings.isUseAndroidBilling());
        binding.swOnesignal.setChecked(settings.isUseOneSignal());

        // ── OneSignal App ID ─────────────────────────────────────────────────
        String savedAppId = oneSignalSettings.getAppId();
        if (!savedAppId.isEmpty()) {
            binding.etOnesignalAppid.setText(savedAppId);
        }
        // Show/hide App ID field based on current switch state
        updateOneSignalAppIdVisibility(settings.isUseOneSignal());

        binding.etOnesignalAppid.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                oneSignalSettings.setAppId(s != null ? s.toString().trim() : "");
            }
        });

        // ── Listeners ───────────────────────────────────────────────────────
        binding.swAnalytics.setOnCheckedChangeListener((b, checked) ->
                settings.setUseGoogleAnalytics(checked));
        binding.swBilling.setOnCheckedChangeListener((b, checked) ->
                settings.setUseAndroidBilling(checked));
        binding.swOnesignal.setOnCheckedChangeListener((b, checked) -> {
            settings.setUseOneSignal(checked);
            updateOneSignalAppIdVisibility(checked);
        });

        // ── Row click toggles ────────────────────────────────────────────────
        binding.lnAnalytics.setOnClickListener(v -> binding.swAnalytics.toggle());
        binding.lnBilling.setOnClickListener(v -> binding.swBilling.toggle());
        binding.lnOnesignal.setOnClickListener(v -> binding.swOnesignal.toggle());

        // ── Availability guards ──────────────────────────────────────────────
        initializeAnalytics();
        initializeBilling();
        initializeOneSignal();
    }

    private void updateOneSignalAppIdVisibility(boolean enabled) {
        binding.lnOnesignalAppid.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    // ── Guards ───────────────────────────────────────────────────────────────

    private boolean isFirebaseEnabled() {
        if (hasOverride) return firebaseEnabledOverride;
        ProjectLibraryBean fb = jC.c(scId).d();
        return fb != null && ProjectLibraryBean.LIB_USE_Y.equals(fb.useYn);
    }

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

    private void initializeAnalytics() {
        boolean ok = true;
        if (getMinSdk() < 24) {
            ok = false;
            binding.tvAnalyticsNote.setText("To use, min SDK required is 24 or newer (Android 7+). "
                    + binding.tvAnalyticsNote.getText());
        } else if (isJava7()) {
            ok = false;
            binding.tvAnalyticsNote.setText("To use, use a newer version of Java. "
                    + binding.tvAnalyticsNote.getText());
        } else if (!isFirebaseEnabled()) {
            ok = false;
            binding.tvAnalyticsNote.setText("To use, enable Firebase. "
                    + binding.tvAnalyticsNote.getText());
        }
        setRowEnabled(binding.lnAnalytics, ok);
    }

    private void initializeBilling() {
        boolean ok = true;
        if (!isFirebaseEnabled()) {
            ok = false;
            binding.tvBillingNote.setText("To use, enable Firebase. "
                    + binding.tvBillingNote.getText());
        } else if (!isAppCompatEnabled()) {
            ok = false;
            binding.tvBillingNote.setText("To use, enable AppCompat. "
                    + binding.tvBillingNote.getText());
        }
        setRowEnabled(binding.lnBilling, ok);
    }

    private void initializeOneSignal() {
        boolean ok = true;
        if (!isFirebaseEnabled()) {
            ok = false;
            binding.tvOnesignalNote.setText("To use, enable Firebase. "
                    + binding.tvOnesignalNote.getText());
        } else if (!isAppCompatEnabled()) {
            ok = false;
            binding.tvOnesignalNote.setText("To use, enable AppCompat. "
                    + binding.tvOnesignalNote.getText());
        }
        setRowEnabled(binding.lnOnesignal, ok);
        // Also disable App ID field when OneSignal itself is unavailable
        if (!ok) {
            binding.lnOnesignalAppid.setVisibility(View.GONE);
        }
    }

    private void setRowEnabled(View row, boolean enabled) {
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1f : 0.5f);
    }
}

