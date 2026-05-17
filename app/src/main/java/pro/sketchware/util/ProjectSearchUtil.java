package pro.sketchware.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ProjectSearchUtil provides advanced search capabilities across the project.
 * It supports Regex, Case-sensitivity, and File Type filtering.
 */
public class ProjectSearchUtil {

    public enum FileFilter {
        ALL, JAVA, XML, GRADLE, DATA
    }

    public static class SearchResult {
        public final String filePath;
        public final int lineNumber;
        public final String lineContent;
        public final boolean editable;

        public SearchResult(String filePath, int lineNumber, String lineContent, boolean editable) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.lineContent = lineContent;
            this.editable = editable;
        }

        @Override
        public String toString() {
            return filePath + ":" + lineNumber + " -> " + lineContent.trim();
        }
    }

    public static List<SearchResult> globalSearch(File rootDir, String query, boolean caseSensitive, boolean useRegex, FileFilter filter) {
        List<SearchResult> results = new ArrayList<>();
        
        Pattern pattern;
        try {
            if (useRegex) {
                pattern = Pattern.compile(query, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
            } else {
                pattern = Pattern.compile(Pattern.quote(query), caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
            }
        } catch (Exception e) {
            return results; // Invalid regex
        }

        searchRecursive(rootDir, query, pattern, filter, results);
        return results;
    }

    private static void searchRecursive(File dir, String query, Pattern pattern, FileFilter filter, List<SearchResult> results) {
        if (dir == null || !dir.exists()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (!file.getName().startsWith(".")) {
                    searchRecursive(file, query, pattern, filter, results);
                }
            } else {
                if (passesFilter(file, filter)) {
                    searchInFile(dir, file, pattern, results);
                }
            }
        }
    }

    private static boolean passesFilter(File file, FileFilter filter) {
        String n = file.getName().toLowerCase();
        switch (filter) {
            case JAVA:   return n.endsWith(".java") || n.endsWith(".kt");
            case XML:    return n.endsWith(".xml");
            case GRADLE: return n.endsWith(".gradle") || n.endsWith(".kts") || n.endsWith(".toml");
            case DATA:   return !n.contains(".");
            case ALL:
            default:     return n.endsWith(".java") || n.endsWith(".kt") || n.endsWith(".xml")
                    || n.endsWith(".json") || n.endsWith(".gradle") || n.endsWith(".kts")
                    || n.endsWith(".properties") || n.endsWith(".txt") || !n.contains(".");
        }
    }

    private static void searchInFile(File rootDir, File file, Pattern pattern, List<SearchResult> results) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 1;
            boolean isEditable = file.getAbsolutePath().contains("/editable/"); // Simple heuristic
            
            while ((line = reader.readLine()) != null) {
                if (pattern.matcher(line).find()) {
                    results.add(new SearchResult(file.getAbsolutePath(), lineNumber, line, isEditable));
                }
                lineNumber++;
            }
        } catch (IOException ignored) {}
    }
}
