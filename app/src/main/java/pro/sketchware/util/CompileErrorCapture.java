package pro.sketchware.util;

import android.os.Environment;

import java.io.File;

import mod.jbk.diagnostic.CompileErrorSaver;
import pro.sketchware.utility.FilePathUtil;

/**
 * Captures and manages compile errors from the project
 */
public class CompileErrorCapture {
    
    /**
     * Returns the last compile errors for the given project
     * @param scId Project ID
     * @return Compile error text, or null if none
     */
    public static String getLastCompileErrors(String scId) {
        try {
            CompileErrorSaver saver = new CompileErrorSaver(scId);
            return saver.getLogsFromFile();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Returns true if there are saved compile errors
     * @param scId Project ID
     * @return true if there are saved compile errors
     */
    public static boolean hasCompileErrors(String scId) {
        try {
            CompileErrorSaver saver = new CompileErrorSaver(scId);
            return saver.logFileExists();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Extracts a summary of the most relevant compile errors
     * @param errorText Full error text
     * @return Formatted summary of errors
     */
    public static String extractErrorSummary(String errorText) {
        if (errorText == null || errorText.trim().isEmpty()) {
            return "No compilation errors";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("**Compilation Errors:**\n\n");
        
        // Extract main errors
        String[] lines = errorText.split("\n");
        int errorCount = 0;
        
        for (String line : lines) {
            if (line.contains("ERROR") || line.contains("error:")) {
                summary.append("- ").append(line.trim()).append("\n");
                errorCount++;
                if (errorCount >= 10) { // Limitar a 10 erros
                    summary.append("... (more errors)\n");
                    break;
                }
            }
        }
        
        if (errorCount == 0) {
            // If no ERROR pattern found, use first lines
            int maxLines = Math.min(20, lines.length);
            for (int i = 0; i < maxLines; i++) {
                summary.append(lines[i]).append("\n");
            }
        }
        
        return summary.toString();
    }
    
    /**
     * Identifies files mentioned in compile errors
     * @param errorText Texto dos erros
     * @return Array of file names mentioned in the errors
     */
    public static String[] extractFilesFromErrors(String errorText) {
        if (errorText == null) {
            return new String[0];
        }
        
        java.util.List<String> files = new java.util.ArrayList<>();
        
        // Common file reference patterns in Java errors
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "([A-Z][a-zA-Z0-9_]*\\.java|\\w+\\.xml|\\w+\\.kt)"
        );
        
        java.util.regex.Matcher matcher = pattern.matcher(errorText);
        while (matcher.find()) {
            String fileName = matcher.group(1);
            if (!files.contains(fileName)) {
                files.add(fileName);
            }
        }
        
        return files.toArray(new String[0]);
    }
}

