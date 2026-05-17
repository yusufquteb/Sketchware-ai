package pro.sketchware.utility;

import androidx.annotation.Nullable;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

public final class CrashlyticsBridge {
    private static volatile boolean unavailable;

    private CrashlyticsBridge() {
    }

    @Nullable
    public static FirebaseCrashlytics getInstance() {
        if (unavailable) {
            return null;
        }
        try {
            return FirebaseCrashlytics.getInstance();
        } catch (Throwable ignored) {
            unavailable = true;
            return null;
        }
    }

    public static void log(String message) {
        FirebaseCrashlytics crashlytics = getInstance();
        if (crashlytics != null) {
            crashlytics.log(message);
        }
    }

    public static void recordException(Throwable throwable) {
        FirebaseCrashlytics crashlytics = getInstance();
        if (crashlytics != null && throwable != null) {
            crashlytics.recordException(throwable);
        }
    }
}
