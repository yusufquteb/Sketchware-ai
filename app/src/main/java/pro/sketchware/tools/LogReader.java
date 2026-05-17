package pro.sketchware.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public final class LogReader {
    private LogReader() {}

    public static List<String> read(int maxLines) throws Exception {
        Process process = new ProcessBuilder("logcat", "-d", "-v", "time").redirectErrorStream(true).start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (maxLines > 0 && lines.size() > maxLines) lines.remove(0);
            }
        }
        return lines;
    }
}
