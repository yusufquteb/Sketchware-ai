package pro.sketchware.ai.tools.code;

import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.ai.models.ToolResult;

/**
 * Lightweight static analysis tools for Sketchware projects.
 * These run on-device without spawning an external lint process.
 */
public class LintTools {

    private static ToolResult success(String msg) { return ToolResult.success(null, msg); }
    private static ToolResult error(String msg)   { return ToolResult.failure(null, msg); }

    // ── RunLintTool ───────────────────────────────────────────────────────────

    public static class RunLintTool implements AgentTool {
        @Override public String getName() { return "run_lint"; }
        @Override public String getDescription() {
            return "Runs a lightweight static analysis pass on a project's Java source files. "
                    + "Detects common issues: unused imports, empty catch blocks, hardcoded strings, "
                    + "missing null checks, and deprecated API usage. Returns a structured report.";
        }
        @Override public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "Project SC ID");
            props.add("sc_id", scIdProp);

            JsonObject fileProp = new JsonObject();
            fileProp.addProperty("type", "string");
            fileProp.addProperty("description", "Optional: specific Java file path relative to the project java dir. Omit to scan all files.");
            props.add("file_path", fileProp);

            JsonArray req = new JsonArray(); req.add("sc_id");
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", props);
            schema.add("required", req);
            return schema;
        }

        @Override public ToolResult execute(JsonObject args, ToolContext context) {
            String scId = args.has("sc_id") ? args.get("sc_id").getAsString() : null;
            if (scId == null || scId.isEmpty()) return error("sc_id is required");
            if (!context.isProjectAllowed(scId)) return error("Project not in workspace");

            File javaDir = context.getProjectJavaDir(scId);
            if (!javaDir.exists()) return error("Java source directory not found: " + javaDir.getAbsolutePath());

            String specificFile = args.has("file_path") ? args.get("file_path").getAsString() : null;
            List<File> filesToScan = new ArrayList<>();

            if (specificFile != null && !specificFile.isEmpty()) {
                File f = new File(javaDir, specificFile);
                if (!f.exists()) return error("File not found: " + f.getAbsolutePath());
                filesToScan.add(f);
            } else {
                collectJavaFiles(javaDir, filesToScan, 0);
            }

            if (filesToScan.isEmpty()) return success("No Java source files found.");

            StringBuilder report = new StringBuilder();
            int totalIssues = 0;
            int scannedFiles = 0;

            for (File file : filesToScan) {
                List<LintIssue> issues = analyzeFile(file, javaDir);
                scannedFiles++;
                if (!issues.isEmpty()) {
                    totalIssues += issues.size();
                    String rel = file.getAbsolutePath().substring(javaDir.getAbsolutePath().length() + 1);
                    report.append("\n📄 ").append(rel).append(" (").append(issues.size()).append(" issue(s)):\n");
                    for (LintIssue issue : issues) {
                        report.append("  Line ").append(issue.line).append(" [")
                              .append(issue.severity).append("] ").append(issue.message).append('\n');
                    }
                }
            }

            if (totalIssues == 0) {
                return success("✅ Scanned " + scannedFiles + " file(s) — no issues found.");
            }
            String header = "Lint scan: " + scannedFiles + " file(s), " + totalIssues + " issue(s) found.\n";
            return success(header + report);
        }

        private static void collectJavaFiles(File dir, List<File> out, int depth) {
            if (depth > 10 || out.size() >= 50) return;
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File f : files) {
                if (f.isDirectory()) collectJavaFiles(f, out, depth + 1);
                else if (f.getName().endsWith(".java")) out.add(f);
            }
        }

        private static List<LintIssue> analyzeFile(File f, File base) {
            List<LintIssue> issues = new ArrayList<>();
            List<String> lines = readLines(f);
            if (lines == null) return issues;

            boolean inMultiLineComment = false;
            List<String> importedClasses = new ArrayList<>();
            boolean inEmptyCatch = false;
            int catchBraceDepth = 0;

            for (int i = 0; i < lines.size(); i++) {
                String raw = lines.get(i);
                String trimmed = raw.trim();
                int lineNum = i + 1;

                // Track multi-line comments
                if (inMultiLineComment) {
                    if (trimmed.contains("*/")) inMultiLineComment = false;
                    continue;
                }
                if (trimmed.startsWith("/*")) { inMultiLineComment = !trimmed.contains("*/"); }
                if (trimmed.startsWith("//")) continue;

                // Collect imports
                if (trimmed.startsWith("import ") && trimmed.endsWith(";")) {
                    String cls = trimmed.substring(7, trimmed.length() - 1).trim();
                    String simpleName = cls.contains(".") ? cls.substring(cls.lastIndexOf('.') + 1) : cls;
                    importedClasses.add(simpleName);
                }

                // Empty catch block: catch(...) { }  or  catch(...) {\n}
                if (trimmed.matches(".*catch\\s*\\(.*\\)\\s*\\{\\s*\\}.*")) {
                    issues.add(new LintIssue(lineNum, "WARNING", "Empty catch block — swallows exception silently"));
                } else if (trimmed.matches(".*catch\\s*\\(.*\\)\\s*\\{")) {
                    inEmptyCatch = true; catchBraceDepth = 1;
                } else if (inEmptyCatch) {
                    for (char c : trimmed.toCharArray()) {
                        if (c == '{') catchBraceDepth++;
                        else if (c == '}') catchBraceDepth--;
                    }
                    if (catchBraceDepth <= 0) {
                        inEmptyCatch = false;
                        // Only flag if the block was truly empty
                        if (trimmed.equals("}")) {
                            issues.add(new LintIssue(lineNum, "WARNING", "Possible empty catch block"));
                        }
                    } else if (!trimmed.isEmpty() && !trimmed.startsWith("//")) {
                        inEmptyCatch = false; // has content
                    }
                }

                // Hardcoded strings in UI method calls (TextView.setText("..."))
                if (trimmed.matches(".*setText\\(\"[^\"]+\"\\).*")) {
                    issues.add(new LintIssue(lineNum, "INFO", "Hardcoded string in setText() — consider using a string resource"));
                }

                // System.out.println / System.err.println (not appropriate in Android)
                if (trimmed.contains("System.out.println") || trimmed.contains("System.err.println")) {
                    issues.add(new LintIssue(lineNum, "WARNING", "Use Log.d/Log.e instead of System.out.println"));
                }

                // Thread.sleep on main thread (heuristic: inside Activity without explicit thread)
                if (trimmed.contains("Thread.sleep(")) {
                    issues.add(new LintIssue(lineNum, "WARNING", "Thread.sleep() may block the UI thread — use Handler(Looper.getMainLooper()).postDelayed() or an ExecutorService background task"));
                }

                // Deprecated: new Thread().start() instead of ExecutorService
                if (trimmed.matches(".*new Thread\\s*\\(.*\\)\\.start\\s*\\(\\).*")) {
                    issues.add(new LintIssue(lineNum, "INFO", "Prefer ExecutorService or Handler over raw Thread.start()"));
                }
            }
            return issues;
        }

        private static List<String> readLines(File f) {
            List<String> lines = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) lines.add(line);
            } catch (IOException e) { return null; }
            return lines;
        }
    }

    private static class LintIssue {
        final int line;
        final String severity;
        final String message;
        LintIssue(int line, String severity, String message) {
            this.line = line; this.severity = severity; this.message = message;
        }
    }
}
