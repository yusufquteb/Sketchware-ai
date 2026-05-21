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
    /** No overlay — raw base app theme (fallback / "system default"). */
    public static final int PRESET_DEFAULT      = 0;
    /** Deep professional blue — modern SaaS / dashboard look. */
    public static final int PRESET_MODERN_BLUE  = 1;
    /** Cool cyan-teal — AI / tech / data-science aesthetic. */
    public static final int PRESET_AI_TEAL      = 2;
    /** Soft blue-gray — minimal, content-first, Notion-inspired. */
    public static final int PRESET_NEUTRAL      = 3;
    /** Calm premium indigo — focus mode, study, deep work. */
    public static final int PRESET_INDIGO       = 4;
    /** Fresh emerald green — natural, calm, long-session comfort. */
    public static final int PRESET_EMERALD      = 5;
    /** Warm caramel-brown — cosy, premium, unique personality. */
    public static final int PRESET_COFFEE       = 6;

    private static final String PREF_FILE  = "themedata";
    private static final String KEY_NIGHT  = "idetheme";
    private static final String KEY_PRESET = "color_preset";

    // ── Display names shown in ThemeSettingsActivity ────────────────────
    public static final String[] PRESET_NAMES = {
        "System Default",    // 0 — no overlay
        "🔵 Modern Blue",    // 1
        "🤖 AI Teal",        // 2
        "⬜ Neutral Clean",  // 3
        "🔷 Indigo Soft",    // 4
        "🌿 Emerald Soft",   // 5
        "☕ Coffee Warm",    // 6
    };

    // ── Preview dots: [primary, secondary, tertiary] — LIGHT mode ───────
    public static final int[][] LIGHT_PREVIEW = {
        // 0 Default — approximate base-theme blue
        {0xFF006493, 0xFF4D5F7C, 0xFF535B8B},
        // 1 Modern Blue
        {0xFF1B5E9E, 0xFF525F70, 0xFF6B5778},
        // 2 AI Teal
        {0xFF006A60, 0xFF4A6362, 0xFF455E7A},
        // 3 Neutral Clean
        {0xFF4F5660, 0xFF575E6B, 0xFF6B5C72},
        // 4 Indigo Soft
        {0xFF3F48B0, 0xFF5A5C72, 0xFF76536D},
        // 5 Emerald Soft
        {0xFF1A6B47, 0xFF4D6355, 0xFF3A656F},
        // 6 Coffee Warm
        {0xFF7B5A38, 0xFF6E5943, 0xFF53633A},
    };

    // ── Preview dots: [primary, secondary, tertiary] — DARK mode ────────
    public static final int[][] DARK_PREVIEW = {
        // 0 Default
        {0xFF8DCDFF, 0xFFBDC8E8, 0xFFBEC6FF},
        // 1 Modern Blue
        {0xFF9EC8FF, 0xFFBAC8DA, 0xFFD9BAE8},
        // 2 AI Teal
        {0xFF80D5CB, 0xFFB0CCCA, 0xFFA5C8E4},
        // 3 Neutral Clean
        {0xFFB5BCCA, 0xFFBFC5D3, 0xFFD6BCE0},
        // 4 Indigo Soft
        {0xFFBEC2FF, 0xFFC4C4DC, 0xFFE9BAD6},
        // 5 Emerald Soft
        {0xFF8BD5AB, 0xFFB3CCBA, 0xFFA2CFD9},
        // 6 Coffee Warm
        {0xFFEFBA88, 0xFFDBBFA5, 0xFFBACCA3},
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
        return prefs(context).getInt(KEY_PRESET, PRESET_DEFAULT);
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
            case PRESET_MODERN_BLUE:  return pro.sketchware.R.style.ThemeOverlay_Accent_ModernBlue;
            case PRESET_AI_TEAL:      return pro.sketchware.R.style.ThemeOverlay_Accent_AITeal;
            case PRESET_NEUTRAL:      return pro.sketchware.R.style.ThemeOverlay_Accent_NeutralClean;
            case PRESET_INDIGO:       return pro.sketchware.R.style.ThemeOverlay_Accent_IndigoSoft;
            case PRESET_EMERALD:      return pro.sketchware.R.style.ThemeOverlay_Accent_EmeraldSoft;
            case PRESET_COFFEE:       return pro.sketchware.R.style.ThemeOverlay_Accent_CoffeeWarm;
            default:                  return 0; // PRESET_DEFAULT: no overlay
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
