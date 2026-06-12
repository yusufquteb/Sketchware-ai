package pro.sketchware.utility.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * ThemeManager — night/day mode + color preset for Sketchware Pro.
 *
 * Architecture:
 *   res/values/themes_accent.xml        — light surfaces per preset
 *   res/values-night/themes_accent.xml  — dark surfaces, SAME style names
 *   Android picks the right file automatically at runtime.
 *   One call: getTheme().applyStyle(presetStyleRes(preset), true) — done.
 *
 * Preset pipeline:
 *   ThemeManager.presetStyleRes(preset)  →  R.style resource id (or 0)
 *   BaseAppCompatActivity.onCreate()     →  getTheme().applyStyle(res, true)
 *   Android resource system              →  resolves correct light/dark variant
 */
public class ThemeManager {

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT  = 1;
    public static final int THEME_DARK   = 2;

    // ── Preset IDs — keep in sync with PRESET_NAMES and presetStyleRes() ──
    /** No overlay — raw base app theme (fallback). */
    public static final int PRESET_DEFAULT        = 0;
    /** Modern Blue (light) / Deep Blue IDE (dark) — production default. */
    public static final int PRESET_MODERN_BLUE    = 1;
    /** Indigo Creator (light) / Indigo Night (dark). */
    public static final int PRESET_INDIGO_CREATOR = 2;
    /** Emerald Studio (light) / Emerald Matrix (dark). */
    public static final int PRESET_EMERALD_STUDIO = 3;
    /** Warm Orange (light) / Slate Professional (dark). */
    public static final int PRESET_WARM_ORANGE    = 4;

    private static final String PREF_FILE  = "themedata";
    private static final String KEY_NIGHT  = "idetheme";
    private static final String KEY_PRESET = "color_preset";

    // ── Display names shown in ThemeSettingsActivity ────────────────────
    public static final String[] PRESET_NAMES = {
        "System Default",    // 0 — no overlay
        "Modern Blue",       // 1 — Modern Blue / Deep Blue IDE
        "Indigo Creator",    // 2 — Indigo Creator / Indigo Night
        "Emerald Studio",    // 3 — Emerald Studio / Emerald Matrix
        "Warm Orange",       // 4 — Warm Orange / Slate Professional
    };

    // ── Preview dots: [primary, secondary, tertiary] — LIGHT mode ───────
    public static final int[][] LIGHT_PREVIEW = {
        // 0 Default — approximate base-theme
        {0xFF006493, 0xFF4D5F7C, 0xFF535B8B},
        // 1 Modern Blue
        {0xFF2563EB, 0xFF3B72D8, 0xFF6D28D9},
        // 2 Indigo Creator
        {0xFF4F46E5, 0xFF7C3AED, 0xFF0D9488},
        // 3 Emerald Studio
        {0xFF059669, 0xFF0D9488, 0xFF2563EB},
        // 4 Warm Orange
        {0xFFC2610C, 0xFFD97706, 0xFF15803D},
    };

    // ── Preview dots: [primary, secondary, tertiary] — DARK mode ────────
    public static final int[][] DARK_PREVIEW = {
        // 0 Default
        {0xFF8DCDFF, 0xFFBDC8E8, 0xFFBEC6FF},
        // 1 Deep Blue IDE
        {0xFF60A5FA, 0xFF93C5FD, 0xFFC4B5FD},
        // 2 Indigo Night
        {0xFF818CF8, 0xFFA78BFA, 0xFF5EEAD4},
        // 3 Emerald Matrix
        {0xFF34D399, 0xFF5EEAD4, 0xFF93C5FD},
        // 4 Slate Professional
        {0xFF7DD3FC, 0xFF38BDF8, 0xFFC4B5FD},
    };

    // ── Compatibility aliases ────────────────────────────────────────────

    /** Alias for compatibility with old callers. */
    public static int getCurrentTheme(Context context) {
        return getCurrentNightMode(context);
    }

    /** Alias for compatibility with old callers. */
    public static void applyTheme(Context context, int mode) {
        applyNightMode(context, mode);
    }

    // ── Public API ───────────────────────────────────────────────────────

    public static void applyNightMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_NIGHT, mode).apply();
        switch (mode) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public static int getCurrentNightMode(Context context) {
        return prefs(context).getInt(KEY_NIGHT, THEME_SYSTEM);
    }

    public static boolean isSystemMode(Context context) {
        return getCurrentNightMode(context) == THEME_SYSTEM;
    }

    /** Alias for SettingsAppearanceFragment compatibility. */
    public static boolean isSystemTheme(Context context) {
        return isSystemMode(context);
    }

    /** Alias for SettingsAppearanceFragment compatibility. */
    public static int getSystemAppliedTheme(Context context) {
        int flags = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return flags == Configuration.UI_MODE_NIGHT_YES ? THEME_DARK : THEME_LIGHT;
    }

    public static void setPreset(Context context, int preset) {
        prefs(context).edit().putInt(KEY_PRESET, preset).apply();
    }

    public static int getPreset(Context context) {
        return prefs(context).getInt(KEY_PRESET, PRESET_MODERN_BLUE);
    }

    /**
     * Returns the style resource id for a given preset, or 0 for
     * PRESET_DEFAULT (no overlay — base app theme is used as-is).
     *
     * Called in BaseAppCompatActivity.onCreate() before super.onCreate() so
     * AppCompat resolves the correct theme attributes before inflating views.
     * Android automatically resolves the correct light/dark variant via the
     * resource qualifier (values/ vs values-night/).
     */
    public static int presetStyleRes(int preset) {
        switch (preset) {
            case PRESET_MODERN_BLUE:    return pro.sketchware.R.style.ThemeOverlay_Accent_ModernBlue;
            case PRESET_INDIGO_CREATOR: return pro.sketchware.R.style.ThemeOverlay_Accent_IndigoCreator;
            case PRESET_EMERALD_STUDIO: return pro.sketchware.R.style.ThemeOverlay_Accent_EmeraldStudio;
            case PRESET_WARM_ORANGE:    return pro.sketchware.R.style.ThemeOverlay_Accent_WarmOrange;
            default:                    return 0; // PRESET_DEFAULT: no overlay
        }
    }

    /** Returns true if the device is currently in dark mode (manual or system). */
    public static boolean isDarkMode(Context context) {
        int mode = getCurrentNightMode(context);
        if (mode == THEME_DARK)  return true;
        if (mode == THEME_LIGHT) return false;
        // THEME_SYSTEM — read device configuration
        int flags = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return flags == Configuration.UI_MODE_NIGHT_YES;
    }

    // ── Private ──────────────────────────────────────────────────────────

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }
}
