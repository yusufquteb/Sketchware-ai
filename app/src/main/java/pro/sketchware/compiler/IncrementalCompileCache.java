package pro.sketchware.compiler;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IncrementalCompileCache {

    private static final String TAG = "IncrementalCompileCache";
    private static final String CACHE_FILENAME = ".incremental_cache";

    private final File cacheFile;
    private CacheState cacheState;

    public IncrementalCompileCache(String projectId) {
        this(projectId, null);
    }

    public IncrementalCompileCache(String projectId, String namespace) {
        File cacheDir = new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + projectId);
        String filename = CACHE_FILENAME + ((namespace == null || namespace.trim().isEmpty()) ? "" : "_" + namespace.trim());
        cacheFile = new File(cacheDir, filename);
        cacheState = load();
    }

    public boolean hasChanges(String... directoriesOrFiles) {
        return getChangeSet(directoriesOrFiles).hasChanges();
    }

    public boolean hasChanges(String environmentFingerprint, String... directoriesOrFiles) {
        return getChangeSet(environmentFingerprint, directoriesOrFiles).hasChanges();
    }

    public ChangeSet getChangeSet(String... directoriesOrFiles) {
        return getChangeSet("", directoriesOrFiles);
    }

    public ChangeSet getChangeSet(String environmentFingerprint, String... directoriesOrFiles) {
        Map<String, SourceFingerprint> current = snapshot(directoriesOrFiles);
        ArrayList<String> changedOrAdded = new ArrayList<>();
        ArrayList<String> removed = new ArrayList<>();

        for (Map.Entry<String, SourceFingerprint> entry : current.entrySet()) {
            SourceFingerprint previous = cacheState.fingerprints.get(entry.getKey());
            if (!entry.getValue().equals(previous)) {
                changedOrAdded.add(entry.getKey());
            }
        }

        for (String previousPath : cacheState.fingerprints.keySet()) {
            if (!current.containsKey(previousPath)) {
                removed.add(previousPath);
            }
        }

        Collections.sort(changedOrAdded);
        Collections.sort(removed);

        boolean environmentChanged = !safe(cacheState.environmentFingerprint).equals(safe(environmentFingerprint));
        return new ChangeSet(current, changedOrAdded, removed, environmentChanged, safe(environmentFingerprint));
    }

    /**
     * Same as {@link #getChangeSet(String, String...)} but with a distinct name
     * to avoid Kotlin overload resolution ambiguity with {@link #getChangeSet(String...)}.
     */
    public ChangeSet getChangeSetWithEnvironment(String environmentFingerprint, String... directoriesOrFiles) {
        return getChangeSet(environmentFingerprint, directoriesOrFiles);
    }

    public void save(String... directoriesOrFiles) {
        save(getChangeSet(directoriesOrFiles));
    }

    public void save(String environmentFingerprint, String... directoriesOrFiles) {
        save(getChangeSet(environmentFingerprint, directoriesOrFiles));
    }

    public void save(ChangeSet changeSet) {
        cacheState = new CacheState(new LinkedHashMap<>(changeSet.getCurrentSnapshot()), changeSet.getEnvironmentFingerprint());
        persist();
    }

    private void persist() {
        try {
            File parent = cacheFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(cacheFile))) {
                output.writeObject(cacheState);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to save incremental cache", e);
        }
    }

    private CacheState load() {
        if (!cacheFile.exists()) {
            return new CacheState(new LinkedHashMap<>(), "");
        }
        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(cacheFile))) {
            Object value = input.readObject();
            if (value instanceof CacheState state) {
                return state.sanitize();
            }
            if (value instanceof Map<?, ?> persistedMap) {
                LinkedHashMap<String, SourceFingerprint> loaded = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : persistedMap.entrySet()) {
                    if (!(entry.getKey() instanceof String path)) {
                        continue;
                    }
                    Object rawFingerprint = entry.getValue();
                    if (rawFingerprint instanceof SourceFingerprint sourceFingerprint) {
                        loaded.put(path, sourceFingerprint);
                    } else if (rawFingerprint instanceof Long lastModified) {
                        loaded.put(path, SourceFingerprint.legacy(lastModified));
                    } else if (rawFingerprint instanceof Map<?, ?> rawMap) {
                        loaded.put(path, SourceFingerprint.fromMap(rawMap));
                    }
                }
                return new CacheState(loaded, "");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load incremental cache", e);
        }
        return new CacheState(new LinkedHashMap<>(), "");
    }

    private Map<String, SourceFingerprint> snapshot(String... directoriesOrFiles) {
        Map<String, SourceFingerprint> current = new LinkedHashMap<>();
        if (directoriesOrFiles == null) {
            return current;
        }
        for (String path : directoriesOrFiles) {
            if (path == null || path.isEmpty()) {
                continue;
            }
            collect(new File(path), current);
        }
        return current;
    }

    private void collect(File file, Map<String, SourceFingerprint> current) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return;
            }
            List<File> sortedChildren = new ArrayList<>();
            Collections.addAll(sortedChildren, children);
            sortedChildren.sort((left, right) -> left.getAbsolutePath().compareTo(right.getAbsolutePath()));
            for (File child : sortedChildren) {
                collect(child, current);
            }
        } else if (isSourceFile(file)) {
            current.put(file.getAbsolutePath(), SourceFingerprint.create(file));
        }
    }

    private boolean isSourceFile(File file) {
        String name = file.getName();
        return name.endsWith(".java") || name.endsWith(".kt");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class CacheState implements Serializable {
        private static final long serialVersionUID = 2L;

        private final Map<String, SourceFingerprint> fingerprints;
        private final String environmentFingerprint;

        private CacheState(Map<String, SourceFingerprint> fingerprints, String environmentFingerprint) {
            this.fingerprints = fingerprints == null ? new LinkedHashMap<>() : fingerprints;
            this.environmentFingerprint = safe(environmentFingerprint);
        }

        private CacheState sanitize() {
            LinkedHashMap<String, SourceFingerprint> sanitized = new LinkedHashMap<>();
            for (Map.Entry<String, SourceFingerprint> entry : fingerprints.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    sanitized.put(entry.getKey(), entry.getValue());
                }
            }
            return new CacheState(sanitized, environmentFingerprint);
        }
    }

    private static final class SourceFingerprint implements Serializable {
        private static final long serialVersionUID = 1L;

        private final long lastModified;
        private final long size;
        private final String sha256;

        private SourceFingerprint(long lastModified, long size, String sha256) {
            this.lastModified = lastModified;
            this.size = size;
            this.sha256 = sha256 == null ? "" : sha256;
        }

        static SourceFingerprint create(File file) {
            return new SourceFingerprint(file.lastModified(), file.length(), sha256(file));
        }

        static SourceFingerprint legacy(long lastModified) {
            return new SourceFingerprint(lastModified, -1L, "");
        }

        static SourceFingerprint fromMap(Map<?, ?> rawMap) {
            long lastModified = rawMap.get("lastModified") instanceof Number number ? number.longValue() : 0L;
            long size = rawMap.get("size") instanceof Number number ? number.longValue() : -1L;
            String sha256 = rawMap.get("sha256") instanceof String hash ? hash : "";
            return new SourceFingerprint(lastModified, size, sha256);
        }

        private static String sha256(File file) {
            try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }

                StringBuilder hex = new StringBuilder();
                for (byte value : digest.digest()) {
                    hex.append(String.format("%02x", value));
                }
                return hex.toString();
            } catch (Exception e) {
                Log.w(TAG, "Failed to hash " + file.getAbsolutePath() + ", falling back to metadata only", e);
                return "";
            }
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SourceFingerprint fingerprint)) {
                return false;
            }
            if (size != fingerprint.size) {
                return false;
            }
            if (!sha256.isEmpty() || !fingerprint.sha256.isEmpty()) {
                return sha256.equals(fingerprint.sha256);
            }
            return lastModified == fingerprint.lastModified;
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(lastModified);
            result = 31 * result + Long.hashCode(size);
            result = 31 * result + sha256.hashCode();
            return result;
        }
    }

    public static final class ChangeSet {
        private final Map<String, SourceFingerprint> currentSnapshot;
        private final List<String> changedOrAddedFiles;
        private final List<String> removedFiles;
        private final boolean environmentChanged;
        private final String environmentFingerprint;

        private ChangeSet(Map<String, SourceFingerprint> currentSnapshot,
                          List<String> changedOrAddedFiles,
                          List<String> removedFiles,
                          boolean environmentChanged,
                          String environmentFingerprint) {
            this.currentSnapshot = currentSnapshot;
            this.changedOrAddedFiles = changedOrAddedFiles;
            this.removedFiles = removedFiles;
            this.environmentChanged = environmentChanged;
            this.environmentFingerprint = environmentFingerprint == null ? "" : environmentFingerprint;
        }

        public boolean hasChanges() {
            return environmentChanged || !changedOrAddedFiles.isEmpty() || !removedFiles.isEmpty();
        }

        public Map<String, SourceFingerprint> getCurrentSnapshot() {
            return currentSnapshot;
        }

        public List<String> getChangedOrAddedFiles() {
            return changedOrAddedFiles;
        }

        public List<String> getRemovedFiles() {
            return removedFiles;
        }

        public boolean isEnvironmentChanged() {
            return environmentChanged;
        }

        public String getEnvironmentFingerprint() {
            return environmentFingerprint;
        }

        public List<String> getChangedOrAddedFilesWithExtension(String extension) {
            return filterByExtension(changedOrAddedFiles, extension);
        }

        public List<String> getRemovedFilesWithExtension(String extension) {
            return filterByExtension(removedFiles, extension);
        }

        private List<String> filterByExtension(List<String> files, String extension) {
            if (extension == null || extension.isEmpty()) {
                return new ArrayList<>(files);
            }
            ArrayList<String> filtered = new ArrayList<>();
            for (String file : files) {
                if (file.endsWith(extension)) {
                    filtered.add(file);
                }
            }
            return filtered;
        }
    }
}
