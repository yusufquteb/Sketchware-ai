package pro.sketchware.util;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * LogcatManager provides the AI Agent with the ability to read runtime logs.
 * This is crucial for debugging runtime crashes (Runtime Exceptions).
 */
public class LogcatManager {

    private static final String TAG = "LogcatManager";

    public static String getRecentLogs(String filterKeyword) {
        StringBuilder logs = new StringBuilder();
        try {
            // -d dumps the log and exits
            // -v time includes timestamps
            Process process = Runtime.getRuntime().exec("logcat -d -v time");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (filterKeyword == null || filterKeyword.isEmpty() || line.contains(filterKeyword)) {
                    logs.append(line).append("\n");
                }
            }
            process.destroy();
        } catch (Exception e) {
            return "Error reading logcat: " + e.getMessage();
        }
        return logs.length() == 0 ? "No relevant logs found." : logs.toString();
    }

    public static String getCrashLogs() {
        // Filters for 'AndroidRuntime' and 'FATAL EXCEPTION'
        return getRecentLogs("AndroidRuntime");
    }
}
