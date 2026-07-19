package <?package_name?>;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import <?class_name_package?>.<?class_name?>;

/**
 * فئة SketchLogger معدلة لضمان التشغيل والإيقاف الآمن.
 */
public class SketchLogger {
    private static volatile boolean isRunning = false;
    private static Thread loggerThread;
    private static Process logcatProcess;

    public static synchronized void startLogging() {
        if (isRunning) {
            broadcastLog("Logger already running");
            return;
        }

        isRunning = true;
        loggerThread = new Thread(() -> {
            try {
                // مسح السجلات القديمة
                Runtime.getRuntime().exec("logcat -c");
                // تشغيل عملية logcat
                logcatProcess = Runtime.getRuntime().exec("logcat");

                try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(logcatProcess.getInputStream()))) {
                    String logTxt;
                    // حلقة قراءة السجلات
                    while (isRunning && (logTxt = bufferedReader.readLine()) != null) {
                        broadcastLog(logTxt);
                    }
                }
            } catch (IOException e) {
                Log.e("SketchLogger", "Error reading logcat: " + e.getMessage());
            } finally {
                // التأكد من إغلاق الموارد عند توقف الحلقة
                stopProcess();
                isRunning = false;
            }
        });
        
        loggerThread.start();
        broadcastLog("Logger started.");
    }

    public static synchronized void stopLogging() {
        if (!isRunning) {
            broadcastLog("Logger not running");
            return;
        }
        
        isRunning = false;
        stopProcess();
        broadcastLog("Stopping logger by user request.");
    }

    private static void stopProcess() {
        if (logcatProcess != null) {
            logcatProcess.destroy();
            logcatProcess = null;
        }
    }

    public static void broadcastLog(String log) {
        try {
            Context context = <?class_name?>.getContext();
            if (context != null) {
                Intent intent = new Intent();
                intent.setAction("pro.sketchware.ACTION_NEW_DEBUG_LOG");
                intent.putExtra("log", log);
                intent.putExtra("packageName", context.getPackageName());
                context.sendBroadcast(intent);
            }
        } catch (Exception e) {
            Log.e("SketchLogger", "Failed to broadcast log: " + e.getMessage());
        }
    }
}
