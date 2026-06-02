package pro.sketchware.ai.engine.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight Myers-style line-level diff engine.
 *
 * Produces a human-readable unified diff between two text strings.
 * Used by the Approval Layer to show MEDIUM-risk changes before execution.
 *
 * Output format (unified diff):
 *   "  line"  → unchanged context
 *   "+ line"  → added in new version
 *   "- line"  → removed from old version
 */
public final class DiffEngine {

    private DiffEngine() {}

    /**
     * Computes a unified diff between two text files.
     *
     * @param filename  display name for the diff header
     * @param oldText   original file content
     * @param newText   new file content
     * @param context   number of unchanged lines to show around changes (typically 3)
     * @return FileDiff containing all change hunks
     */
    public static FileDiff diff(String filename, String oldText, String newText, int context) {
        String[] oldLines = splitLines(oldText);
        String[] newLines = splitLines(newText);

        List<DiffEntry> entries = computeLcs(oldLines, newLines);
        List<DiffHunk>  hunks   = groupIntoHunks(entries, context);

        int added   = 0, removed = 0;
        for (DiffEntry e : entries) {
            if (e.type == DiffEntry.Type.ADDED)   added++;
            if (e.type == DiffEntry.Type.REMOVED)  removed++;
        }
        return new FileDiff(filename, hunks, added, removed);
    }

    /** Convenience: diff with default 3-line context. */
    public static FileDiff diff(String filename, String oldText, String newText) {
        return diff(filename, oldText, newText, 3);
    }

    /** Formats a FileDiff as a readable string for display in a Dialog or log. */
    public static String format(FileDiff diff) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(diff.filename).append("\n");
        sb.append("+++ ").append(diff.filename).append("\n");
        for (DiffHunk hunk : diff.hunks) {
            sb.append(hunk.header).append("\n");
            for (DiffEntry e : hunk.entries) {
                switch (e.type) {
                    case ADDED:     sb.append("+ ").append(e.line).append("\n"); break;
                    case REMOVED:   sb.append("- ").append(e.line).append("\n"); break;
                    case UNCHANGED: sb.append("  ").append(e.line).append("\n"); break;
                }
            }
        }
        if (diff.hunks.isEmpty()) sb.append("(no changes)\n");
        return sb.toString();
    }

    // ── LCS-based diff algorithm ─────────────────────────────────────────────

    private static List<DiffEntry> computeLcs(String[] oldLines, String[] newLines) {
        int m = oldLines.length, n = newLines.length;

        // Build LCS table (m+1 x n+1)
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (oldLines[i].equals(newLines[j])) {
                    dp[i][j] = 1 + dp[i + 1][j + 1];
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        // Trace back to build diff entries
        List<DiffEntry> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < m || j < n) {
            if (i < m && j < n && oldLines[i].equals(newLines[j])) {
                result.add(new DiffEntry(DiffEntry.Type.UNCHANGED, oldLines[i], i + 1, j + 1));
                i++; j++;
            } else if (j < n && (i >= m || dp[i][j + 1] >= dp[i + 1][j])) {
                result.add(new DiffEntry(DiffEntry.Type.ADDED, newLines[j], -1, j + 1));
                j++;
            } else {
                result.add(new DiffEntry(DiffEntry.Type.REMOVED, oldLines[i], i + 1, -1));
                i++;
            }
        }
        return result;
    }

    private static List<DiffHunk> groupIntoHunks(List<DiffEntry> entries, int ctx) {
        List<DiffHunk> hunks = new ArrayList<>();
        int size = entries.size();

        // Find indices of changed lines
        boolean[] changed = new boolean[size];
        for (int k = 0; k < size; k++) {
            if (entries.get(k).type != DiffEntry.Type.UNCHANGED) changed[k] = true;
        }

        int k = 0;
        while (k < size) {
            if (!changed[k]) { k++; continue; }

            // Hunk start: go back 'ctx' lines for context
            int start = Math.max(0, k - ctx);
            // Hunk end: advance until no more changes within ctx
            int end = k;
            while (end < size) {
                if (changed[end]) {
                    end = Math.min(size - 1, end + ctx);
                    int next = end + 1;
                    while (next < size && next <= end + ctx && changed[next]) {
                        end = Math.min(size - 1, next + ctx);
                        next++;
                    }
                    break;
                }
                end++;
            }
            end = Math.min(size - 1, end);

            List<DiffEntry> hunkEntries = new ArrayList<>(entries.subList(start, end + 1));

            // Compute hunk header @@ -old +new @@
            int oldStart = -1, newStart = -1, oldCount = 0, newCount = 0;
            for (DiffEntry e : hunkEntries) {
                if (e.oldLine > 0) { if (oldStart < 0) oldStart = e.oldLine; oldCount++; }
                if (e.newLine > 0) { if (newStart < 0) newStart = e.newLine; newCount++; }
            }
            String header = "@@ -" + Math.max(1, oldStart) + "," + oldCount
                    + " +" + Math.max(1, newStart) + "," + newCount + " @@";

            hunks.add(new DiffHunk(header, hunkEntries));
            k = end + 1;
        }
        return hunks;
    }

    private static String[] splitLines(String text) {
        if (text == null || text.isEmpty()) return new String[0];
        return text.split("\n", -1);
    }
}
