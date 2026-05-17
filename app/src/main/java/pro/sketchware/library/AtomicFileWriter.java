package pro.sketchware.library;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Proposal 19 — Atomic file writes.
 *
 * Writes content to a .tmp file first, then atomically renames it
 * to the target. If the process crashes mid-write, the original file
 * is untouched. If rename fails (cross-device), falls back to copy.
 *
 * All file writes in LibraryProjectLinker, LocalLibraryManager, and
 * LibraryConflictChecker should go through this class.
 */
public final class AtomicFileWriter {

    private static final String TAG = "AtomicFileWriter";

    private AtomicFileWriter() {}

    /**
     * Writes {@code content} to {@code target} atomically.
     * Creates parent directories if needed.
     *
     * @throws IOException if the write or rename fails
     */
    public static void write(File target, String content) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        File tmp = new File(parent, target.getName() + ".tmp_" + Thread.currentThread().getId());
        try {
            // Write to temp file
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
                fos.getFD().sync();   // flush to OS buffer + physical disk
            }

            // Atomic rename
            if (!tmp.renameTo(target)) {
                // renameTo can fail cross-filesystem — fallback to copy-then-delete
                copyAndReplace(tmp, target);
            }
        } catch (IOException e) {
            tmp.delete();
            throw e;
        }
    }

    /** Read helper — returns empty string on missing file, never null */
    public static String read(File f) {
        if (f == null || !f.exists()) return "";
        try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int read = fis.read(buf);
            return new String(buf, 0, Math.max(0, read), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.w(TAG, "Failed to read " + f.getAbsolutePath(), e);
            return "";
        }
    }

    /** Read helper — validates JSON array before returning; returns "[]" if corrupt */
    public static String readJsonArray(File f) {
        String content = read(f).trim();
        if (content.isEmpty()) return "[]";
        // Quick structural check: must start with '[' and end with ']'
        if (!content.startsWith("[") || !content.endsWith("]")) {
            Log.w(TAG, "Corrupt JSON array in " + f.getName() + " — resetting to []");
            // Attempt recovery: write clean empty array atomically
            try { write(f, "[]"); } catch (IOException ignored) {}
            return "[]";
        }
        return content;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static void copyAndReplace(File src, File dst) throws IOException {
        File backup = new File(dst.getParent(), dst.getName() + ".bak");
        if (dst.exists()) dst.renameTo(backup);  // save old version
        try (java.io.FileInputStream in  = new java.io.FileInputStream(src);
             FileOutputStream        out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.getFD().sync();
        }
        src.delete();
        backup.delete();  // clean backup only after successful copy
    }
}
