package pro.sketchware.ai.tools.impl.utility;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import pro.sketchware.ai.tools.Tool;

/**
 * CopyTextTool — Copies text content to the Android system clipboard.
 *
 * <p><b>Expected JSON input:</b>
 * <pre>
 * {
 *   "text":  "The text to copy",
 *   "label": "Optional clipboard label"   // optional, default "AI Copy"
 * }
 * </pre>
 *
 * <p>This tool operates synchronously because {@link ClipboardManager}
 * requires main-thread access on some API levels. The {@link ToolManager}
 * handles thread routing via its executor, but this tool dispatches back
 * to the main thread internally using a {@link android.os.Handler}.
 */
public class CopyTextTool implements Tool {

    public static final String NAME = "copy_text";

    @NonNull
    private final Context applicationContext;

    public CopyTextTool(@NonNull Context context) {
        this.applicationContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Copies text to the Android system clipboard. "
                + "The copied text can be pasted anywhere on the device.";
    }

    @Nullable
    @Override
    public String getInputSchema() {
        return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "  \"text\":{\"type\":\"string\",\"description\":\"The text to copy to clipboard\"},"
                + "  \"label\":{\"type\":\"string\",\"description\":\"Optional clipboard label (visible in clipboard manager)\"}"
                + "},"
                + "\"required\":[\"text\"]"
                + "}";
    }

    @NonNull
    @Override
    public ToolResult execute(@Nullable String jsonInput) {
        // ── 1. Parse input ─────────────────────────────────────────────────
        if (jsonInput == null || jsonInput.trim().isEmpty()) {
            return ToolResult.failure(NAME,
                    "Input required. Provide {\"text\": \"...\"}");
        }

        String text;
        String label;
        try {
            JSONObject input = new JSONObject(jsonInput);
            text  = input.optString("text", "");
            label = input.optString("label", "AI Copy");
        } catch (JSONException e) {
            return ToolResult.failure(NAME, "Invalid JSON input: " + e.getMessage());
        }

        if (text.isEmpty()) {
            return ToolResult.failure(NAME, "'text' field is required and must not be empty.");
        }

        // ── 2. Copy to clipboard (must run on main thread) ─────────────────
        final String finalText  = text;
        final String finalLabel = label;
        final boolean[] success = {false};
        final Object lock = new Object();

        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                ClipboardManager clipboard =
                        (ClipboardManager) applicationContext
                                .getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText(finalLabel, finalText);
                    clipboard.setPrimaryClip(clip);
                    success[0] = true;
                }
            } finally {
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        });

        // Wait for the main-thread operation to complete (max 3 seconds)
        synchronized (lock) {
            try {
                lock.wait(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolResult.failure(NAME, "Copy operation was interrupted.");
            }
        }

        if (!success[0]) {
            return ToolResult.failure(NAME,
                    "ClipboardManager is unavailable on this device.");
        }

        // ── 3. Result ──────────────────────────────────────────────────────
        int charCount = text.length();
        int wordCount = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;

        return ToolResult.success(
                "Copied to clipboard successfully.\n"
                + "Label: " + label + "\n"
                + "Characters: " + charCount + "\n"
                + "Words: " + wordCount + "\n"
                + "Preview: " + (charCount > 80 ? text.substring(0, 80) + "…" : text)
        );
    }
}
