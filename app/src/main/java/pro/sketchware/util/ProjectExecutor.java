package pro.sketchware.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.utility.FileUtil;

/**
 * ProjectExecutor acts as the "hands" of the AI Agent.
 * It performs surgical mutations and provides headless analysis:
 * - Patching specific blocks of text.
 * - Appending content to the end.
 * - Inserting content at specific line numbers.
 * - Reading only specific ranges of lines.
 * - Auto-rollback support via backups.
 */
public class ProjectExecutor {

    /**
     * Replaces 'searchString' with 'replaceString' in the given file.
     * Supports Auto-Rollback by taking a backup before mutation.
     */
    public static boolean patchFile(File file, String searchString, String replaceString) throws IOException {
        if (!file.exists() || !file.isFile()) return false;

        String content = FileUtil.readFile(file.getAbsolutePath());
        if (!content.contains(searchString)) return false;

        // Take backup for potential rollback
        createBackup(file);

        String newContent = content.replace(searchString, replaceString);
        if (!content.equals(newContent)) {
            FileUtil.writeFile(file.getAbsolutePath(), newContent);
            return true;
        }
        return false;
    }

    /**
     * Appends code to the end of the file.
     */
    public static void appendCode(File file, String content) throws IOException {
        if (!file.exists()) return;
        createBackup(file);
        try (FileWriter fw = new FileWriter(file, true)) {
            fw.write("\n" + content);
        }
    }

    /**
     * Inserts code at a specific line number.
     */
    public static void insertCodeAtLine(File file, int lineNumber, String content) throws IOException {
        if (!file.exists()) return;
        createBackup(file);
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }

        int index = Math.max(0, Math.min(lineNumber - 1, lines.size()));
        lines.add(index, content);

        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l).append("\n");
        }
        FileUtil.writeFile(file.getAbsolutePath(), sb.toString());
    }

    /**
     * Reads a specific range of lines from a file.
     * Headless Analysis: Provides only the requested snippet to save tokens.
     */
    public static String readFileRange(File file, int startLine, int endLine) throws IOException {
        if (!file.exists()) return "File not found";

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int currentLine = 1;
            while ((line = br.readLine()) != null) {
                if (currentLine >= startLine && currentLine <= endLine) {
                    sb.append("L").append(currentLine).append(": ").append(line).append("\n");
                }
                if (currentLine > endLine) break;
                currentLine++;
            }
        }
        return sb.length() == 0 ? "No lines found in specified range" : sb.toString();
    }

    /**
     * Auto-Rollback: Restores the file from the last backup.
     */
    public static boolean rollback(File file) {
        File backup = new File(file.getAbsolutePath() + ".bak");
        if (backup.exists()) {
            try {
                Files.copy(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    private static void createBackup(File file) throws IOException {
        File backup = new File(file.getAbsolutePath() + ".bak");
        Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
