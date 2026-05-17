package pro.sketchware.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight relevance search scoped to the active Sketchware project.
 */
public class SemanticFileSearcher {

    private static final int MAX_RESULTS = 5;
    private static final int MAX_FILE_SIZE_BYTES = 256 * 1024;

    public static class SearchResult {
        public final String filePath;
        public final String relevance;
        public final String snippet;
        public final double score;

        public SearchResult(String filePath, String relevance, String snippet, double score) {
            this.filePath = filePath;
            this.relevance = relevance;
            this.snippet = snippet;
            this.score = score;
        }
    }

    public static List<SearchResult> searchRelevantFiles(String query, String scId) {
        List<SearchResult> results = new ArrayList<>();
        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (normalizedQuery.isEmpty()) {
            return results;
        }

        String[] keywords = normalizedQuery.split("\\s+");
        List<ProjectFileDiscovery.FileInfo> projectFiles = ProjectFileDiscovery.discoverFiles(scId, null);

        for (ProjectFileDiscovery.FileInfo fileInfo : projectFiles) {
            if (fileInfo.isDirectory || fileInfo.size > MAX_FILE_SIZE_BYTES) {
                continue;
            }

            String content = SketchwareFileDecryptor.decryptFile(scId, fileInfo.path);
            if (content == null || content.isEmpty()) {
                continue;
            }

            double score = calculateRelevanceScore(fileInfo.path, content, keywords);
            if (score <= 0) {
                continue;
            }

            results.add(new SearchResult(
                    fileInfo.path,
                    determineRelevance(fileInfo.path, normalizedQuery, content),
                    extractRelevantSnippet(content, keywords),
                    score
            ));
        }

        results.sort((left, right) -> Double.compare(right.score, left.score));
        return results.size() > MAX_RESULTS ? new ArrayList<>(results.subList(0, MAX_RESULTS)) : results;
    }

    private static double calculateRelevanceScore(String filePath, String content, String[] keywords) {
        String lowerPath = filePath.toLowerCase(Locale.ROOT);
        String lowerContent = content.toLowerCase(Locale.ROOT);
        double score = 0.0;

        for (String keyword : keywords) {
            if (keyword.length() < 2) {
                continue;
            }

            int pathHits = countOccurrences(lowerPath, keyword);
            int contentHits = countOccurrences(lowerContent, keyword);
            score += (pathHits * 4.0) + contentHits;

            if (isImportantKeyword(keyword)) {
                score += 3.0;
            }
        }

        return score;
    }

    private static String extractRelevantSnippet(String content, String[] keywords) {
        String lowerContent = content.toLowerCase(Locale.ROOT);
        int bestIndex = -1;
        int bestScore = 0;

        for (int i = 0; i < Math.max(content.length() - 200, 1); i += 40) {
            int end = Math.min(i + 220, content.length());
            String window = lowerContent.substring(i, end);
            int score = 0;

            for (String keyword : keywords) {
                if (window.contains(keyword)) {
                    score++;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        if (bestIndex >= 0) {
            int start = Math.max(0, bestIndex - 40);
            int end = Math.min(content.length(), bestIndex + 220);
            String snippet = content.substring(start, end).trim();
            return snippet.length() > 220 ? snippet.substring(0, 220) + "..." : snippet;
        }

        return content.length() > 220 ? content.substring(0, 220) + "..." : content;
    }

    private static String determineRelevance(String filePath, String query, String content) {
        String lowerPath = filePath.toLowerCase(Locale.ROOT);
        String lowerContent = content.toLowerCase(Locale.ROOT);

        if (lowerPath.contains("/file") || lowerPath.endsWith("/file")) {
            return "Project files metadata";
        }
        if (lowerPath.contains("/logic") || lowerPath.endsWith(".java") || lowerPath.endsWith(".kt")) {
            return "Logic and source code";
        }
        if (lowerPath.contains("/view") || lowerPath.contains("/layout/") || lowerContent.startsWith("<")) {
            return "Layout and UI";
        }
        if (lowerPath.contains("/library") || lowerPath.contains("gradle")) {
            return "Libraries and dependencies";
        }
        if (lowerPath.contains("manifest") || query.contains("manifest")) {
            return "Android manifest";
        }
        return "Project content";
    }

    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    private static boolean isImportantKeyword(String keyword) {
        String[] important = {
                "activity", "fragment", "view", "layout", "component",
                "function", "method", "class", "package", "import",
                "error", "exception", "compile", "build", "manifest"
        };

        for (String item : important) {
            if (keyword.contains(item) || item.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> findRelatedFiles(String filePath, String scId) {
        List<String> related = new ArrayList<>();
        List<ProjectFileDiscovery.FileInfo> projectFiles = ProjectFileDiscovery.discoverFiles(scId, null);

        String baseName = filePath.substring(filePath.lastIndexOf("/") + 1).replaceAll("\\.[^.]+$", "");
        for (ProjectFileDiscovery.FileInfo fileInfo : projectFiles) {
            if (!fileInfo.isDirectory && !fileInfo.path.equals(filePath) && fileInfo.path.contains(baseName)) {
                related.add(fileInfo.path);
            }
        }

        return related;
    }
}
