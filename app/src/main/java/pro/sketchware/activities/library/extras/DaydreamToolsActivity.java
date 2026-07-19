package pro.sketchware.activities.library.extras;

import android.content.Intent;
import android.os.Bundle;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import pro.sketchware.databinding.ActivityDaydreamToolsBinding;

/**
 * Standalone page hosting the "daydream tools" cards (Firebase & Google,
 * User Interface, Extra Libraries). Opened from a single "daydream tools"
 * row inside ManageLibraryActivity, under "External libraries" — mirrors
 * how Material3Manager and other sub-screens are launched from Library
 * Manager as separate pages rather than being expanded inline.
 */
public class DaydreamToolsActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";
    public static final String EXTRA_FIREBASE_ENABLED = "firebase_enabled";
    public static final String EXTRA_APP_COMPAT_ENABLED = "app_compat_enabled";

    private ActivityDaydreamToolsBinding binding;
    private DaydreamToolsSection section;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // enableEdgeToEdgeNoContrast() calls EdgeToEdge.enable() which MUST be called
        // before setContentView() per AndroidX documentation.
        enableEdgeToEdgeNoContrast();
        binding = ActivityDaydreamToolsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        String scId = getIntent().getStringExtra(EXTRA_SC_ID);
        boolean firebaseEnabled = getIntent().getBooleanExtra(EXTRA_FIREBASE_ENABLED, false);
        boolean appCompatEnabled = getIntent().getBooleanExtra(EXTRA_APP_COMPAT_ENABLED, false);

        // Toolbar "←" button behaves identically to the back gesture.
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        section = new DaydreamToolsSection(this, scId);
        section.build(binding.container, firebaseEnabled, appCompatEnabled);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Firebase/AppCompat may have been toggled on ManageLibraryActivity's
        // own screen before this page was opened; re-read the freshest state
        // passed in via the intent extras each time this page becomes visible.
        boolean firebaseEnabled = getIntent().getBooleanExtra(EXTRA_FIREBASE_ENABLED, false);
        boolean appCompatEnabled = getIntent().getBooleanExtra(EXTRA_APP_COMPAT_ENABLED, false);
        if (section != null) {
            section.refreshAvailability(firebaseEnabled, appCompatEnabled);
        }
    }

    @Override
    public void finish() {
        // No explicit result payload is needed: every toggle in
        // DaydreamToolsSection writes straight through to LibraryExtrasSettings
        // (on-disk project settings) as soon as it changes, same as the old
        // standalone extras activities did.
        setResult(RESULT_OK, new Intent());
        super.finish();
    }
}
