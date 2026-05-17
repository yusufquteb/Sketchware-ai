package pro.sketchware.settings;

/**
 * Manages boolean toggle settings for the extra library features
 * (WorkManager, Media3, Browser, Credential Manager, Glide Transformations,
 * Shizuku, Android Billing, OneSignal, Google Analytics) and UI settings
 * (Edge-to-Edge, Window Insets, Text Color Removal, Back Gesture).
 *
 * Backed by {@link ProjectSettingsStore} (project-scoped SharedPreferences).
 */
public final class LibraryExtrasSettings {

    // ── Keys ────────────────────────────────────────────────────────────────
    public static final String KEY_FORCE_WORKMANAGER       = "lib_force_workmanager";
    public static final String KEY_USE_MEDIA3              = "lib_use_media3";
    public static final String KEY_USE_BROWSER             = "lib_use_browser";
    public static final String KEY_USE_CREDENTIAL_MANAGER  = "lib_use_credential_manager";
    public static final String KEY_USE_GLIDE_TRANSFORMS    = "lib_use_glide_transforms";
    public static final String KEY_USE_SHIZUKU             = "lib_use_shizuku";
    public static final String KEY_USE_BILLING             = "lib_use_billing";
    public static final String KEY_USE_ONESIGNAL           = "lib_use_onesignal";
    public static final String KEY_USE_ANALYTICS           = "lib_use_analytics";
    public static final String KEY_UI_EDGE_TO_EDGE         = "lib_ui_edge_to_edge";
    public static final String KEY_UI_WINDOW_INSETS        = "lib_ui_window_insets";
    public static final String KEY_UI_TEXT_COLOR_REMOVAL   = "lib_ui_text_color_removal";
    public static final String KEY_UI_BACK_GESTURE         = "lib_ui_back_gesture";

    private final ProjectSettingsStore store;

    public LibraryExtrasSettings(ProjectSettingsStore store) {
        this.store = store;
    }

    // ── Extra Libraries ─────────────────────────────────────────────────────
    public boolean isForceWorkManager()         { return store.getBoolean(KEY_FORCE_WORKMANAGER, false); }
    public void    setForceWorkManager(boolean v){ store.putBoolean(KEY_FORCE_WORKMANAGER, v); }

    public boolean isUseMedia3()                { return store.getBoolean(KEY_USE_MEDIA3, false); }
    public void    setUseMedia3(boolean v)      { store.putBoolean(KEY_USE_MEDIA3, v); }

    public boolean isUseBrowser()               { return store.getBoolean(KEY_USE_BROWSER, false); }
    public void    setUseBrowser(boolean v)     { store.putBoolean(KEY_USE_BROWSER, v); }

    public boolean isUseCredentialManager()             { return store.getBoolean(KEY_USE_CREDENTIAL_MANAGER, false); }
    public void    setUseCredentialManager(boolean v)   { store.putBoolean(KEY_USE_CREDENTIAL_MANAGER, v); }

    public boolean isUseGlideTransformations()          { return store.getBoolean(KEY_USE_GLIDE_TRANSFORMS, false); }
    public void    setUseGlideTransformations(boolean v){ store.putBoolean(KEY_USE_GLIDE_TRANSFORMS, v); }

    public boolean isUseShizuku()               { return store.getBoolean(KEY_USE_SHIZUKU, false); }
    public void    setUseShizuku(boolean v)     { store.putBoolean(KEY_USE_SHIZUKU, v); }

    // ── Firebase-dependent Libraries ────────────────────────────────────────
    public boolean isUseAndroidBilling()        { return store.getBoolean(KEY_USE_BILLING, false); }
    public void    setUseAndroidBilling(boolean v){ store.putBoolean(KEY_USE_BILLING, v); }

    public boolean isUseOneSignal()             { return store.getBoolean(KEY_USE_ONESIGNAL, false); }
    public void    setUseOneSignal(boolean v)   { store.putBoolean(KEY_USE_ONESIGNAL, v); }

    public boolean isUseGoogleAnalytics()       { return store.getBoolean(KEY_USE_ANALYTICS, false); }
    public void    setUseGoogleAnalytics(boolean v){ store.putBoolean(KEY_USE_ANALYTICS, v); }

    // ── UI Settings ─────────────────────────────────────────────────────────
    public boolean isEdgeToEdge()               { return store.getBoolean(KEY_UI_EDGE_TO_EDGE, false); }
    public void    setEdgeToEdge(boolean v)     { store.putBoolean(KEY_UI_EDGE_TO_EDGE, v); }

    public boolean isWindowInsetsHandling()             { return store.getBoolean(KEY_UI_WINDOW_INSETS, false); }
    public void    setWindowInsetsHandling(boolean v)   { store.putBoolean(KEY_UI_WINDOW_INSETS, v); }

    public boolean isTextColorRemoval()                 { return store.getBoolean(KEY_UI_TEXT_COLOR_REMOVAL, false); }
    public void    setTextColorRemoval(boolean v)       { store.putBoolean(KEY_UI_TEXT_COLOR_REMOVAL, v); }

    public boolean isBackGesture()              { return store.getBoolean(KEY_UI_BACK_GESTURE, false); }
    public void    setBackGesture(boolean v)    { store.putBoolean(KEY_UI_BACK_GESTURE, v); }
}
