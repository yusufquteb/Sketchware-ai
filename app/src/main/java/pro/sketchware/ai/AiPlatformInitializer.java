package pro.sketchware.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

import pro.sketchware.ai.chat.coordinator.ChatCoordinator;
import pro.sketchware.ai.offline.OfflineModeController;
import pro.sketchware.ai.orchestrator.AIOrchestrator;
import pro.sketchware.ai.tools.ToolManager;
import pro.sketchware.ai.tools.impl.data.AnalyzeXmlTool;
import pro.sketchware.ai.tools.impl.data.ParseJsonTool;
import pro.sketchware.ai.tools.impl.file.DeleteFileTool;
import pro.sketchware.ai.tools.impl.file.ReadFileTool;
import pro.sketchware.ai.tools.impl.file.WriteFileTool;
import pro.sketchware.ai.tools.impl.utility.CopyTextTool;

/**
 * AiPlatformInitializer — Single entry point for wiring the Stage 2 AI platform.
 *
 * <p>Call {@link #initialize} once at app/Activity startup. After initialization:
 * <ul>
 *   <li>All tools are registered in {@link ToolManager}.</li>
 *   <li>{@link AIOrchestrator} is wired to {@link ChatCoordinator} via the AiDelegate.</li>
 *   <li>{@link OfflineModeController} is wired to {@link ChatCoordinator} via the OfflineMessageHandler.</li>
 * </ul>
 *
 * <p><b>Usage example (in Activity.onCreate or Application.onCreate):</b>
 * <pre>
 * // Create coordinator first (Stage 1 already handles this)
 * ChatCoordinator coordinator = new ChatCoordinator(this);
 *
 * // Initialize Stage 2 — pass your AI model provider
 * AiPlatformInitializer.Result platform = AiPlatformInitializer.initialize(
 *     this,
 *     coordinator,
 *     new MyAiModelProvider()   // your AIOrchestrator.AiModelProvider implementation
 * );
 *
 * // Access the offline controller if needed
 * platform.offlineController.activateOfflineMode();
 * </pre>
 */
public final class AiPlatformInitializer {

    private static final String TAG = "AiPlatformInitializer";

    // Prevent instantiation
    private AiPlatformInitializer() {}

    // ─── Result ───────────────────────────────────────────────────────────────

    /**
     * Result of initialization — holds references to all Stage 2 components.
     */
    public static final class Result {
        /** The tool registry (pre-populated with all standard tools). */
        @NonNull public final ToolManager toolManager;

        /** The AI decision layer — connected to the coordinator as an AiDelegate. */
        @NonNull public final AIOrchestrator orchestrator;

        /** The offline tool execution controller. */
        @NonNull public final OfflineModeController offlineController;

        private Result(
                @NonNull ToolManager toolManager,
                @NonNull AIOrchestrator orchestrator,
                @NonNull OfflineModeController offlineController
        ) {
            this.toolManager       = toolManager;
            this.orchestrator      = orchestrator;
            this.offlineController = offlineController;
        }
    }

    // ─── Initialize ───────────────────────────────────────────────────────────

    /**
     * Initializes the full Stage 2 AI platform and wires it to the given coordinator.
     *
     * <p>This method:
     * <ol>
     *   <li>Registers all standard tools in {@link ToolManager}.</li>
     *   <li>Creates {@link AIOrchestrator} with the given model provider.</li>
     *   <li>Wires {@link AIOrchestrator} to the coordinator via {@code setAiDelegate}.</li>
     *   <li>Creates {@link OfflineModeController} and wires it to the coordinator.</li>
     * </ol>
     *
     * @param context       application or activity context
     * @param coordinator   the existing ChatCoordinator from Stage 1
     * @param modelProvider the AI model backend; pass null for offline-only mode
     * @return a {@link Result} containing all initialized Stage 2 components
     */
    @NonNull
    public static Result initialize(
            @NonNull Context context,
            @NonNull ChatCoordinator coordinator,
            @Nullable AIOrchestrator.AiModelProvider modelProvider
    ) {
        Log.d(TAG, "Initializing AI Platform Stage 2…");

        // ── 1. Get/configure ToolManager ───────────────────────────────────
        ToolManager toolManager = ToolManager.getInstance();
        registerStandardTools(context, toolManager);

        Log.d(TAG, "Tools registered: " + toolManager.getToolCount());

        // ── 2. Create AIOrchestrator ───────────────────────────────────────
        // If no model provider is given, use a no-op provider that triggers offline mode
        AIOrchestrator.AiModelProvider effectiveProvider =
                modelProvider != null ? modelProvider : buildNoOpProvider();

        AIOrchestrator orchestrator = new AIOrchestrator(effectiveProvider, toolManager);

        // ── 3. Wire orchestrator to coordinator ────────────────────────────
        coordinator.setAiDelegate(orchestrator);
        Log.d(TAG, "AIOrchestrator wired to ChatCoordinator.");

        // ── 4. Create OfflineModeController ────────────────────────────────
        OfflineModeController offlineController =
                new OfflineModeController(toolManager, coordinator);

        // Wire offline handler to coordinator
        coordinator.setOfflineMessageHandler(offlineController::handleOfflineMessage);
        Log.d(TAG, "OfflineModeController wired to ChatCoordinator.");

        // ── 5. If no model provider, auto-activate offline mode ────────────
        if (modelProvider == null) {
            Log.d(TAG, "No AI model provider — activating offline mode.");
            offlineController.activateOfflineMode();
        }

        Log.d(TAG, "Stage 2 initialization complete.");
        return new Result(toolManager, orchestrator, offlineController);
    }

    // ─── Tool Registration ────────────────────────────────────────────────────

    /**
     * Registers all standard tools with the ToolManager.
     * Add new tools here as the platform grows.
     */
    private static void registerStandardTools(
            @NonNull Context context,
            @NonNull ToolManager toolManager
    ) {
        toolManager.registerAll(Arrays.asList(
                // File tools
                new ReadFileTool(),
                new WriteFileTool(),
                new DeleteFileTool(),

                // Data tools
                new ParseJsonTool(),
                new AnalyzeXmlTool(),

                // Utility tools
                new CopyTextTool(context)
        ));
    }

    // ─── No-Op Provider ───────────────────────────────────────────────────────

    /**
     * A no-op AI model provider used when no real AI backend is available.
     * Returning null from complete() causes the orchestrator to trigger an error,
     * which will surface through the coordinator (and may trigger offline mode).
     */
    @NonNull
    private static AIOrchestrator.AiModelProvider buildNoOpProvider() {
        return new AIOrchestrator.AiModelProvider() {
            @Nullable
            @Override
            public String complete(
                    @NonNull String systemPrompt,
                    @NonNull String context,
                    @NonNull String userMessage
            ) {
                // Return a JSON response that tells the user AI is unavailable
                return "{\"type\":\"text\","
                        + "\"content\":\"AI model is not configured. "
                        + "Use /tool_name commands to work offline. "
                        + "Type /help to see available tools.\"}";
            }

            @Override
            public void cancel() {
                // No-op
            }
        };
    }
}
