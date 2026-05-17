package pro.sketchware.util;

import java.util.HashMap;
import java.util.Map;

/**
 * FileChangeTracker — tracks before/after content of project files modified by the AI agent.
 *
 * When the AI edits a file, the old and new content are stored here so the agent
 * can generate diffs, review what changed, and roll back if needed.
 */
public class FileChangeTracker {

    private static final Map<String, FileChange> changes = new HashMap<>();

    /** Represents a single file modification. */
    public static class FileChange {
        public final String filePath;
        public final String beforeContent;
        public final String afterContent;
        public final long   timestamp;

        public FileChange(String filePath, String beforeContent, String afterContent) {
            this.filePath      = filePath;
            this.beforeContent = beforeContent;
            this.afterContent  = afterContent;
            this.timestamp     = System.currentTimeMillis();
        }

        /** Generates a simple unified-style diff between before and after content. */
        public String generateDiff() {
            if (beforeContent == null || afterContent == null) return "No diff available";
            if (beforeContent.equals(afterContent)) return "No changes";

            String[] before = beforeContent.split("\n");
            String[] after  = afterContent.split("\n");

            StringBuilder diff = new StringBuilder();
            diff.append("--- ").append(filePath).append(" (before)\n");
            diff.append("+++ ").append(filePath).append(" (after)\n");

            int max = Math.max(before.length, after.length);
            for (int i = 0; i < max; i++) {
                if (i < before.length && i < after.length) {
                    if (!before[i].equals(after[i])) {
                        diff.append("- ").append(before[i]).append("\n");
                        diff.append("+ ").append(after[i]).append("\n");
                    }
                } else if (i < before.length) {
                    diff.append("- ").append(before[i]).append("\n");
                } else {
                    diff.append("+ ").append(after[i]).append("\n");
                }
            }

            return diff.toString();
        }
    }

    /** Records a file modification. Call with old and new content after every AI edit. */
    public static void trackChange(String filePath, String beforeContent, String afterContent) {
        changes.put(filePath, new FileChange(filePath, beforeContent, afterContent));
    }

    /** Returns the most recent change for a specific file path. */
    public static FileChange getLastChange(String filePath) {
        return changes.get(filePath);
    }

    /** Returns all tracked changes in this session. */
    public static Map<String, FileChange> getAllRecentChanges() {
        return new HashMap<>(changes);
    }

    /** Clears all tracked changes (call at session start or after a successful build). */
    public static void clearChanges() {
        changes.clear();
    }

    /** Returns a Markdown summary of all recent changes with diffs. */
    public static String generateChangesSummary() {
        if (changes.isEmpty()) return "No recent file changes.";

        StringBuilder sb = new StringBuilder();
        sb.append("**Recent File Changes:**\n\n");

        for (FileChange change : changes.values()) {
            sb.append("**File:** ").append(change.filePath).append("\n");
            sb.append("**Time:** ").append(new java.util.Date(change.timestamp)).append("\n");
            sb.append("**Diff:**\n```\n");
            sb.append(change.generateDiff());
            sb.append("\n```\n\n");
        }

        return sb.toString();
    }
}
