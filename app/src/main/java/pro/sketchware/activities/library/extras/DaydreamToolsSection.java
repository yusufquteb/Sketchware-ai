package pro.sketchware.activities.library.extras;

import android.content.Context;

import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.R;
import pro.sketchware.settings.LibraryExtrasSettings;
import pro.sketchware.settings.ProjectSettingsStore;

/**
 * Builds the "daydream tools" cards shown inside {@link DaydreamToolsActivity},
 * a dedicated page opened from a single row in ManageLibraryActivity (under
 * "External libraries"). Ported from daydream's LibrarySettings / GoogleSettings
 * / UISettings screens (same rows, same dependency notes, same enable/disable
 * guards) but wired to this project's own data layer (LibraryExtrasSettings)
 * instead of daydream's separate DayDreamProjectSettings/DataDayDream.json
 * system, so state always stays in sync with the rest of the Library Manager.
 * <p>
 * Renders as three titled sub-cards: "Firebase & Google", "User Interface",
 * and "Extra Libraries".
 */
public class DaydreamToolsSection {

    private final Context context;
    private final String scId;
    private final LibraryExtrasSettings settings;

    // Rows that need to be re-validated when Firebase/AppCompat state changes
    // in the parent ManageLibraryActivity (before the user has saved).
    private DaydreamToolItemView rowAnalytics;
    private DaydreamToolItemView rowBilling;

    private DaydreamToolItemView rowWorkManager;
    private DaydreamToolItemView rowMedia3;
    private DaydreamToolItemView rowBrowser;
    private DaydreamToolItemView rowCredentialManager;
    private DaydreamToolItemView rowGlideTransforms;
    private DaydreamToolItemView rowShizuku;

    private DaydreamToolItemView rowEdgeToEdge;
    private DaydreamToolItemView rowWindowInsets;
    private DaydreamToolItemView rowTextColorRemoval;
    private DaydreamToolItemView rowBackGesture;

    public DaydreamToolsSection(Context context, String scId) {
        this.context = context;
        this.scId = scId;
        ProjectSettingsStore store = new ProjectSettingsStore(context, scId);
        this.settings = new LibraryExtrasSettings(store);
    }

    /**
     * Builds and adds the full "daydream tools" category (with its three
     * sub-cards) into {@code container}.
     *
     * @param firebaseEnabled  current (possibly-unsaved) Firebase state
     * @param appCompatEnabled current (possibly-unsaved) AppCompat state
     */
    public void build(android.view.ViewGroup container, boolean firebaseEnabled, boolean appCompatEnabled) {
        buildFirebaseGoogleCard(container);
        buildUserInterfaceCard(container);
        buildExtraLibrariesCard(container);

        refreshAvailability(firebaseEnabled, appCompatEnabled);
    }

    // ── Firebase & Google ───────────────────────────────────────────────────

    private void buildFirebaseGoogleCard(android.view.ViewGroup parent) {
        DaydreamSubCardView card = new DaydreamSubCardView(context, R.drawable.ic_mtrl_firebase, "Firebase & Google");
        parent.addView(card);

        rowAnalytics = new DaydreamToolItemView(context)
                .setTitle("Analytics")
                .setDescription("Track your app's success every day. Once enabled, the necessary libraries will be added and Google Analytics will start running in MainActivity.")
                .setChecked(settings.isUseGoogleAnalytics());
        rowAnalytics.setOnCheckedChangeListener((b, checked) -> settings.setUseGoogleAnalytics(checked));
        card.addToolItem(rowAnalytics, true);

        rowBilling = new DaydreamToolItemView(context)
                .setTitle("Android Billing")
                .setDescription("In-app purchases with the Google Play Store. Note: This only works if the app is installed from the Google Play Store or Android Studio. If installed from another source, it will fail and won't work.")
                .setChecked(settings.isUseAndroidBilling());
        rowBilling.setOnCheckedChangeListener((b, checked) -> settings.setUseAndroidBilling(checked));
        card.addToolItem(rowBilling, false);
    }

    // ── User Interface ──────────────────────────────────────────────────────

    private void buildUserInterfaceCard(android.view.ViewGroup parent) {
        DaydreamSubCardView card = new DaydreamSubCardView(context, R.drawable.ic_mtrl_devices, "User Interface");
        parent.addView(card);

        rowEdgeToEdge = new DaydreamToolItemView(context)
                .setTitle("Edge-to-Edge")
                .setDescription("Display content that spans the entire screen, including below the navigation bar and notch.")
                .setChecked(settings.isEdgeToEdge());
        rowEdgeToEdge.setOnCheckedChangeListener((b, checked) -> settings.setEdgeToEdge(checked));
        card.addToolItem(rowEdgeToEdge, true);

        rowWindowInsets = new DaydreamToolItemView(context)
                .setTitle("Window insets handling")
                .setDescription("Suggestions to enable when using Edge-to-Edge. App content will not be obscured by status bars, navigation, or notch.")
                .setChecked(settings.isWindowInsetsHandling());
        rowWindowInsets.setOnCheckedChangeListener((b, checked) -> settings.setWindowInsetsHandling(checked));
        card.addToolItem(rowWindowInsets, true);

        rowTextColorRemoval = new DaydreamToolItemView(context)
                .setTitle("Remove default color set for text")
                .setDescription("When enabled, the default black color that Sketchware Pro sets for text in TextView, EditText, Button etc. will be removed and their text color will automatically change according to the theme.")
                .setChecked(settings.isTextColorRemoval());
        rowTextColorRemoval.setOnCheckedChangeListener((b, checked) -> settings.setTextColorRemoval(checked));
        card.addToolItem(rowTextColorRemoval, true);

        rowBackGesture = new DaydreamToolItemView(context)
                .setTitle("Predictive back gesture")
                .setDescription("If your app targets SDK 36 (Android 16), enable this to use OnBackInvokedCallback. By default on Android 16, onBackPressed() is ignored and the Activity is exited immediately.")
                .setChecked(settings.isBackGesture());
        rowBackGesture.setOnCheckedChangeListener((b, checked) -> settings.setBackGesture(checked));
        card.addToolItem(rowBackGesture, false);
    }

    // ── Extra Libraries ─────────────────────────────────────────────────────

    private void buildExtraLibrariesCard(android.view.ViewGroup parent) {
        DaydreamSubCardView card = new DaydreamSubCardView(context, R.drawable.ic_mtrl_component, "Extra Libraries");
        parent.addView(card);

        rowWorkManager = new DaydreamToolItemView(context)
                .setTitle("AndroidX WorkManager")
                .setDescription("The WorkManager library is only included by default when using Google AdMob for testing ads. If you need WorkManager in production, enable this.")
                .setChecked(settings.isForceWorkManager());
        rowWorkManager.setOnCheckedChangeListener((b, checked) -> settings.setForceWorkManager(checked));
        card.addToolItem(rowWorkManager, true);

        rowMedia3 = new DaydreamToolItemView(context)
                .setTitle("AndroidX Media3")
                .setDescription("Found VideoView limited? Switch to Media3 now for more format support, better streaming stability, subtitles, playback speed, and more. Requires min SDK 24+, AppCompat, and Java 8+.")
                .setChecked(settings.isUseMedia3());
        rowMedia3.setOnCheckedChangeListener((b, checked) -> settings.setUseMedia3(checked));
        card.addToolItem(rowMedia3, true);

        rowBrowser = new DaydreamToolItemView(context)
                .setTitle("AndroidX Browser")
                .setDescription("Want faster page loading, more security, and quick login for your users? Try replacing WebView with AndroidX Browser. This library is imported by default only when using Google AdMob with testing.")
                .setChecked(settings.isUseBrowser());
        rowBrowser.setOnCheckedChangeListener((b, checked) -> settings.setUseBrowser(checked));
        card.addToolItem(rowBrowser, true);

        rowCredentialManager = new DaydreamToolItemView(context)
                .setTitle("AndroidX Credential Manager")
                .setDescription("The modern new way to sign in for Google accounts and more. Requires min SDK 24+, AppCompat, and Java 8+.")
                .setChecked(settings.isUseCredentialManager());
        rowCredentialManager.setOnCheckedChangeListener((b, checked) -> settings.setUseCredentialManager(checked));
        card.addToolItem(rowCredentialManager, true);

        rowGlideTransforms = new DaydreamToolItemView(context)
                .setTitle("Glide Transformations")
                .setDescription("A transformation library providing a variety of image transformations for Glide.")
                .setChecked(settings.isUseGlideTransformations());
        rowGlideTransforms.setOnCheckedChangeListener((b, checked) -> settings.setUseGlideTransformations(checked));
        card.addToolItem(rowGlideTransforms, true);

        rowShizuku = new DaydreamToolItemView(context)
                .setTitle("Shizuku")
                .setDescription("Shizuku allows normal apps to use system APIs directly with elevated privileges using ADB on non-rooted devices. Currently at version 12.2.0. Requires min SDK 24+ and AppCompat.")
                .setChecked(settings.isUseShizuku());
        rowShizuku.setOnCheckedChangeListener((b, checked) -> settings.setUseShizuku(checked));
        card.addToolItem(rowShizuku, false);
    }

    // ── Dependency guards (mirrors daydream's LibrarySettings/GoogleSettings/UISettings) ──

    /**
     * Re-evaluates every row's dependency note and enabled/disabled state,
     * using the given live (possibly-unsaved) Firebase/AppCompat state from
     * the parent ManageLibraryActivity. Call this after building, and again
     * whenever Firebase/AppCompat are toggled elsewhere on this screen.
     */
    public void refreshAvailability(boolean firebaseEnabled, boolean appCompatEnabled) {
        boolean java7 = isJava7();
        int minSdk = getMinSdk();

        // Analytics: min SDK 24+, Java 8+, Firebase
        if (minSdk < 24) {
            rowAnalytics.setDependencyNote("To use, min SDK required is 24 or newer (Android 7+).");
        } else if (java7) {
            rowAnalytics.setDependencyNote("To use, use a newer version of Java.");
        } else if (!firebaseEnabled) {
            rowAnalytics.setDependencyNote("To use, enable Firebase.");
        } else {
            rowAnalytics.setDependencyNote(null);
        }

        // Android Billing: Firebase, AppCompat
        if (!firebaseEnabled) {
            rowBilling.setDependencyNote("To use, enable Firebase.");
        } else if (!appCompatEnabled) {
            rowBilling.setDependencyNote("To use, enable AppCompat.");
        } else {
            rowBilling.setDependencyNote(null);
        }

        // Edge-to-Edge: AppCompat
        if (!appCompatEnabled) {
            rowEdgeToEdge.setDependencyNote("To use, enable AppCompat.");
        } else {
            rowEdgeToEdge.setDependencyNote(null);
        }

        // Window insets handling: AppCompat, Java 8+
        if (!appCompatEnabled) {
            rowWindowInsets.setDependencyNote("To use, enable AppCompat.");
        } else if (java7) {
            rowWindowInsets.setDependencyNote("To use, use a newer version of Java.");
        } else {
            rowWindowInsets.setDependencyNote(null);
        }

        // Text color removal & Predictive back gesture: no dependencies in daydream

        // WorkManager: AppCompat
        if (!appCompatEnabled) {
            rowWorkManager.setDependencyNote("To use, enable AppCompat.");
        } else {
            rowWorkManager.setDependencyNote(null);
        }

        // Media3: min SDK 24+, AppCompat, Java 8+
        if (minSdk < 24) {
            rowMedia3.setDependencyNote("To use, min SDK required is 24 or newer (Android 7+).");
        } else if (!appCompatEnabled) {
            rowMedia3.setDependencyNote("To use, enable AppCompat.");
        } else if (java7) {
            rowMedia3.setDependencyNote("To use, use a newer version of Java.");
        } else {
            rowMedia3.setDependencyNote(null);
        }

        // Browser: AppCompat
        if (!appCompatEnabled) {
            rowBrowser.setDependencyNote("To use, enable AppCompat.");
        } else {
            rowBrowser.setDependencyNote(null);
        }

        // Credential Manager: min SDK 24+, AppCompat, Java 8+
        if (minSdk < 24) {
            rowCredentialManager.setDependencyNote("To use, min SDK required is 24 or newer (Android 7+).");
        } else if (!appCompatEnabled) {
            rowCredentialManager.setDependencyNote("To use, enable AppCompat.");
        } else if (java7) {
            rowCredentialManager.setDependencyNote("To use, use a newer version of Java.");
        } else {
            rowCredentialManager.setDependencyNote(null);
        }

        // Glide Transformations: no dependencies in daydream

        // Shizuku: min SDK 24+, AppCompat
        if (minSdk < 24) {
            rowShizuku.setDependencyNote("To use, min SDK required is 24 or newer (Android 7+).");
        } else if (!appCompatEnabled) {
            rowShizuku.setDependencyNote("To use, enable AppCompat.");
        } else {
            rowShizuku.setDependencyNote(null);
        }
    }

    private boolean isJava7() {
        BuildSettings bs = new BuildSettings(scId);
        return BuildSettings.SETTING_JAVA_VERSION_1_7.equals(
                bs.getValue(BuildSettings.SETTING_JAVA_VERSION, BuildSettings.SETTING_JAVA_VERSION_1_8));
    }

    private int getMinSdk() {
        return new ProjectSettings(scId).getMinSdkVersion();
    }
}
