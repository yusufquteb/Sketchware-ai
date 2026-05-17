package pro.sketchware.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Searches for text patterns in code (grep-style)
 * Supports regex and exact string search for symbols and functions
 */
public class CodeGrep {
    
    /**
     * A single grep search result
     */
    public static class GrepResult {
        public final String filePath;
        public final int lineNumber;
        public final String lineContent;
        public final String match;
        
        public GrepResult(String filePath, int lineNumber, String lineContent, String match) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.lineContent = lineContent;
            this.match = match;
        }
    }
    
    /**
     * Searches for a pattern in a specific file
     * @param filePath Relative file path
     * @param pattern Search pattern (regex or plain text)
     * @param useRegex If true, treats pattern as regex; if false, exact match
     * @return List of matching results
     */
    public static List<GrepResult> searchInFile(String scId, String filePath, String pattern, boolean useRegex) {
        List<GrepResult> results = new ArrayList<>();
        
        try {
            String content = SketchwareFileDecryptor.decryptFile(scId, filePath);
            if (content == null || content.isEmpty()) {
                return results;
            }
            
            String[] lines = content.split("\n");
            Pattern regexPattern = null;
            
            if (useRegex) {
                try {
                    regexPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                } catch (Exception e) {
                    // If invalid regex, fall back to plain search
                    useRegex = false;
                }
            }
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                boolean matches = false;
                String match = null;
                
                if (useRegex && regexPattern != null) {
                    Matcher matcher = regexPattern.matcher(line);
                    if (matcher.find()) {
                        matches = true;
                        match = matcher.group();
                    }
                } else {
                    // Busca simples (case-insensitive)
                    if (line.toLowerCase().contains(pattern.toLowerCase())) {
                        matches = true;
                        match = pattern;
                    }
                }
                
                if (matches) {
                    results.add(new GrepResult(filePath, i + 1, line.trim(), match));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return results;
    }
    
    /**
     * Searches for a pattern across multiple project files
     * @param scId ID do projeto
     * @param pattern Padrão de busca
     * @param useRegex Se true, trata pattern como regex
     * @param filePattern Padrão opcional para filtrar arquivos (ex: "*.java")
     * @return List of matching results
     */
    public static List<GrepResult> searchInProject(String scId, String pattern, boolean useRegex, String filePattern) {
        List<GrepResult> allResults = new ArrayList<>();
        
        // Discover project files
        List<ProjectFileDiscovery.FileInfo> files;
        if (filePattern != null && !filePattern.isEmpty()) {
            files = GlobFileSearch.search(scId, filePattern);
        } else {
            files = ProjectFileDiscovery.discoverFiles(scId, null);
        }
        
        // Search in each file
        for (ProjectFileDiscovery.FileInfo fileInfo : files) {
            if (!fileInfo.isDirectory) {
                List<GrepResult> fileResults = searchInFile(scId, fileInfo.path, pattern, useRegex);
                allResults.addAll(fileResults);
            }
        }
        
        return allResults;
    }
}

