package pro.sketchware.compiler;

import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Measures wall-clock time for each phase of a project build.
 *
 * Usage:
 *   BuildProfiler profiler = new BuildProfiler();
 *   profiler.start("ECJ");
 *   ... compile ...
 *   profiler.stop("ECJ");
 *   profiler.log("MyBuild");
 */
public final class BuildProfiler {

    private static final String TAG = "BuildProfiler";

    private final Map<String, Long> startTimes = new LinkedHashMap<>();
    private final Map<String, Long> durations = new LinkedHashMap<>();
    private final long buildStart = System.currentTimeMillis();

    public void start(String phase) {
        startTimes.put(phase, System.currentTimeMillis());
    }

    public long stop(String phase) {
        Long start = startTimes.remove(phase);
        if (start == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - start;
        durations.merge(phase, elapsed, Long::sum);
        return elapsed;
    }

    /** Logs a summary of all recorded phase durations. */
    public void log(String buildLabel) {
        long totalBuild = System.currentTimeMillis() - buildStart;
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════╗\n");
        sb.append(String.format("║  Build Profile — %-24s║\n", buildLabel));
        sb.append("╠══════════════════════════════════════════╣\n");

        long accounted = 0;
        for (Map.Entry<String, Long> entry : durations.entrySet()) {
            long ms = entry.getValue();
            accounted += ms;
            int bar = (int) Math.min(20, (ms * 20) / Math.max(1, totalBuild));
            String filled = "█".repeat(bar) + "░".repeat(20 - bar);
            sb.append(String.format("║  %-18s %s %5d ms ║\n", entry.getKey(), filled, ms));
        }

        long overhead = totalBuild - accounted;
        if (overhead > 0) {
            int bar = (int) Math.min(20, (overhead * 20) / Math.max(1, totalBuild));
            String filled = "█".repeat(bar) + "░".repeat(20 - bar);
            sb.append(String.format("║  %-18s %s %5d ms ║\n", "other/overhead", filled, overhead));
        }

        sb.append("╠══════════════════════════════════════════╣\n");
        sb.append(String.format("║  %-18s %s %5d ms ║\n", "TOTAL", "████████████████████", totalBuild));
        sb.append("╚══════════════════════════════════════════╝");
        Log.i(TAG, sb.toString());
    }
}
