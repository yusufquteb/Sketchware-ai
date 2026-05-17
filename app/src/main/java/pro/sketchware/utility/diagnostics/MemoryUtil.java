package pro.sketchware.utility.diagnostics;

public final class MemoryUtil {
    private MemoryUtil() {}

    public static Snapshot snapshot() {
        Runtime runtime = Runtime.getRuntime();
        return new Snapshot(runtime.maxMemory(), runtime.totalMemory(), runtime.freeMemory());
    }

    public static final class Snapshot {
        public final long maxBytes;
        public final long totalBytes;
        public final long freeBytes;
        public final long usedBytes;

        Snapshot(long maxBytes, long totalBytes, long freeBytes) {
            this.maxBytes = maxBytes;
            this.totalBytes = totalBytes;
            this.freeBytes = freeBytes;
            this.usedBytes = totalBytes - freeBytes;
        }

        public double usedRatio() { return maxBytes <= 0 ? 0 : (double) usedBytes / (double) maxBytes; }
        @Override public String toString() { return "used=" + usedBytes + ", free=" + freeBytes + ", total=" + totalBytes + ", max=" + maxBytes; }
    }
}
