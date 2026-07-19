package pro.sketchware.ai.offline;

/**
 * Lifecycle state of a single {@link LocalModelCatalog} entry on this device.
 * Tracked per model ID by {@link LocalModelManager}.
 */
public enum LocalModelState {
    /** No file on disk yet — never downloaded, or previously deleted. */
    NOT_DOWNLOADED,
    /** {@link LocalModelDownloader} has an active or paused-but-resumable download. */
    DOWNLOADING,
    /** File fully downloaded and its size matches the expected size — ready to load. */
    READY,
    /** Last download or verification attempt failed. See {@link LocalModelManager#getLastError}. */
    ERROR
}
