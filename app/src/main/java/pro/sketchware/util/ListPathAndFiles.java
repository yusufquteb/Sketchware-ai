package pro.sketchware.util;

import java.util.List;

/**
 * Helper tool that lists folders, subfolders, and files (including encrypted binaries)
 * within a Sketchware project directory.
 */
public class ListPathAndFiles {

    /**
     * Recursively lists all files and folders, with an optional name filter.
     *
     * @param scId          Sketchware project ID (sc_id)
     * @param path          Base path (null = list entire project structure)
     * @param searchPattern Optional filename filter (null = no filter)
     * @return Formatted text listing all folders and files found
     */
    public static String execute(String scId, String path, String searchPattern) {
        try {
            List<ProjectFileDiscovery.FileInfo> files;

            if (searchPattern != null && !searchPattern.trim().isEmpty()) {
                files = ProjectFileDiscovery.searchFiles(scId, searchPattern);
            } else {
                files = ProjectFileDiscovery.discoverFiles(scId, path);
            }

            if (files == null || files.isEmpty()) {
                return "No files or folders found"
                        + (path != null && !path.isEmpty() ? " at " + path : "") + ".";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("**Project file listing**\n\n");

            if (path != null && !path.isEmpty()) {
                sb.append("Base path: `").append(path).append("`\n\n");
            }

            sb.append("Legend: \uD83D\uDCC1 folder | \uD83D\uDCC4 file\n");
            sb.append("Files marked `encrypted=true` use Sketchware binary format.\n");
            sb.append("IMPORTANT: Files without an extension (no dot in name) are encrypted.\n");
            sb.append("Do NOT add extensions (.json, .xml) when using decrypt_project_file.\n");
            sb.append("Use the EXACT path shown in this listing.\n\n");

            for (ProjectFileDiscovery.FileInfo f : files) {
                sb.append(f.isDirectory ? "\uD83D\uDCC1 " : "\uD83D\uDCC4 ");
                sb.append(f.path);

                if (!f.isDirectory) {
                    sb.append(" (size=").append(f.size).append(" bytes");
                    sb.append(", encrypted=").append(f.isEncrypted ? "true" : "false");
                    if (f.isEncrypted && !f.name.contains(".")) {
                        sb.append(", NO EXTENSION - use exact path");
                    }
                    sb.append(")");
                }

                sb.append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            return "Error listing files: " + e.getMessage();
        }
    }
}
