package pro.sketchware.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Searches files by glob pattern.
 * Exemplos: padrões como .java, test/.ts, etc.
 * Retorna caminhos ordenados por data de modificação.
 */
public class GlobFileSearch {
    
    /**
     * Searches files using a glob pattern.
     * @param scId ID do projeto
     * @param globPattern Glob pattern (e.g. *.java, test/*.kt)
     * @return List of matching files sorted by modification date (newest first)
     */
    public static List<ProjectFileDiscovery.FileInfo> search(String scId, String globPattern) {
        List<ProjectFileDiscovery.FileInfo> matches = new ArrayList<>();
        
        try {
            // Convert glob pattern to regex
            String regexPattern = globToRegex(globPattern);
            Pattern pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
            
            // Discover all project files
            List<ProjectFileDiscovery.FileInfo> allFiles = ProjectFileDiscovery.discoverFiles(scId, null);
            
            // Filter files matching the pattern
            for (ProjectFileDiscovery.FileInfo fileInfo : allFiles) {
                if (!fileInfo.isDirectory) {
                    // Check if file name or path matches pattern
                    if (pattern.matcher(fileInfo.name).matches() || 
                        pattern.matcher(fileInfo.path).matches()) {
                        matches.add(fileInfo);
                    }
                }
            }
            
            // Sort by modification date (newest first)
            matches.sort((a, b) -> {
                try {
                    File projectBase = ProjectPathResolver.getSketchwareRoot();
                    
                    File fileA = new File(projectBase, a.path);
                    File fileB = new File(projectBase, b.path);
                    
                    if (fileA.exists() && fileB.exists()) {
                        return Long.compare(fileB.lastModified(), fileA.lastModified());
                    }
                } catch (Exception e) {
                    // Ignore sort errors
                }
                return 0;
            });
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return matches;
    }
    
    /**
     * Converts glob pattern to regex
     * Suporta: *, **, ?, [chars], {a,b}
     */
    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        int len = glob.length();
        boolean inBrackets = false;
        boolean inBraces = false;
        
        for (int i = 0; i < len; i++) {
            char c = glob.charAt(i);
            
            switch (c) {
                case '*':
                    if (i + 1 < len && glob.charAt(i + 1) == '*') {
                        // ** corresponde a qualquer sequência de diretórios
                        regex.append(".*");
                        i++; // Pular o próximo *
                    } else {
                        // * corresponde a qualquer sequência de caracteres (exceto /)
                        regex.append("[^/]*");
                    }
                    break;
                    
                case '?':
                    // ? corresponde a um único caractere (exceto /)
                    regex.append("[^/]");
                    break;
                    
                case '[':
                    inBrackets = true;
                    regex.append('[');
                    break;
                    
                case ']':
                    inBrackets = false;
                    regex.append(']');
                    break;
                    
                case '{':
                    inBraces = true;
                    regex.append('(');
                    break;
                    
                case '}':
                    inBraces = false;
                    regex.append(')');
                    break;
                    
                case ',':
                    if (inBraces) {
                        regex.append('|');
                    } else {
                        regex.append(',');
                    }
                    break;
                    
                case '.':
                case '+':
                case '(':
                case ')':
                case '^':
                case '$':
                case '|':
                    // Escapar caracteres especiais do regex
                    regex.append('\\').append(c);
                    break;
                    
                default:
                    regex.append(c);
                    break;
            }
        }
        
        return regex.toString();
    }
}

