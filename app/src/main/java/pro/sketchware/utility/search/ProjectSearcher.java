package pro.sketchware.utility.search;

import pro.sketchware.utility.io.SafeFileOps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ProjectSearcher {
    private ProjectSearcher() {}

    public static List<File> findFiles(File root, String glob) throws Exception {
        GlobPattern pattern = GlobPattern.compile(glob == null ? "**" : glob);
        List<File> results = new ArrayList<>();
        String base = root.getCanonicalPath();
        for (File file : SafeFileOps.listFilesRecursively(root)) {
            String rel = file.getCanonicalPath().substring(base.length()).replace(File.separatorChar, '/');
            if (rel.startsWith("/")) rel = rel.substring(1);
            if (pattern.matches(rel)) results.add(file);
        }
        results.sort(Comparator.comparing(File::getAbsolutePath));
        return results;
    }

    public static List<FileSearchResult> searchText(File root, String query, String glob, int limit) throws Exception {
        String needle = query == null ? "" : query.toLowerCase(Locale.US);
        List<FileSearchResult> results = new ArrayList<>();
        for (File file : findFiles(root, glob == null ? "**" : glob)) {
            if (!isLikelyText(file)) continue;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    int score = score(line, needle);
                    if (score > 0) results.add(new FileSearchResult(file, lineNumber, line, score));
                    if (limit > 0 && results.size() >= limit) return results;
                }
            } catch (Exception ignored) {
            }
        }
        results.sort((a, b) -> Integer.compare(b.score, a.score));
        return results;
    }

    private static int score(String line, String needle) {
        if (needle.isEmpty()) return 1;
        String hay = line == null ? "" : line.toLowerCase(Locale.US);
        if (hay.equals(needle)) return 100;
        if (hay.contains(needle)) return 50 + Math.min(40, needle.length());
        int score = 0;
        for (String token : needle.split("\\s+")) if (!token.isEmpty() && hay.contains(token)) score += 10;
        return score;
    }

    private static boolean isLikelyText(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        return name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".xml") || name.endsWith(".gradle") || name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".pro") || name.endsWith(".properties");
    }
}
