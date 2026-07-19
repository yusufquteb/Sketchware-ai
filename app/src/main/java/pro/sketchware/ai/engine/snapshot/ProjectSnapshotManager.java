package pro.sketchware.ai.engine.snapshot;

import android.util.Log;
import pro.sketchware.ai.utils.AiLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import pro.sketchware.ai.tools.ToolContext;

/**
 * Manages project snapshots for safe rollback before MEDIUM/CRITICAL tool execution.
 *
 * Strategy:
 *   - Snapshot = full copy of .sketchware/data/{sc_id}/ directory
 *   - Stored at .sketchware/bak/{sc_id}/snap_{timestamp}/
 *   - Maximum 5 snapshots per project (oldest deleted automatically)
 *   - Restore = overwrite data dir with snapshot content
 */
public final class ProjectSnapshotManager {

    private static final String TAG           = "SnapshotManager";
    private static final int    MAX_SNAPSHOTS = 5;

    private final ToolContext context;

    public ProjectSnapshotManager(ToolContext context) {
        this.context = context;
    }

    /**
     * Creates a snapshot of the project data directory before a risky operation.
     *
     * @param scId        project ID
     * @param label       description (e.g. "before modify_xml activity_main")
     * @param triggerTool name of the tool about to run
     * @return snapshot metadata, or null if snapshot creation failed
     */
    public SnapshotMetadata createSnapshot(String scId, String label, String triggerTool) {
        File sourceDir = context.getProjectDataDir(scId);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            Log.e(TAG, "Source dir does not exist: " + sourceDir);
            return null;
        }

        String snapshotId = "snap_" + scId + "_" + System.currentTimeMillis();
        File snapshotDir  = new File(context.getProjectBackupDir(scId), snapshotId);
        if (!snapshotDir.mkdirs()) {
            Log.e(TAG, "Cannot create snapshot directory: " + snapshotDir);
            return null;
        }

        try {
            long size = copyDirectory(sourceDir, snapshotDir);
            SnapshotMetadata meta = new SnapshotMetadata(
                    snapshotId, scId, label, System.currentTimeMillis(), size, triggerTool);
            pruneOldSnapshots(scId);
            Log.i(TAG, "Snapshot created: " + meta);
            return meta;
        } catch (IOException e) {
            Log.e(TAG, "Snapshot failed: " + e.getMessage());
            deleteDirectory(snapshotDir);
            return null;
        }
    }

    /**
     * Restores a project from a snapshot.
     *
     * @param scId       project ID
     * @param snapshotId the snapshot ID to restore
     * @return true if restore succeeded
     */
    public boolean restoreSnapshot(String scId, String snapshotId) {
        File snapshotDir = new File(context.getProjectBackupDir(scId), snapshotId);
        if (!snapshotDir.exists()) {
            Log.e(TAG, "Snapshot not found: " + snapshotId);
            return false;
        }

        File targetDir = context.getProjectDataDir(scId);
        // Clear target first
        if (targetDir.exists()) {
            deleteDirectoryContents(targetDir);
        } else {
            targetDir.mkdirs();
        }

        try {
            copyDirectory(snapshotDir, targetDir);
            Log.i(TAG, "Restored snapshot " + snapshotId + " → " + targetDir);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Restore failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lists all snapshots for a project, sorted newest first.
     */
    public List<SnapshotMetadata> listSnapshots(String scId) {
        File bakDir = context.getProjectBackupDir(scId);
        if (!bakDir.exists()) return Collections.emptyList();

        File[] dirs = bakDir.listFiles(f -> f.isDirectory() && f.getName().startsWith("snap_"));
        if (dirs == null || dirs.length == 0) return Collections.emptyList();

        List<File> sorted = new ArrayList<>(Arrays.asList(dirs));
        Collections.sort(sorted, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        List<SnapshotMetadata> result = new ArrayList<>();
        for (File dir : sorted) {
            long size = dirSize(dir);
            result.add(new SnapshotMetadata(
                    dir.getName(), scId,
                    "(snapshot)", dir.lastModified(), size, "unknown"));
        }
        return result;
    }

    /**
     * Deletes a specific snapshot.
     */
    public boolean deleteSnapshot(String scId, String snapshotId) {
        File snapshotDir = new File(context.getProjectBackupDir(scId), snapshotId);
        return deleteDirectory(snapshotDir);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void pruneOldSnapshots(String scId) {
        File bakDir = context.getProjectBackupDir(scId);
        if (!bakDir.exists()) return;
        File[] dirs = bakDir.listFiles(f -> f.isDirectory() && f.getName().startsWith("snap_"));
        if (dirs == null || dirs.length <= MAX_SNAPSHOTS) return;

        List<File> sorted = new ArrayList<>(Arrays.asList(dirs));
        Collections.sort(sorted, Comparator.comparingLong(File::lastModified));
        // Delete oldest entries beyond the limit
        for (int i = 0; i < sorted.size() - MAX_SNAPSHOTS; i++) {
            deleteDirectory(sorted.get(i));
            AiLog.d(TAG, "Pruned old snapshot: " + sorted.get(i).getName());
        }
    }

    private long copyDirectory(File src, File dst) throws IOException {
        long totalBytes = 0;
        File[] files = src.listFiles();
        if (files == null) return 0;
        for (File file : files) {
            File dstFile = new File(dst, file.getName());
            if (file.isDirectory()) {
                dstFile.mkdirs();
                totalBytes += copyDirectory(file, dstFile);
            } else {
                totalBytes += copyFile(file, dstFile);
            }
        }
        return totalBytes;
    }

    private long copyFile(File src, File dst) throws IOException {
        dst.getParentFile().mkdirs();
        try (FileInputStream in  = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
                total += read;
            }
            return total;
        }
    }

    private void deleteDirectoryContents(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) deleteDirectory(f);
            else f.delete();
        }
    }

    private boolean deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return true;
        deleteDirectoryContents(dir);
        return dir.delete();
    }

    private long dirSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            size += f.isDirectory() ? dirSize(f) : f.length();
        }
        return size;
    }
}
