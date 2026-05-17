package pro.sketchware.activities.library.extras;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Objects;

import a.a.a.jC;
import com.besome.sketch.beans.ProjectLibraryBean;
import mod.hey.studios.build.BuildSettings;
import pro.sketchware.databinding.ActivityUiSettingsLibraryBinding;
import pro.sketchware.settings.LibraryExtrasSettings;
import pro.sketchware.settings.ProjectSettingsStore;

public class UiSettingsLibraryActivity extends AppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    private String scId;
    private ActivityUiSettingsLibraryBinding binding;
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
        binding = ActivityUiSettingsLibraryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        settings = new LibraryExtrasSettings(new ProjectSettingsStore(this, scId));
        initialize();
    }

    private void initialize() {
        // ── Restore saved states ─────────────────────────────────────────────
        binding.swEdgeToEdge.setChecked(settings.isEdgeToEdge());
        binding.swWindowInsets.setChecked(settings.isWindowInsetsHandling());
        binding.swTextColorRemoval.setChecked(settings.isTextColorRemoval());
        binding.swBackGesture.setChecked(settings.isBackGesture());

        // ── Listeners ────────────────────────────────────────────────────────
        binding.swEdgeToEdge.setOnCheckedChangeListener((b, checked) ->
                settings.setEdgeToEdge(checked));
        binding.swWindowInsets.setOnCheckedChangeListener((b, checked) ->
                settings.setWindowInsetsHandling(checked));
        binding.swTextColorRemoval.setOnCheckedChangeListener((b, checked) ->
                settings.setTextColorRemoval(checked));
        binding.swBackGesture.setOnCheckedChangeListener((b, checked) ->
                settings.setBackGesture(checked));

        // ── Row click toggles ────────────────────────────────────────────────
        binding.lnEdgeToEdge.setOnClickListener(v -> binding.swEdgeToEdge.toggle());
        binding.lnWindowInsets.setOnClickListener(v -> binding.swWindowInsets.toggle());
        binding.lnTextColorRemoval.setOnClickListener(v -> binding.swTextColorRemoval.toggle());
        binding.lnBackGesture.setOnClickListener(v -> binding.swBackGesture.toggle());

        // ── Availability guards ──────────────────────────────────────────────
        initializeEdgeToEdge();
        initializeWindowInsets();
    }

    // ── Guards ────────────────────────────────────────────────────────────────

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

    private void initializeEdgeToEdge() {
        if (!isAppCompatEnabled()) {
            binding.tvEdgeToEdgeNote.setText("To use, enable AppCompat. "
                    + binding.tvEdgeToEdgeNote.getText());
            setRowEnabled(binding.lnEdgeToEdge, false);
        }
    }

    private void initializeWindowInsets() {
        boolean ok = true;
        if (!isAppCompatEnabled()) {
            ok = false;
            binding.tvWindowInsetsNote.setText("To use, enable AppCompat. "
                    + binding.tvWindowInsetsNote.getText());
        } else if (isJava7()) {
            ok = false;
            binding.tvWindowInsetsNote.setText("To use, use a newer version of Java. "
                    + binding.tvWindowInsetsNote.getText());
        }
        setRowEnabled(binding.lnWindowInsets, ok);
    }

    private void setRowEnabled(android.view.View row, boolean enabled) {
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1f : 0.5f);
    }
}
