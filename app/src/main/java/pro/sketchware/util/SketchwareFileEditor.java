package pro.sketchware.util;

/**
 * SketchwareFileEditor — patch-style editing of Sketchware project files.
 *
 * Reads an encrypted project file, applies textual edits, and saves back.
 * For AI-driven edits the AgentExecutor calls decrypt → modify → encrypt tools directly.
 */
public class SketchwareFileEditor {

    public static class EditResult {
        public final boolean success;
        public final String  initialContent;
        public final String  editedContent;
        public final String  errorMessage;

        private EditResult(boolean success, String initialContent,
                           String editedContent, String errorMessage) {
            this.success        = success;
            this.initialContent = initialContent;
            this.editedContent  = editedContent;
            this.errorMessage   = errorMessage;
        }

        public static EditResult success(String initial, String edited) {
            return new EditResult(true, initial, edited, null);
        }

        public static EditResult error(String msg) {
            return new EditResult(false, null, null, msg);
        }

        public static EditResult error(String initial, String msg) {
            return new EditResult(false, initial, null, msg);
        }
    }

    /**
     * Applies a simple string-replacement patch to a Sketchware project file.
     */
    public static EditResult patchFile(String scId, String filePath,
                                       String oldContent, String newContent) {
        try {
            String initial = SketchwareFileDecryptor.decryptFile(scId, filePath);
            if (initial == null || initial.isEmpty()) {
                return EditResult.error("Error: File not found or empty: " + filePath);
            }
            if (!initial.contains(oldContent)) {
                return EditResult.error(initial, "Error: Target string not found in: " + filePath);
            }
            String edited = initial.replace(oldContent, newContent);
            boolean saved = SketchwareFileEncryptor.encryptAndSaveFile(scId, filePath, edited);
            if (!saved) {
                return EditResult.error(initial, "Error: Could not save file: " + filePath);
            }
            FileChangeTracker.trackChange(filePath, initial, edited);
            return EditResult.success(initial, edited);
        } catch (Exception e) {
            return EditResult.error("Error modifying file: " + e.getMessage());
        }
    }

    /**
     * Overwrites the entire content of a Sketchware project file.
     */
    public static EditResult overwriteFile(String scId, String filePath, String newContent) {
        try {
            String initial = SketchwareFileDecryptor.decryptFile(scId, filePath);
            boolean saved = SketchwareFileEncryptor.encryptAndSaveFile(scId, filePath, newContent);
            if (!saved) {
                return EditResult.error(initial, "Error: Could not save file: " + filePath);
            }
            FileChangeTracker.trackChange(filePath,
                    initial != null ? initial : "", newContent);
            return EditResult.success(initial, newContent);
        } catch (Exception e) {
            return EditResult.error("Error writing file: " + e.getMessage());
        }
    }
}
