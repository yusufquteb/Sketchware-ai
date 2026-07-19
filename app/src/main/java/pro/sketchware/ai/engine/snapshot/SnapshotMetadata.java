package pro.sketchware.ai.engine.snapshot;

/**
 * Metadata record for a project snapshot.
 */
public final class SnapshotMetadata {

    public final String snapshotId;   // unique ID: snap_{scId}_{timestamp}
    public final String scId;
    public final String label;        // human-readable description
    public final long   createdAt;    // System.currentTimeMillis()
    public final long   sizeBytes;    // total copied bytes
    public final String triggerTool;  // which tool triggered this snapshot

    public SnapshotMetadata(String snapshotId, String scId, String label,
                            long createdAt, long sizeBytes, String triggerTool) {
        this.snapshotId  = snapshotId;
        this.scId        = scId;
        this.label       = label;
        this.createdAt   = createdAt;
        this.sizeBytes   = sizeBytes;
        this.triggerTool = triggerTool;
    }

    public String getFormattedSize() {
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return (sizeBytes / 1024) + " KB";
        return String.format(java.util.Locale.ROOT, "%.1f MB", sizeBytes / (1024.0 * 1024));
    }

    @Override
    public String toString() {
        return "Snapshot{id=" + snapshotId + ", scId=" + scId
                + ", size=" + getFormattedSize() + ", label='" + label + "'}";
    }
}
