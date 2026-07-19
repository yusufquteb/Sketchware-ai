package pro.sketchware.ai.utils;

import android.util.Log;
import pro.sketchware.BuildConfig;

/**
 * Debug-only wrapper for android.util.Log.
 * Log.d and Log.v calls are stripped in release builds (BuildConfig.DEBUG == false).
 * Log.i, Log.w, Log.e pass through unconditionally.
 */
public final class AiLog {

    private AiLog() {}

    public static void d(String tag, String msg) {
        if (BuildConfig.DEBUG) Log.d(tag, msg);
    }

    public static void v(String tag, String msg) {
        if (BuildConfig.DEBUG) Log.v(tag, msg);
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
    }

    public static void w(String tag, String msg, Throwable tr) {
        Log.w(tag, msg, tr);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
    }

    public static void e(String tag, String msg, Throwable tr) {
        Log.e(tag, msg, tr);
    }
}
