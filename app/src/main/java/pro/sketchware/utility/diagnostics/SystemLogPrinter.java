package pro.sketchware.utility.diagnostics;

import android.util.Log;

public final class SystemLogPrinter {
    private SystemLogPrinter() {}

    public static void info(String tag, String message) { Log.i(clean(tag), safe(message)); }
    public static void warn(String tag, String message) { Log.w(clean(tag), safe(message)); }
    public static void error(String tag, String message, Throwable throwable) { Log.e(clean(tag), safe(message), throwable); }

    private static String clean(String tag) {
        if (tag == null || tag.trim().isEmpty()) return "Sketchware";
        return tag.length() > 23 ? tag.substring(0, 23) : tag;
    }

    private static String safe(String message) { return message == null ? "" : message; }
}
