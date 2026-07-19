package pro.sketchware.ai.diagnostics;

import android.content.Context;
import android.os.Build;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import pro.sketchware.ai.api.AiApiClient;
import pro.sketchware.ai.api.AiClientFactory;
import pro.sketchware.ai.api.StreamingResponseHandler;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.AiProviderModels;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolRegistry;

/**
 * End-to-end health checks for every enabled AI model and every registered tool.
 *
 * <p>Each model receives a minimal "Reply with OK" ping (12-second timeout).
 * Each tool is validated for schema correctness and description presence.
 * Results are written to ai_logs/ai_diagnostic_YYYYMMDD_HHmmss.md.
 */
public class AiDiagnosticRunner {

    /** Progress callbacks — may arrive on a background thread. */
    public interface ProgressCallback {
        /** @param done  items completed so far  @param total total items queued */
        void onStatusUpdate(String status, int done, int total);
        void onComplete(File reportFile);
        void onCancelled();
        void onError(String message);
    }

    // Per-model ping parameters
    private static final int    MODEL_TIMEOUT_SECS = 12;
    private static final String TEST_PROMPT        =
            "Reply with the single word OK and nothing else.";
    private static final String TEST_SYSTEM        =
            "You are a connectivity test bot. Follow instructions exactly.";

    private final Context       context;
    private final AiPreferences preferences;
    private volatile boolean    cancelled;

    public AiDiagnosticRunner(Context context) {
        this.context     = context.getApplicationContext();
        this.preferences = AiPreferences.getInstance(context);
    }

    public void cancel() { cancelled = true; }

    // ── Public entry points ────────────────────────────────────────────────────

    /** Tests only AI models — one request per model in the static list. */
    public void runModelTests(ProgressCallback cb) {
        new Thread(() -> executeModelTests(cb)).start();
    }

    /** Validates tool schema and description for every tool in the global registry. */
    public void runToolTests(ProgressCallback cb) {
        new Thread(() -> executeToolTests(cb)).start();
    }

    /** Runs model tests followed by tool tests and writes a combined report. */
    public void runAll(ProgressCallback cb) {
        new Thread(() -> {
            StringBuilder report = buildHeader();

            // ── Model tests ────────────────────────────────────────────────
            int[] modelSummary = appendModelSection(report, cb);
            if (cancelled) { cb.onCancelled(); return; }

            // ── Tool tests ─────────────────────────────────────────────────
            report.append("---\n\n");
            int[] toolSummary = appendToolSection(report, cb);

            // ── Overall summary ────────────────────────────────────────────
            report.append("\n---\n\n## SUMMARY\n\n");
            report.append("### Models\n");
            report.append("  Tested:  ").append(modelSummary[0]).append("\n");
            report.append("  Passed:  ").append(modelSummary[1]).append("\n");
            report.append("  Failed:  ").append(modelSummary[0] - modelSummary[1]).append("\n\n");
            report.append("### Tools\n");
            report.append("  Total:   ").append(toolSummary[0]).append("\n");
            report.append("  Valid:   ").append(toolSummary[1]).append("\n");
            report.append("  Invalid: ").append(toolSummary[0] - toolSummary[1]).append("\n");

            File f = writeReport(report.toString());
            if (f != null) cb.onComplete(f);
            else cb.onError("Failed to write report to disk");
        }).start();
    }

    // ── Section builders ───────────────────────────────────────────────────────

    /**
     * Appends model test results to {@code report}.
     * @return int[]{tested, passed}
     */
    private int[] appendModelSection(StringBuilder report, ProgressCallback cb) {
        report.append("## MODEL TESTS\n\n");

        int total  = countModelsToTest();
        int done   = 0;
        int tested = 0;
        int passed = 0;

        for (AiProvider provider : AiProvider.values()) {
            if (cancelled) break;

            boolean enabled = preferences.isProviderEnabled(provider);
            boolean hasKey  = !provider.requiresApiKey() || preferences.hasApiKey(provider);

            if (!enabled) {
                report.append("### ").append(provider.getDisplayName())
                      .append(" ⛔  (disabled by user)\n\n");
                continue;
            }
            if (!hasKey) {
                report.append("### ").append(provider.getDisplayName())
                      .append(" ⛔  (no API key)\n\n");
                continue;
            }

            String apiKey = preferences.getApiKey(provider);
            AiApiClient client = AiClientFactory.createClient(context, provider, apiKey);
            if (client == null) {
                report.append("### ").append(provider.getDisplayName())
                      .append(" ⛔  (client creation failed)\n\n");
                continue;
            }

            List<String> models = modelsFor(provider);
            report.append("### ").append(provider.getDisplayName()).append("\n");

            int provPass = 0;
            for (String modelId : models) {
                if (cancelled) break;
                done++;
                cb.onStatusUpdate(
                        "Testing " + provider.getDisplayName() + " / " + modelId,
                        done, total);

                ModelTestResult r = pingModel(client, modelId);
                tested++;
                if (r.success) { passed++; provPass++; }
                report.append(formatModelLine(modelId, r)).append("\n");
            }

            report.append("   → ").append(provPass).append(" / ").append(models.size())
                  .append(" passed\n\n");
        }

        return new int[]{tested, passed};
    }

    /**
     * Appends tool validation results to {@code report}.
     * @return int[]{total, valid}
     */
    private int[] appendToolSection(StringBuilder report, ProgressCallback cb) {
        report.append("## TOOL TESTS\n\n");

        ToolRegistry registry = ToolRegistry.createGlobal();
        List<AgentTool> tools = registry.getAllTools();

        cb.onStatusUpdate("Checking " + tools.size() + " tools…", 0, tools.size());

        int valid = 0;
        for (int i = 0; i < tools.size(); i++) {
            if (cancelled) break;
            AgentTool tool = tools.get(i);
            cb.onStatusUpdate("Tool: " + tool.getName(), i + 1, tools.size());

            boolean schemaOk = isSchemaValid(tool.getParametersSchema());
            boolean descOk   = tool.getDescription() != null
                    && !tool.getDescription().trim().isEmpty();

            if (schemaOk && descOk) {
                valid++;
                int params = paramCount(tool.getParametersSchema());
                report.append("✅  ")
                      .append(String.format(Locale.getDefault(), "%-36s", tool.getName()))
                      .append("  ").append(params).append(" param")
                      .append(params == 1 ? "" : "s").append("\n");
            } else {
                report.append("❌  ").append(tool.getName());
                if (!schemaOk) report.append("  [invalid schema]");
                if (!descOk)   report.append("  [missing description]");
                report.append("\n");
            }
        }

        report.append("\nTotal: ").append(tools.size())
              .append("   Valid: ").append(valid)
              .append("   Invalid: ").append(tools.size() - valid).append("\n\n");

        return new int[]{tools.size(), valid};
    }

    // ── Stand-alone runners (model-only / tool-only) ───────────────────────────

    private void executeModelTests(ProgressCallback cb) {
        StringBuilder report = buildHeader();
        int[] summary = appendModelSection(report, cb);
        if (cancelled) { cb.onCancelled(); return; }
        report.append("---\n\nTested: ").append(summary[0])
              .append("   Passed: ").append(summary[1])
              .append("   Failed: ").append(summary[0] - summary[1]).append("\n");
        File f = writeReport(report.toString());
        if (f != null) cb.onComplete(f);
        else cb.onError("Failed to write report");
    }

    private void executeToolTests(ProgressCallback cb) {
        StringBuilder report = buildHeader();
        int[] summary = appendToolSection(report, cb);
        if (cancelled) { cb.onCancelled(); return; }
        report.append("---\n\nTotal: ").append(summary[0])
              .append("   Valid: ").append(summary[1])
              .append("   Invalid: ").append(summary[0] - summary[1]).append("\n");
        File f = writeReport(report.toString());
        if (f != null) cb.onComplete(f);
        else cb.onError("Failed to write report");
    }

    // ── Model ping ─────────────────────────────────────────────────────────────

    private ModelTestResult pingModel(AiApiClient client, String modelId) {
        ModelTestResult r = new ModelTestResult(modelId);
        long start = System.currentTimeMillis();

        List<ChatMessage> messages = Collections.singletonList(
                ChatMessage.userMessage(UUID.randomUUID().toString(), TEST_PROMPT));

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder chunkBuf = new StringBuilder();

        client.sendChatRequest(messages, modelId, TEST_SYSTEM,
                new StreamingResponseHandler() {
                    @Override public void onChunk(String delta) {
                        if (delta != null && chunkBuf.length() < 60) chunkBuf.append(delta);
                    }
                    @Override public void onToolCall(ToolCall tc) {}
                    @Override public void onComplete(String fullText) {
                        r.success   = true;
                        r.latencyMs = System.currentTimeMillis() - start;
                        String raw  = chunkBuf.length() > 0
                                ? chunkBuf.toString()
                                : (fullText != null ? fullText : "");
                        r.response  = raw.trim().replace("\n", " ");
                        if (r.response.length() > 45)
                            r.response = r.response.substring(0, 45) + "…";
                        latch.countDown();
                    }
                    @Override public void onError(String error) {
                        r.success   = false;
                        r.latencyMs = System.currentTimeMillis() - start;
                        r.error     = error != null ? error.trim() : "Unknown error";
                        if (r.error.length() > 90) r.error = r.error.substring(0, 90) + "…";
                        latch.countDown();
                    }
                });

        try {
            if (!latch.await(MODEL_TIMEOUT_SECS, TimeUnit.SECONDS)) {
                r.success   = false;
                r.error     = "Timeout after " + MODEL_TIMEOUT_SECS + "s (no response)";
                r.latencyMs = MODEL_TIMEOUT_SECS * 1000L;
            }
        } catch (InterruptedException ex) {
            r.success = false;
            r.error   = "Interrupted";
        }

        // Rate-limit / server-side 5xx → model exists but is temporarily unavailable.
        // Count as alive so the user doesn't think the model ID is invalid.
        if (!r.success && r.error != null) {
            String e = r.error.toLowerCase(Locale.getDefault());
            if (e.contains("429") || e.contains("rate limit") || e.contains("quota")
                    || e.contains("503") || e.contains("502") || e.contains("server error")
                    || e.contains("overloaded")) {
                r.success  = true;
                r.response = "⚡ rate-limited / server busy — model exists";
                if (r.latencyMs == 0) r.latencyMs = System.currentTimeMillis() - start;
            }
        }

        return r;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<String> modelsFor(AiProvider provider) {
        List<String> list = new ArrayList<>(AiProviderModels.getStaticModels(provider));
        if (list.isEmpty()) {
            String sel = preferences.getSelectedModel(provider);
            if (sel != null && !sel.isEmpty()) list.add(sel);
        }
        return list;
    }

    private int countModelsToTest() {
        int n = 0;
        for (AiProvider p : AiProvider.values()) {
            if (!preferences.isProviderEnabled(p)) continue;
            if (p.requiresApiKey() && !preferences.hasApiKey(p)) continue;
            List<String> m = AiProviderModels.getStaticModels(p);
            n += m.isEmpty() ? 1 : m.size();
        }
        return n;
    }

    private String formatModelLine(String modelId, ModelTestResult r) {
        if (r.success) {
            return String.format(Locale.getDefault(),
                    "✅  %-42s  (%dms)  \"%s\"",
                    modelId, r.latencyMs, r.response != null ? r.response : "");
        } else {
            return String.format(Locale.getDefault(),
                    "❌  %-42s  %s",
                    modelId, r.error != null ? r.error : "unknown error");
        }
    }

    private boolean isSchemaValid(JsonObject schema) {
        return schema != null && schema.has("type");
    }

    private int paramCount(JsonObject schema) {
        try {
            JsonObject props = schema.getAsJsonObject("properties");
            return props != null ? props.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private StringBuilder buildHeader() {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append("# AI Diagnostic Report\n");
        sb.append("Generated: ").append(ts).append("\n");
        sb.append("Device: Android ").append(Build.VERSION.RELEASE)
          .append("  Model: ").append(Build.MODEL).append("\n\n---\n\n");
        return sb;
    }

    private File writeReport(String content) {
        File dir = new File(context.getExternalFilesDir(null), "ai_logs");
        if (!dir.exists()) dir.mkdirs();
        String name = "ai_diagnostic_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date())
                + ".md";
        File f = new File(dir, name);
        try (FileWriter fw = new FileWriter(f)) {
            fw.write(content);
            return f;
        } catch (IOException e) {
            return null;
        }
    }

    // ── Result POJO ────────────────────────────────────────────────────────────

    public static class ModelTestResult {
        public final String modelId;
        public boolean success;
        public String  response;
        public String  error;
        public long    latencyMs;

        ModelTestResult(String id) { this.modelId = id; }
    }
}
