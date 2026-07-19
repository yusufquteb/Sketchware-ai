package pro.sketchware.compiler;

import android.util.Log;

import pro.sketchware.utility.FileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaCompileGraph {

    private static final String TAG = "JavaCompileGraph";
    private static final int CACHE_VERSION = 1;

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][\\w.]*)");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+(static\\s+)?([A-Za-z_][\\w.]*(?:\\.\\*)?)\\s*;");
    private static final Pattern TYPE_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:public\\s+|protected\\s+|private\\s+|abstract\\s+|final\\s+|sealed\\s+|non-sealed\\s+|static\\s+)*(?:class|@interface|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)"
    );
    private static final Pattern SIMPLE_TYPE_PATTERN = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\b");
    private static final Pattern FQCN_PATTERN = Pattern.compile("\\b([a-z_][\\w]*(?:\\.[a-z_][\\w]*)*\\.[A-Z][A-Za-z0-9_]*)\\b");

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, String> declaredTypeToSource = new HashMap<>();
    private final Map<String, Set<String>> dependencies = new LinkedHashMap<>();
    private final Map<String, Set<String>> dependents = new LinkedHashMap<>();

    public JavaCompileGraph(Collection<String> sourceFiles) {
        if (sourceFiles == null) {
            return;
        }
        ArrayList<String> sorted = new ArrayList<>(sourceFiles);
        Collections.sort(sorted);
        for (String sourceFile : sorted) {
            Node node = parse(sourceFile);
            if (node != null) {
                nodes.put(sourceFile, node);
                for (String fqcn : node.declaredFqcns) {
                    declaredTypeToSource.putIfAbsent(fqcn, sourceFile);
                }
            }
        }
        buildEdges();
    }

    private JavaCompileGraph(Map<String, Node> cachedNodes) {
        nodes.putAll(cachedNodes);
        rebuildDerivedMaps();
    }

    /**
     * Loads a previously saved graph and applies incremental changes, or builds from scratch
     * if the cache is missing, stale, or the source file set has changed significantly.
     *
     * @param cacheFile         where the serialized graph is stored
     * @param currentSources    the full set of source files for this build
     * @param changedOrAdded    files that are new or have been modified since the last build
     * @param removed           files that no longer exist
     */
    public static JavaCompileGraph loadOrBuild(
            File cacheFile,
            Collection<String> currentSources,
            List<String> changedOrAdded,
            List<String> removed) {

        JavaCompileGraph cached = tryLoad(cacheFile, currentSources, removed);
        if (cached != null) {
            if (!changedOrAdded.isEmpty() || !removed.isEmpty()) {
                cached.applyChanges(changedOrAdded, removed);
            }
            return cached;
        }

        JavaCompileGraph fresh = new JavaCompileGraph(currentSources);
        fresh.save(cacheFile);
        return fresh;
    }

    /** Persists the current graph nodes to disk for reuse in the next build. */
    public void save(File cacheFile) {
        if (cacheFile == null) {
            return;
        }
        try {
            File parent = cacheFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(cacheFile))) {
                out.writeInt(CACHE_VERSION);
                out.writeObject(new LinkedHashMap<>(nodes));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to save graph cache", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static JavaCompileGraph tryLoad(
            File cacheFile,
            Collection<String> currentSources,
            List<String> removed) {
        if (cacheFile == null || !cacheFile.exists()) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(cacheFile))) {
            int version = in.readInt();
            if (version != CACHE_VERSION) {
                return null;
            }
            Map<String, Node> cachedNodes = (Map<String, Node>) in.readObject();
            if (cachedNodes == null) {
                return null;
            }

            // Invalidate if the source file sets differ too much (e.g. a full clean build).
            // Allow for the expected removals; anything beyond that means the project changed
            // substantially and a fresh parse is safer.
            Set<String> currentSet = new LinkedHashSet<>(currentSources);
            Set<String> cachedSet = cachedNodes.keySet();
            long unexpectedDiff = cachedSet.stream()
                    .filter(p -> !currentSet.contains(p) && (removed == null || !removed.contains(p)))
                    .count();
            if (unexpectedDiff > 0) {
                Log.d(TAG, "Graph cache invalid: " + unexpectedDiff + " unexpected stale path(s); rebuilding");
                return null;
            }

            return new JavaCompileGraph(cachedNodes);
        } catch (Exception e) {
            Log.w(TAG, "Failed to load graph cache; will rebuild", e);
            return null;
        }
    }

    private void applyChanges(List<String> changedOrAdded, List<String> removed) {
        for (String path : removed) {
            nodes.remove(path);
        }
        for (String path : changedOrAdded) {
            Node node = parse(path);
            if (node != null) {
                nodes.put(path, node);
            } else {
                nodes.remove(path);
            }
        }
        rebuildDerivedMaps();
    }

    private void rebuildDerivedMaps() {
        declaredTypeToSource.clear();
        dependencies.clear();
        dependents.clear();
        for (Map.Entry<String, Node> entry : nodes.entrySet()) {
            for (String fqcn : entry.getValue().declaredFqcns) {
                declaredTypeToSource.putIfAbsent(fqcn, entry.getKey());
            }
        }
        buildEdges();
    }

    public List<String> collectImpactedSources(Collection<String> changedSources) {
        LinkedHashSet<String> impacted = new LinkedHashSet<>();
        if (changedSources == null) {
            return new ArrayList<>();
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String source : changedSources) {
            if (source != null && nodes.containsKey(source) && impacted.add(source)) {
                queue.add(source);
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (String dependent : dependents.getOrDefault(current, Collections.emptySet())) {
                if (impacted.add(dependent)) {
                    queue.addLast(dependent);
                }
            }
        }
        return new ArrayList<>(impacted);
    }

    public List<List<String>> partitionIndependentSourceGroups(Collection<String> sourceFiles) {
        LinkedHashSet<String> remaining = new LinkedHashSet<>();
        if (sourceFiles != null) {
            for (String source : sourceFiles) {
                if (source != null && nodes.containsKey(source)) {
                    remaining.add(source);
                }
            }
        }

        ArrayList<List<String>> groups = new ArrayList<>();
        while (!remaining.isEmpty()) {
            String seed = remaining.iterator().next();
            ArrayDeque<String> queue = new ArrayDeque<>();
            LinkedHashSet<String> component = new LinkedHashSet<>();
            queue.add(seed);
            remaining.remove(seed);
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                component.add(current);
                for (String next : getUndirectedNeighbors(current)) {
                    if (remaining.remove(next)) {
                        queue.addLast(next);
                    }
                }
            }
            ArrayList<String> group = new ArrayList<>(component);
            Collections.sort(group);
            groups.add(group);
        }

        groups.sort((left, right) -> Integer.compare(right.size(), left.size()));
        return groups;
    }

    private Set<String> getUndirectedNeighbors(String source) {
        LinkedHashSet<String> neighbors = new LinkedHashSet<>();
        neighbors.addAll(dependencies.getOrDefault(source, Collections.emptySet()));
        neighbors.addAll(dependents.getOrDefault(source, Collections.emptySet()));
        neighbors.remove(source);
        return neighbors;
    }

    private void buildEdges() {
        for (Map.Entry<String, Node> entry : nodes.entrySet()) {
            String sourcePath = entry.getKey();
            Node node = entry.getValue();
            LinkedHashSet<String> resolvedDependencies = new LinkedHashSet<>();

            for (String importedFqcn : node.importedFqcns) {
                String dependency = declaredTypeToSource.get(importedFqcn);
                if (dependency != null && !dependency.equals(sourcePath)) {
                    resolvedDependencies.add(dependency);
                }
            }

            for (String wildcardPackage : node.wildcardImportedPackages) {
                addPackageDependencies(resolvedDependencies, sourcePath, wildcardPackage);
            }

            for (String staticOwner : node.staticImportedOwnerFqcns) {
                String dependency = declaredTypeToSource.get(staticOwner);
                if (dependency != null && !dependency.equals(sourcePath)) {
                    resolvedDependencies.add(dependency);
                }
            }

            for (String fqcnReference : node.referencedFqcns) {
                String dependency = declaredTypeToSource.get(fqcnReference);
                if (dependency != null && !dependency.equals(sourcePath)) {
                    resolvedDependencies.add(dependency);
                }
            }

            for (String simpleName : node.referencedSimpleTypeNames) {
                String samePackageFqcn = node.packageName.isEmpty() ? simpleName : node.packageName + "." + simpleName;
                String dependency = declaredTypeToSource.get(samePackageFqcn);
                if (dependency != null && !dependency.equals(sourcePath)) {
                    resolvedDependencies.add(dependency);
                }
            }

            dependencies.put(sourcePath, resolvedDependencies);
            for (String dependency : resolvedDependencies) {
                dependents.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(sourcePath);
            }
        }
    }

    private Node parse(String sourcePath) {
        if (sourcePath == null) {
            return null;
        }
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists() || !sourceFile.getName().endsWith(".java")) {
            return null;
        }
        String content = FileUtil.readFile(sourceFile.getAbsolutePath());
        String packageName = "";
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(content);
        if (packageMatcher.find()) {
            packageName = packageMatcher.group(1);
        }

        LinkedHashSet<String> declaredSimpleNames = new LinkedHashSet<>();
        LinkedHashSet<String> declaredFqcns = new LinkedHashSet<>();
        Matcher typeMatcher = TYPE_PATTERN.matcher(content);
        while (typeMatcher.find()) {
            String simpleName = typeMatcher.group(1);
            if (simpleName != null && !simpleName.isEmpty()) {
                declaredSimpleNames.add(simpleName);
                declaredFqcns.add(packageName.isEmpty() ? simpleName : packageName + "." + simpleName);
            }
        }

        LinkedHashSet<String> importedFqcns = new LinkedHashSet<>();
        LinkedHashSet<String> wildcardImportedPackages = new LinkedHashSet<>();
        LinkedHashSet<String> staticImportedOwnerFqcns = new LinkedHashSet<>();
        Matcher importMatcher = IMPORT_PATTERN.matcher(content);
        while (importMatcher.find()) {
            boolean isStatic = importMatcher.group(1) != null;
            String imported = importMatcher.group(2);
            if (imported == null || imported.isEmpty()) {
                continue;
            }

            if (imported.endsWith(".*")) {
                String wildcardTarget = imported.substring(0, imported.length() - 2);
                if (wildcardTarget.isEmpty()) {
                    continue;
                }

                if (isStatic) {
                    staticImportedOwnerFqcns.add(wildcardTarget);
                } else {
                    wildcardImportedPackages.add(wildcardTarget);
                }
                continue;
            }

            if (isStatic) {
                int lastDot = imported.lastIndexOf('.');
                if (lastDot > 0) {
                    staticImportedOwnerFqcns.add(imported.substring(0, lastDot));
                }
                continue;
            }

            importedFqcns.add(imported);
        }

        LinkedHashSet<String> referencedSimpleNames = new LinkedHashSet<>();
        Matcher simpleMatcher = SIMPLE_TYPE_PATTERN.matcher(content);
        while (simpleMatcher.find()) {
            String simpleName = simpleMatcher.group(1);
            if (simpleName == null || declaredSimpleNames.contains(simpleName)) {
                continue;
            }
            referencedSimpleNames.add(simpleName);
        }

        LinkedHashSet<String> referencedFqcns = new LinkedHashSet<>();
        Matcher fqcnMatcher = FQCN_PATTERN.matcher(content);
        while (fqcnMatcher.find()) {
            String fqcn = fqcnMatcher.group(1);
            if (fqcn != null) {
                referencedFqcns.add(fqcn);
            }
        }

        return new Node(
                packageName,
                declaredFqcns,
                importedFqcns,
                wildcardImportedPackages,
                staticImportedOwnerFqcns,
                referencedSimpleNames,
                referencedFqcns
        );
    }

    private void addPackageDependencies(Set<String> resolvedDependencies, String sourcePath, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }

        String packagePrefix = packageName + ".";
        for (Map.Entry<String, String> entry : declaredTypeToSource.entrySet()) {
            if (entry.getKey().startsWith(packagePrefix) && !entry.getValue().equals(sourcePath)) {
                resolvedDependencies.add(entry.getValue());
            }
        }
    }

    private static final class Node implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String packageName;
        private final Set<String> declaredFqcns;
        private final Set<String> importedFqcns;
        private final Set<String> wildcardImportedPackages;
        private final Set<String> staticImportedOwnerFqcns;
        private final Set<String> referencedSimpleTypeNames;
        private final Set<String> referencedFqcns;

        private Node(String packageName,
                     Set<String> declaredFqcns,
                     Set<String> importedFqcns,
                     Set<String> wildcardImportedPackages,
                     Set<String> staticImportedOwnerFqcns,
                     Set<String> referencedSimpleTypeNames,
                     Set<String> referencedFqcns) {
            this.packageName = packageName == null ? "" : packageName;
            this.declaredFqcns = declaredFqcns;
            this.importedFqcns = importedFqcns;
            this.wildcardImportedPackages = wildcardImportedPackages;
            this.staticImportedOwnerFqcns = staticImportedOwnerFqcns;
            this.referencedSimpleTypeNames = referencedSimpleTypeNames;
            this.referencedFqcns = referencedFqcns;
        }
    }
}
