package pro.sketchware.compiler;

import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FileUtil;

public class SourceOutputTracker {

    private static final String TAG = "SourceOutputTracker";
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][\\w.]*)");
    private static final Pattern TYPE_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:public\\s+|protected\\s+|private\\s+|internal\\s+|abstract\\s+|final\\s+|sealed\\s+|data\\s+|open\\s+|enum\\s+|annotation\\s+|value\\s+|static\\s+)*(?:class|interface|enum|object|record|annotation\\s+class)\\s+([A-Za-z_][A-Za-z0-9_]*)"
    );

    private final File storeFile;
    private final Gson gson = new Gson();
    private Map<String, List<String>> mappings;

    public SourceOutputTracker(String projectId, String namespace) {
        File cacheDir = new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + projectId);
        this.storeFile = new File(cacheDir, ".source_output_map_" + sanitize(namespace) + ".json");
        this.mappings = load();
    }

    public synchronized void deleteOutputsForSources(Collection<String> sourcePaths, File outputDirectory) {
        if (sourcePaths == null || outputDirectory == null) {
            return;
        }
        for (String sourcePath : sourcePaths) {
            for (String relativePath : getKnownOrDerivedOutputs(sourcePath, outputDirectory)) {
                File candidate = new File(outputDirectory, relativePath.replace('/', File.separatorChar));
                if (candidate.exists() && !candidate.delete()) {
                    Log.w(TAG, "Failed to delete stale class output " + candidate.getAbsolutePath());
                }
            }
        }
    }

    public synchronized void refreshOutputsForSources(Collection<String> sourcePaths, File outputDirectory) {
        if (sourcePaths == null || outputDirectory == null) {
            return;
        }
        boolean changed = false;
        for (String sourcePath : sourcePaths) {
            File sourceFile = new File(sourcePath);
            if (!sourceFile.exists()) {
                if (mappings.remove(sourcePath) != null) {
                    changed = true;
                }
                continue;
            }
            List<String> resolvedOutputs = resolveOutputsForSource(sourceFile, outputDirectory);
            if (resolvedOutputs.isEmpty()) {
                if (mappings.remove(sourcePath) != null) {
                    changed = true;
                }
            } else {
                ArrayList<String> sorted = new ArrayList<>(resolvedOutputs);
                Collections.sort(sorted);
                List<String> previous = mappings.put(sourcePath, sorted);
                if (previous == null || !previous.equals(sorted)) {
                    changed = true;
                }
            }
        }
        if (changed) {
            save();
        }
    }

    public synchronized void removeSources(Collection<String> sourcePaths) {
        if (sourcePaths == null) {
            return;
        }
        boolean changed = false;
        for (String sourcePath : sourcePaths) {
            if (mappings.remove(sourcePath) != null) {
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private Collection<String> getKnownOrDerivedOutputs(String sourcePath, File outputDirectory) {
        LinkedHashSet<String> outputs = new LinkedHashSet<>();
        List<String> persisted = mappings.get(sourcePath);
        if (persisted != null) {
            outputs.addAll(persisted);
        }
        File sourceFile = sourcePath == null ? null : new File(sourcePath);
        if (sourceFile != null && sourceFile.exists()) {
            outputs.addAll(resolveOutputsForSource(sourceFile, outputDirectory));
        }
        return outputs;
    }

    private List<String> resolveOutputsForSource(File sourceFile, File outputDirectory) {
        if (sourceFile == null || outputDirectory == null || !outputDirectory.exists()) {
            return Collections.emptyList();
        }

        SourceMetadata metadata = SourceMetadata.fromSource(sourceFile);
        File packageDirectory = metadata.packageName.isEmpty()
                ? outputDirectory
                : new File(outputDirectory, metadata.packageName.replace('.', File.separatorChar));

        if (!packageDirectory.exists() || !packageDirectory.isDirectory()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> matches = new LinkedHashSet<>();
        File[] files = packageDirectory.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }

        String packagePrefix = metadata.packageName.isEmpty() ? "" : metadata.packageName.replace('.', '/') + "/";
        for (File candidate : files) {
            if (!candidate.isFile() || !candidate.getName().endsWith(".class")) {
                continue;
            }
            String name = candidate.getName();
            for (String prefix : metadata.outputPrefixes) {
                if (name.equals(prefix + ".class") || name.startsWith(prefix + "$")) {
                    matches.add(packagePrefix + name);
                    break;
                }
            }
        }

        return new ArrayList<>(matches);
    }

    private Map<String, List<String>> load() {
        if (!storeFile.exists()) {
            return new LinkedHashMap<>();
        }
        try (FileReader reader = new FileReader(storeFile)) {
            Type type = new TypeToken<LinkedHashMap<String, ArrayList<String>>>() {}.getType();
            Map<String, List<String>> loaded = gson.fromJson(reader, type);
            return loaded == null ? new LinkedHashMap<>() : loaded;
        } catch (Exception e) {
            Log.w(TAG, "Failed to load source output mapping", e);
            return new LinkedHashMap<>();
        }
    }

    private void save() {
        try {
            File parent = storeFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(storeFile, false)) {
                gson.toJson(mappings, writer);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to save source output mapping", e);
        }
    }

    private static String sanitize(String value) {
        String safe = value == null ? "default" : value.trim().toLowerCase(Locale.ENGLISH);
        if (safe.isEmpty()) {
            return "default";
        }
        return safe.replaceAll("[^a-z0-9._-]", "_");
    }

    private static final class SourceMetadata {
        private final String packageName;
        private final Set<String> outputPrefixes;

        private SourceMetadata(String packageName, Set<String> outputPrefixes) {
            this.packageName = packageName == null ? "" : packageName;
            this.outputPrefixes = outputPrefixes;
        }

        private static SourceMetadata fromSource(File file) {
            String content = file.exists() ? FileUtil.readFile(file.getAbsolutePath()) : "";
            String packageName = "";
            Matcher packageMatcher = PACKAGE_PATTERN.matcher(content);
            if (packageMatcher.find()) {
                packageName = packageMatcher.group(1);
            }

            LinkedHashSet<String> prefixes = new LinkedHashSet<>();
            String basename = FileUtil.getFileNameNoExtension(file.getName());
            if (!basename.isEmpty()) {
                prefixes.add(basename);
                if (file.getName().endsWith(".kt")) {
                    prefixes.add(basename + "Kt");
                }
            }

            Matcher typeMatcher = TYPE_PATTERN.matcher(content);
            while (typeMatcher.find()) {
                String name = typeMatcher.group(1);
                if (name != null && !name.isEmpty()) {
                    prefixes.add(name);
                }
            }
            return new SourceMetadata(packageName, prefixes);
        }
    }
}
