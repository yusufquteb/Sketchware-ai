package pro.sketchware.activities.settings;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.materialswitch.MaterialSwitch;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.main.activities.MainActivity;
import pro.sketchware.databinding.ActivityPermissionsBinding;
import pro.sketchware.databinding.ItemPermissionRowBinding;

/**
 * A single consolidated screen for every runtime/manifest permission the app
 * actually uses. Replaces the scattered permission dialogs that used to show
 * up individually across different screens (storage access notice, Android 11
 * "all files access" notice, etc).
 *
 * Only permissions that are genuinely required by app features are listed here:
 *  - Storage (read/write project files, merged into a single toggle)
 *  - Notifications (build progress and alerts)
 *  - Install unknown apps (needed to install the APKs this app builds)
 *  - Microphone (voice input for the AI assistant)
 *
 * Each row mirrors the real system permission state and routes taps through
 * the proper system flow (runtime prompt or the app's Settings screen),
 * since permissions can't be toggled programmatically.
 *
 * This screen also doubles as the first-run onboarding step: on a fresh
 * install, MainActivity redirects here before showing anything else. See
 * {@link #shouldShowOnFirstRun(Context)}.
 */
public class PermissionsActivity extends BaseAppCompatActivity {

    private static final String PREFS_NAME = "permissions_onboarding";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";
    public static final String EXTRA_ONBOARDING = "extra_onboarding";

    private ActivityPermissionsBinding binding;

    private MaterialSwitch storageSwitch;
    private MaterialSwitch notificationsSwitch;
    private MaterialSwitch installApkSwitch;
    private MaterialSwitch microphoneSwitch;

    private boolean onboardingMode;

    /** Whether MainActivity should redirect to this screen before showing its own UI. */
    public static boolean shouldShowOnFirstRun(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return !prefs.getBoolean(KEY_ONBOARDING_DONE, false);
    }

    private static void markOnboardingDone(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ONBOARDING_DONE, true)
                .apply();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityPermissionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        onboardingMode = getIntent().getBooleanExtra(EXTRA_ONBOARDING, false);

        {
            View view = binding.appBarLayout;
            int left = view.getPaddingLeft();
            int top = view.getPaddingTop();
            int right = view.getPaddingRight();
            int bottom = view.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + insets.left, top + insets.top, right + insets.right, bottom + insets.bottom);
                return i;
            });
        }

        {
            View view = binding.contentScroll;
            int left = view.getPaddingLeft();
            int top = view.getPaddingTop();
            int right = view.getPaddingRight();
            int bottom = view.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(left, top, right, bottom + insets.bottom);
                return i;
            });
        }

        if (onboardingMode) {
            // First-run onboarding: no back navigation, no "close" affordance —
            // the only way forward is the Continue button.
            binding.topAppBar.setNavigationIcon(null);
            binding.permissionsIntro.setText("Welcome — let's set up permissions");
            binding.btnContinue.setVisibility(View.VISIBLE);
            binding.btnContinue.setOnClickListener(v -> finishOnboarding());
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    // Swallow back presses during onboarding instead of exiting the app.
                }
            });
        } else {
            binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        }

        buildRows();
    }

    private void finishOnboarding() {
        markOnboardingDone(this);
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().setAutoInitEnabled(true);
        } catch (Throwable ignored) {
        }
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reflect the real system state every time the screen becomes visible
        // (e.g. after coming back from the system Settings screen).
        refreshSwitchStates();
    }

    private void buildRows() {
        storageSwitch = addPermissionRow(
                R.drawable.ic_mtrl_folder,
                "Storage",
                "Read and write project files",
                this::onStorageRowClicked
        );

        // POST_NOTIFICATIONS is only a runtime permission from Android 13 (API 33)
        // onward. Below that, notifications are enabled by default at the OS level
        // and there is no system dialog to show — so this row is only relevant
        // (and only added) on API 33+, matching hasNotificationPermission()'s
        // SDK_INT >= 33 check below.
        if (Build.VERSION.SDK_INT >= 33) {
            notificationsSwitch = addPermissionRow(
                    R.drawable.ic_mtrl_notifications,
                    "Notifications",
                    "Build progress and alerts",
                    this::onNotificationsRowClicked
            );
        }

        installApkSwitch = addPermissionRow(
                R.drawable.ic_mtrl_apk_install,
                "Install unknown apps",
                "Required to install the APKs this app builds",
                this::onInstallApkRowClicked
        );

        microphoneSwitch = addPermissionRow(
                R.drawable.ic_mtrl_mic,
                "Microphone",
                "Voice input for AI assistant",
                this::onMicrophoneRowClicked
        );
    }

    private MaterialSwitch addPermissionRow(int icon, String title, String description, View.OnClickListener onClick) {
        ItemPermissionRowBinding row = ItemPermissionRowBinding.inflate(getLayoutInflater(), binding.permissionsContainer, false);

        ImageView iconView = row.permissionIcon;
        TextView titleView = row.permissionTitle;
        TextView descView = row.permissionDesc;
        MaterialSwitch switchView = row.permissionSwitch;

        iconView.setImageResource(icon);
        titleView.setText(title);
        descView.setText(description);

        // The switch only mirrors real system state; taps go through the
        // proper system flow (runtime prompt or Settings screen) instead of
        // being toggled directly, since permissions can't be granted/revoked
        // programmatically.
        switchView.setClickable(false);
        switchView.setFocusable(false);
        row.getRoot().setOnClickListener(onClick);

        binding.permissionsContainer.addView(row.getRoot());
        return switchView;
    }

    private void refreshSwitchStates() {
        if (storageSwitch != null) storageSwitch.setChecked(hasStoragePermission());
        if (notificationsSwitch != null) notificationsSwitch.setChecked(hasNotificationPermission());
        if (installApkSwitch != null) installApkSwitch.setChecked(canInstallApks());
        if (microphoneSwitch != null) microphoneSwitch.setChecked(hasMicrophonePermission());
    }

    // ── Storage ──────────────────────────────────────────────────────────

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT > 29) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void onStorageRowClicked(View v) {
        if (hasStoragePermission()) {
            openAppSettingsScreen();
            return;
        }
        if (Build.VERSION.SDK_INT > 29) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                openAppSettingsScreen();
            }
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_STORAGE);
        }
    }

    // ── Notifications ────────────────────────────────────────────────────
    // This row only exists in buildRows() on API 33+ (see above) since
    // POST_NOTIFICATIONS isn't a runtime permission before Android 13 — there's
    // no system dialog to show and no state for the user to change from here.
    // The SDK_INT checks below are kept anyway as a defensive fallback in case
    // these methods are ever called directly (e.g. from another screen).

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Not a runtime permission before Android 13
    }

    private void onNotificationsRowClicked(View v) {
        if (hasNotificationPermission()) {
            openAppSettingsScreen();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    // ── Install unknown apps ─────────────────────────────────────────────

    private boolean canInstallApks() {
        if (Build.VERSION.SDK_INT >= 26) {
            return getPackageManager().canRequestPackageInstalls();
        }
        return true; // Controlled by a global device setting before Android 8
    }

    private void onInstallApkRowClicked(View v) {
        if (Build.VERSION.SDK_INT >= 26) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception e) {
                openAppSettingsScreen();
            }
        }
    }

    // ── Microphone ───────────────────────────────────────────────────────

    private boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void onMicrophoneRowClicked(View v) {
        if (hasMicrophonePermission()) {
            openAppSettingsScreen();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
    }

    // ── Shared helpers ───────────────────────────────────────────────────

    private void openAppSettingsScreen() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshSwitchStates();
    }

    private static final int REQUEST_STORAGE = 5001;
    private static final int REQUEST_NOTIFICATIONS = 5002;
    private static final int REQUEST_MICROPHONE = 5003;
}
