package pro.sketchware.ai.offline;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

import pro.sketchware.ai.api.AiApiClient;
import pro.sketchware.ai.api.StreamingResponseHandler;
import pro.sketchware.ai.api.ToolDefinition;
import pro.sketchware.ai.engine.budget.TokenBudgetChecker;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.ai.models.ToolCall;

/**
 * {@link AiApiClient} implementation that runs entirely on-device via {@link LlamaCppEngineBridge}
 * — no network request is made for chat generation. Implements the same abstract contract every
 * cloud provider client implements (verified against the actual {@code AiApiClient.java} in this
 * archive before writing this class — see CHANGES.md Phase 5) so it drops into the existing
 * send path in {@code AiClientFactory} without changes anywhere else in the chat pipeline.
 *
 * <p><b>llama.cpp migration (this session).</b> This provider previously ran on LiteRT-LM (Google's
 * {@code .litertlm} engine, since removed), whose catalog files each had a fixed 4096-token KV
 * cache baked in at export time — not a runtime-configurable value, and the root cause of the
 * local model only ever getting {@code ToolRegistry}'s smallest tool tier. It now runs on
 * {@link LlamaCppEngineBridge} (vendored `:llama` module wrapping {@code ggml-org/llama.cpp}'s
 * own {@code examples/llama.android}), which takes context size as a load-time parameter instead
 * — fixed at 8192 tokens for every model in this first migration pass (see
 * {@code LlamaCppEngineBridge.CONTEXT_SIZE_TOKENS}; GPU backend and per-device context tiering
 * are deferred follow-ups, not part of this change).
 *
 * <p><b>The engine is stateful — this class no longer resends full history.</b> This is a
 * deliberate design change from every prior revision of this class (LiteRT-LM era included): the
 * vendored llama.cpp module keeps the running conversation (KV cache, chat-templated turn
 * history, its own generated replies) entirely inside native code once a system prompt has been
 * established — see {@link LlamaCppEngineBridge}'s class doc. {@link #buildPromptAssembly} now
 * only ever extracts the *newest* message from {@code messages} (folding a "tool" role result
 * into user-turn text, same as before) plus the system/tool/knowledge blocks; it does not build
 * or resend a history list. {@link #isContinuationOf} decides, on every call, whether the
 * incoming {@code messages} list is a strict-prefix-preserving continuation of what this instance
 * last sent to the engine — if so, the native conversation's own memory is trusted and only the
 * new message is sent; if not (first call, provider switched away and back, history edited/
 * reordered, etc.), {@link LlamaCppEngineBridge#generate}'s {@code forceReset} re-establishes the
 * system prompt fresh, and only the newest message is sent — older turns are not replayed into
 * the new conversation, since the module's public API has no bulk-history-seeding method. This is
 * an accepted, documented trade-off, not an oversight. <b>Not verified against a real build</b> —
 * see {@link LlamaCppEngineBridge}'s class doc for what's confirmed against the actual vendored
 * source versus what still needs a real NDK build to test.
 *
 * <p><b>Context/Token MVP — tool calling re-enabled for the local model, tiered subset only.</b>
 * Phase 5.4 disabled tools entirely because sending the live {@code ToolRegistry} — 106 registered
 * tools as of that phase — reformatted into a compact prompt block on every call was fundamentally
 * incompatible with a 4096-token model: even a minimal one-line-per-tool format for all 106 tools
 * alone landed in the 8,000–11,000-token range, confirmed from an actual field failure where a
 * single "hi" message produced an estimated ~11203 prompt tokens. Rather than sending the full
 * registry, {@code AgentExecutor} sends {@code ToolRegistry.getToolsForContextBudget(8192)} —
 * which resolves to the MEDIUM tier (~21 tools) for this provider, a deliberate audit decision
 * (see {@code ProviderCapabilities}'s LOCAL_LLM comment for the reasoning and guards; an earlier
 * revision of this doc said "7-tool essential subset", which stopped being what actually happens
 * once the context budget doubled). {@link #buildToolBlock} builds a compact block from whatever
 * it's given and its measured token cost is reserved in the prompt budget the same way the system
 * prompt's is (see {@link #buildPromptAssembly}). {@link #parseToolCall}, {@code
 * TOOL_CALL_OPEN_TAG}/{@code TOOL_CALL_CLOSE_TAG}, and the {@code <tool_call>...</tool_call>}
 * convention are live for this provider.
 *
 * <p><b>Still open — Phi-4 "exits the project" after a tool call.</b> A field report from the
 * LiteRT-LM era: Phi-4 emits a parseable {@code <tool_call>} block but then leaves/closes the
 * project after the tool result is fed back. Not re-tested against llama.cpp — the suspected cause
 * (tool-result shape, or {@code sc_id}/project scope threading through {@link AgentExecutor}'s
 * shared tool-execution loop) is engine-independent, so it needs its own field reproduction on
 * this engine before being considered resolved either way.
 *
 * <p><b>fetchModels()</b>: there is no network models-list endpoint for a local engine, so this
 * returns the single currently-selected {@link LocalModelCatalog} entry as one {@link ModelInfo}.
 *
 * <p><b>Not tested end-to-end</b>: no Android device/emulator or NDK is available in this
 * environment (github.com access became available mid-session and was used to vendor the real
 * `:llama` module and verify its actual API — see {@link LlamaCppEngineBridge}'s class doc — but
 * huggingface.co remains blocked, and nothing here has been compiled or run). See CHANGES.md
 * Phase 5/6 for the full field-verification and migration history.
 */
public class LocalModelProvider extends AiApiClient {

    private final Context appContext;
    private final LocalModelManager modelManager;
    private final LlamaCppEngineBridge engineBridge;
    /** Persistent project rules/env/tool notes — see class javadoc for why this exists
     *  separately from the trimmed chat history (see {@link #buildPromptAssembly}). */
    private final pro.sketchware.ai.offline.knowledge.KnowledgeStore knowledgeStore;

    /** Tags of requests the caller has asked to cancel — checked between chunks. */
    private final CopyOnWriteArraySet<Object> cancelledTags = new CopyOnWriteArraySet<>();

    /**
     * The full {@code messages} list this instance sent to the engine on its last call, used by
     * {@link #isContinuationOf} to decide whether the next call can trust the native engine's own
     * conversation memory or must force a fresh system-prompt reset — see class javadoc's
     * "engine is stateful" note. Null until the first call. Not thread-safe beyond the
     * {@code volatile} publish — this provider's calls are expected to be sequential per
     * conversation the same way {@link LlamaCppEngineBridge}'s single-threaded engine access is.
     */
    private volatile List<ChatMessage> lastSentMessages = null;

    // ── Prompt budget ─────────────────────────────────────────────────────
    //
    // llama.cpp migration: HARD_KV_CACHE_TOKENS now mirrors
    // LlamaCppEngineBridge.CONTEXT_SIZE_TOKENS (8192, a load-time parameter for this engine —
    // see that class's doc) rather than a value baked into the model file at export time, which
    // is what LiteRT-LM's ekv4096 exports required. Reserve a slice for the model's own output;
    // the rest budgets the system instruction (system prompt + knowledge block + tool block)
    // plus the single newest message this class now sends — see class javadoc for why this is
    // no longer a multi-turn history budget. Deliberately conservative: TokenBudgetChecker's
    // estimator is a coarse 4-chars-per-token heuristic (see its own javadoc), and under-filling
    // the context window is far safer than an engine-level failure from overfilling it.
    //
    // Context/Token MVP: the tool-block reservation covers the MEDIUM tool tier (~21 tools —
    // what AgentExecutor actually resolves for this provider at 8192, see class javadoc and
    // ProviderCapabilities' LOCAL_LLM comment) rather than the full 106+-tool registry Phase
    // 5.4 found unaffordable. The block's real measured size is subtracted from the budget in
    // buildPromptAssembly, and the pre-flight check below rejects any assembled prompt that
    // still ends up too large.
    private static final int HARD_KV_CACHE_TOKENS = LlamaCppEngineBridge.CONTEXT_SIZE_TOKENS;
    private static final int RESERVED_FOR_OUTPUT_TOKENS = 1024;
    /** Safety margin subtracted before the final pre-flight check, on top of the
     *  RESERVED_FOR_OUTPUT_TOKENS already carved out — covers estimator error, since
     *  TokenBudgetChecker's char-count heuristic is approximate, not exact. */
    private static final int FINAL_CHECK_SAFETY_MARGIN_TOKENS = 128;
    /** Max size of the persistent-knowledge block (project rules/env/tool notes) — kept
     *  deliberately small since it competes with chat history for the same context window.
     *  CRITICAL entries are still included in full even past this cap (see KnowledgeBlockBuilder
     *  javadoc) so a rule the user marked critical is never silently dropped; this cap mainly
     *  limits how many NORMAL (relevance-matched) entries get pulled in. */
    private static final int MAX_KNOWLEDGE_BLOCK_TOKENS = 600;

    // ── Tool-call prompt convention ─────────────────────────────────────────
    //
    // Strict, single-purpose markers — chosen specifically so parseToolCall() never has to
    // guess: a message either contains this exact block or it doesn't.
    private static final String TOOL_CALL_OPEN_TAG = "<tool_call>";
    private static final String TOOL_CALL_CLOSE_TAG = "</tool_call>";

    public LocalModelProvider(@NonNull Context context) {
        // No API key concept for a local engine — pass empty string, matching how
        // no-auth providers like PollinationsApiClient already do it.
        super("", AiProvider.LOCAL_LLM);
        this.appContext = context.getApplicationContext();
        this.modelManager = new LocalModelManager(appContext);
        this.engineBridge = new LlamaCppEngineBridge(appContext);
        this.knowledgeStore = new pro.sketchware.ai.offline.knowledge.KnowledgeStore(appContext);
    }

    // ── AiApiClient contract ────────────────────────────────────────────────

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        LocalModelCatalog selected = modelManager.getSelectedModel();
        List<ModelInfo> result = new ArrayList<>();
        result.add(new ModelInfo(
                selected.getId(),
                selected.getDisplayName() + " (on-device)",
                AiProvider.LOCAL_LLM,
                LlamaCppEngineBridge.CONTEXT_SIZE_TOKENS, // fixed n_ctx load-time parameter, see that class's doc
                selected.getCapabilityNote()));
        return result;
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                 String systemPrompt, StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, null, handler);
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                 String systemPrompt, Object tag, StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, null, tag, handler);
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId, String systemPrompt,
                                 List<ToolDefinition> tools, StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, tools, null, handler);
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId, String systemPrompt,
                                 List<ToolDefinition> tools, Object tag, StreamingResponseHandler handler) {
        // Resolve the model the caller actually asked for (the BottomSheet picker calls
        // preferences.setSelectedModel(LOCAL_LLM, model.getId()) but that write goes to a
        // different storage key than LocalModelManager's KEY_SELECTED_MODEL — so without this
        // lookup, generation would silently ignore the picked model and always fall back to
        // whatever was last set via the Settings screen's onSelect()). getSelectedModel() is
        // kept only as the fallback for callers that don't pass a specific modelId.
        // Phase 5.6: DEEPSEEK_R1_DISTILL_QWEN_1_5B was re-enabled in the catalog, so the special
        // case that used to redirect a direct request for it back to the default was removed —
        // an explicit modelId for it is now honored like any other catalog entry.
        // Runtime half of the minSdk mismatch between this app (minSdk 26) and the vendored
        // :llama module (minSdk 33, arm64-v8a/x86_64 only) — see AndroidManifest.xml's
        // tools:overrideLibrary and app/build.gradle's dependency-block comment for the full
        // reasoning. Checked first, before touching modelManager/engineBridge at all, so a
        // device below the floor gets a clear message instead of risking an
        // UnsatisfiedLinkError or crash further down.
        if (!LlamaCppEngineBridge.isDeviceSupported()) {
            handler.onError("Offline AI needs Android 13+ on a 64-bit device (arm64-v8a/x86_64) "
                    + "— this device doesn't meet that requirement.");
            return;
        }

        LocalModelCatalog requested = modelId != null ? LocalModelCatalog.fromId(modelId) : null;
        LocalModelCatalog selected = requested != null ? requested : modelManager.getSelectedModel();
        if (modelManager.getState(selected) != LocalModelState.READY) {
            handler.onError("Model \"" + selected.getDisplayName()
                    + "\" isn't downloaded on this device yet — download it first from AI Settings.");
            return;
        }

        boolean continuing = isContinuationOf(messages);
        PromptAssembly assembly = buildPromptAssembly(messages, systemPrompt, tools);
        lastSentMessages = messages;

        // Phase 5.3 — final pre-flight check: buildPromptAssembly already truncates the newest
        // message to a computed budget, but that budget is itself an estimate (char-count
        // heuristic) — measuring the *actual* assembled content one last time, right before it
        // reaches the engine, catches whatever the budgeting step could still miss, so the user
        // gets a clear in-app message instead of the engine's raw "too long" failure.
        int finalPromptTokens = assembly.estimatedTotalTokens();
        int finalCheckLimit = HARD_KV_CACHE_TOKENS - RESERVED_FOR_OUTPUT_TOKENS - FINAL_CHECK_SAFETY_MARGIN_TOKENS;
        if (finalPromptTokens > finalCheckLimit) {
            handler.onError("This message is too long for \"" + selected.getDisplayName()
                    + "\" (~" + finalPromptTokens + " tokens estimated, limit ~" + finalCheckLimit
                    + " for this on-device model). Try shortening it or starting a new conversation.");
            return;
        }

        File modelFile = modelManager.getModelFile(selected);
        cancelledTags.remove(tag);

        // Streams model output live for normal conversation, but withholds anything that is (or
        // might be) part of a tool call so the raw <tool_call>{"name":...}</tool_call> block (or
        // an untagged fallback JSON object matching an offered tool name) never appears as
        // visible chat text — see ToolCallStreamGate's own javadoc for exactly how it decides
        // what's safe to forward immediately vs. what must wait for onComplete's parseToolCall
        // pass. Previously this callback forwarded every raw token to handler.onChunk()
        // unconditionally, so a user watching the stream would see the literal tool-call JSON
        // typed into the chat bubble before the tool actually ran — the tool DID still execute
        // (parseToolCall + handler.onToolCall() below were already correct), but it looked to the
        // user like the assistant was "just writing text" instead of taking action, which is
        // this bug's exact reported symptom.
        ToolCallStreamGate streamGate = new ToolCallStreamGate(TOOL_CALL_OPEN_TAG, handler::onChunk);

        engineBridge.generate(modelFile, assembly.systemInstruction, assembly.latestUserMessage,
                /* forceReset= */ !continuing, modelManager.isGpuBackendPreferred(),
                new LlamaCppEngineBridge.GenerationCallback() {
            @Override
            public void onChunk(@NonNull String textDelta) {
                if (cancelledTags.contains(tag)) return;
                streamGate.onDelta(textDelta);
            }

            @Override
            public void onComplete(@NonNull String fullResponse) {
                if (cancelledTags.contains(tag)) return;
                // Flush whatever the gate is still holding back (e.g. a suspected-but-unconfirmed
                // JSON object that turned out not to be a real tool call) now that the full
                // response is available to check for certain — see ToolCallStreamGate.flush().
                streamGate.flush();
                // Check the *complete* response for a tool-call block. Chunk-by-chunk
                // detection would need to buffer across arbitrary token boundaries anyway
                // (the tags can split across chunks), so parsing once on the assembled
                // text is both simpler and exactly as correct.
                ToolCall parsedCall = parseToolCall(fullResponse, tools);
                if (parsedCall != null) {
                    handler.onToolCall(parsedCall);
                }
                handler.onComplete(fullResponse);
                cancelledTags.remove(tag);
            }

            @Override
            public void onError(@NonNull String message) {
                handler.onError(message);
                cancelledTags.remove(tag);
            }
        });
    }

    /**
     * Holds the structured pieces {@link LlamaCppEngineBridge#generate} needs. No history list —
     * see class javadoc's "engine is stateful" note; the native conversation carries its own
     * memory once established, so only the system instruction and the single newest message are
     * ever built here.
     */
    private static final class PromptAssembly {
        @Nullable final String systemInstruction;
        @NonNull final String latestUserMessage;
        final int estimatedTokens;

        PromptAssembly(@Nullable String systemInstruction,
                        @NonNull String latestUserMessage, int estimatedTokens) {
            this.systemInstruction = systemInstruction;
            this.latestUserMessage = latestUserMessage;
            this.estimatedTokens = estimatedTokens;
        }

        int estimatedTotalTokens() {
            return estimatedTokens;
        }
    }

    /**
     * True if {@code messages} is a strict-prefix-preserving continuation of the list this
     * instance last sent to the engine ({@link #lastSentMessages}) — i.e. every message this
     * provider already told the engine about is still present, unchanged, in the same order,
     * with only new messages appended after it. When true, the native conversation's own memory
     * (see {@link LlamaCppEngineBridge}'s class doc) is trusted and {@link #sendChatRequest} only
     * sends the newest message. When false (first call ever, a provider switch away and back,
     * edited/reordered/regenerated history, or a completely different conversation), the caller
     * passes {@code forceReset=true} to {@link LlamaCppEngineBridge#generate}, which
     * re-establishes the system prompt and starts the native conversation fresh from just the
     * newest message — older turns are not replayed in, since the engine's public API has no
     * bulk-history-seeding method (see that class's doc). This is a heuristic, not a guarantee:
     * it compares by role+content equality, not object identity, so it correctly recognizes
     * continuation across independently-constructed {@link ChatMessage} instances with the same
     * content (e.g. after being reloaded from persistence) as long as nothing actually changed.
     */
    private boolean isContinuationOf(List<ChatMessage> messages) {
        List<ChatMessage> prior = lastSentMessages;
        if (prior == null || messages == null || messages.size() <= prior.size()) return false;
        for (int i = 0; i < prior.size(); i++) {
            ChatMessage a = prior.get(i);
            ChatMessage b = messages.get(i);
            if (!java.util.Objects.equals(a.getRole(), b.getRole())
                    || !java.util.Objects.equals(a.getContent(), b.getContent())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds the system instruction (system prompt + persistent knowledge block + tool block)
     * and extracts the single newest message to send — see class javadoc's "engine is stateful"
     * note for why this no longer builds or trims a multi-turn history list the way every prior
     * revision of this class did.
     *
     * <p><b>Context/Token MVP — small tool block.</b> {@code tools} is expected to be the 7-tool
     * essential subset ({@code ToolRegistry.getEssentialTools()}), not the full 106+-tool
     * registry Phase 5.4 found unaffordable (an all-106 compact block alone landed in the
     * 8,000–11,000-token range — confirmed from an actual failure where a single "hi" message
     * reported ~11203 estimated tokens). See {@link #buildToolBlock} for how the block itself is
     * built.
     *
     * <p><b>Persistent knowledge block.</b> Project rules/env/tool notes stored in {@link
     * #knowledgeStore} are folded into {@code systemInstruction} the same way, budgeted the same
     * way {@code toolBlock} is — see {@link pro.sketchware.ai.offline.knowledge.KnowledgeBlockBuilder}.
     * Reading this from a store outside {@code messages} means it's re-sent every time the system
     * prompt is (re-)established, regardless of how much native conversation memory is retained.
     *
     * <p>The newest message is truncated (tail kept) to fit whatever budget remains after the
     * system instruction's real measured size is subtracted from {@link #HARD_KV_CACHE_TOKENS} —
     * see {@link #truncateStringToBudget}.
     */
    private PromptAssembly buildPromptAssembly(List<ChatMessage> messages, String systemPrompt,
                                                List<ToolDefinition> tools) {
        int systemPromptTokens = TokenBudgetChecker.estimateTokens(systemPrompt);
        String toolBlock = buildToolBlock(tools);
        int toolBlockTokens = TokenBudgetChecker.estimateTokens(toolBlock);

        ChatMessage newest = (messages != null && !messages.isEmpty())
                ? messages.get(messages.size() - 1) : null;
        String newestContentRaw = newest != null && newest.getContent() != null
                ? newest.getContent() : "";
        // "tool" is this app's own third role (see ChatMessage's constructors) with no dedicated
        // chat-template role of its own on the native side (only system/user/assistant — see
        // LlamaCppEngineBridge's class doc) — folded into a labeled user-turn text so the model
        // still sees the tool result as content.
        String latestUserMessage = newest != null && "tool".equals(newest.getRole())
                ? "[Tool result: " + newest.getToolName() + "] " + newestContentRaw
                : newestContentRaw;

        // Rank NORMAL knowledge entries against the newest message — the same message this call
        // is actually about to send, so relevance ranking matches what the model will see.
        //
        // RFC-001 review, recommendation 1: routed through KnowledgeRetriever rather than
        // calling KnowledgeBlockBuilder directly, per KnowledgeRetriever's own javadoc — "both
        // the online path (AgentExecutor.buildCompactSystemPrompt) and the offline path
        // (LocalModelProvider.buildPromptFromMessages) are expected to call this same method
        // once wired in, so the two engines never drift into two different notions of what the
        // assistant knows." pageContext isn't currently threaded into sendChatRequest's
        // signature (it would require changing the AiApiClient interface every provider
        // implements, out of scope here), so this passes null for it as before.
        pro.sketchware.ai.offline.knowledge.KnowledgeBlockBuilder.Result knowledgeResult =
                pro.sketchware.ai.offline.knowledge.KnowledgeRetriever.buildContextBlock(
                        knowledgeStore, latestUserMessage, /* pageContext= */ null,
                        MAX_KNOWLEDGE_BLOCK_TOKENS);
        String knowledgeBlock = knowledgeResult.block;
        int knowledgeBlockTokens = knowledgeResult.estimatedTokens;

        StringBuilder systemSb = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            systemSb.append(systemPrompt);
        }
        if (!knowledgeBlock.isEmpty()) {
            if (systemSb.length() > 0) systemSb.append("\n\n");
            systemSb.append(knowledgeBlock);
        }
        if (!toolBlock.isEmpty()) {
            if (systemSb.length() > 0) systemSb.append("\n\n");
            systemSb.append(toolBlock);
        }
        String systemInstruction = systemSb.length() > 0 ? systemSb.toString() : null;

        int messageBudget = HARD_KV_CACHE_TOKENS - RESERVED_FOR_OUTPUT_TOKENS
                - systemPromptTokens - toolBlockTokens - knowledgeBlockTokens;
        // Guard against a pathologically large system prompt (or tool block, or a knowledge
        // block dominated by CRITICAL entries — see that builder's javadoc on why CRITICAL
        // entries are never trimmed) alone consuming the whole cache — the newest message is
        // still always kept (truncated if needed), so a zero-or-negative budget here just means
        // "keep only a small tail of it."
        if (messageBudget < 0) messageBudget = 0;
        latestUserMessage = truncateStringToBudget(latestUserMessage, messageBudget);

        int totalTokens = systemPromptTokens + toolBlockTokens + knowledgeBlockTokens
                + TokenBudgetChecker.estimateTokens(latestUserMessage);
        return new PromptAssembly(systemInstruction, latestUserMessage, totalTokens);
    }

    /**
     * Builds a compact, one-line-per-tool prompt block for the tiered tool subset the caller
     * passes in (the MEDIUM tier, ~21 tools, for this provider at 8192 — see class javadoc;
     * versus the full 106+-tool catalog this class's Phase 5.4 javadoc describes as unaffordable).
     * A compact block this size coexists comfortably with a conversation inside the 8192-token
     * context, unlike the full registry. Returns an empty string when {@code tools} is null or
     * empty so callers can skip adding the block entirely.
     *
     * <p><b>Parameter-name fix (field bug — wrong argument keys in tool calls).</b> This method
     * previously rendered only each tool's name and free-text description — never its actual
     * {@link ToolDefinition#getParameters()} JSON Schema, even though {@code ToolRegistry
     * .getEssentialTools()} already populates that field correctly from each tool's real
     * {@code getParametersSchema()}. With no parameter names visible anywhere in the prompt, a
     * small model had nothing to go on but the literal example in this block's own instruction
     * line ({@code {"name":...,"arguments":{...}}}) — which is why field reports showed calls like
     * {@code describe_layout} being invoked with {@code {"name":"main"}} instead of the tool's
     * actual required parameters, {@code {"sc_id":"...","activity_name":"main"}}. Each tool's
     * required parameter names (and their JSON Schema {@code type}) are now appended in
     * parentheses after its description, e.g. {@code "describe_layout: ...description... (required:
     * sc_id: string, activity_name: string)"}, giving the model the real keys to copy instead of
     * inventing a generic {@code name} key by pattern-matching the instruction line. This mirrors
     * — at far lower token cost — what the online/cloud path already gets for free: providers'
     * native {@code tools} parameter always carries the full JSON Schema, so this offline block was
     * the one place in the whole prompt pipeline where parameter names were silently dropped.
     */
    private String buildToolBlock(List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("Available tools (call using ")
                .append(TOOL_CALL_OPEN_TAG).append("{\"name\":\"<tool_name>\",\"arguments\":{<the exact ")
                .append("parameter names listed for that tool below>}}").append(TOOL_CALL_CLOSE_TAG)
                .append(" — use the REAL parameter names shown per tool, never a generic \"name\" key ")
                .append("for arguments):\n");
        for (ToolDefinition tool : tools) {
            sb.append("- ").append(tool.getName()).append(": ").append(tool.getDescription());
            String paramSummary = summarizeRequiredParameters(tool.getParameters());
            if (!paramSummary.isEmpty()) {
                sb.append(" (required: ").append(paramSummary).append(")");
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * Renders a JSON-Schema {@code parameters} object as {@code "key: type, key2: type2"} for the
     * properties listed in its {@code required} array — the minimum a small model needs to build a
     * correctly-keyed {@code arguments} object. Optional parameters are omitted from this summary
     * to keep the block small (the tool's own description text is expected to mention any optional
     * parameters worth knowing about); this only fixes the *required*-argument-name guessing that
     * was the confirmed field bug. Returns an empty string for a null/malformed schema rather than
     * throwing — a missing parameter summary degrades to the old (buggy but non-crashing) behavior
     * for that one tool, instead of breaking the whole block.
     */
    private String summarizeRequiredParameters(JsonObject parameters) {
        if (parameters == null) return "";
        try {
            if (!parameters.has("properties") || !parameters.get("properties").isJsonObject()) return "";
            JsonObject properties = parameters.getAsJsonObject("properties");

            java.util.LinkedHashSet<String> requiredNames = new java.util.LinkedHashSet<>();
            if (parameters.has("required") && parameters.get("required").isJsonArray()) {
                for (JsonElement el : parameters.getAsJsonArray("required")) {
                    if (el.isJsonPrimitive()) requiredNames.add(el.getAsString());
                }
            }
            // Fall back to every declared property if the schema has no explicit "required"
            // array — better to show real names the model might not otherwise need than to
            // show none at all for a tool whose author didn't mark anything required.
            java.util.Collection<String> namesToShow = requiredNames.isEmpty()
                    ? properties.keySet() : requiredNames;

            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String name : namesToShow) {
                if (!properties.has(name) || !properties.get(name).isJsonObject()) continue;
                JsonObject propSchema = properties.getAsJsonObject(name);
                String type = propSchema.has("type") && propSchema.get("type").isJsonPrimitive()
                        ? propSchema.get("type").getAsString() : "string";
                if (!first) sb.append(", ");
                sb.append(name).append(": ").append(type);
                first = false;
            }
            return sb.toString();
        } catch (RuntimeException e) {
            // Malformed schema on one tool shouldn't take down the whole block — see javadoc.
            return "";
        }
    }

    /**
     * Truncates {@code content} (tail kept, not head) to roughly fit {@code budgetTokens} via
     * {@link TokenBudgetChecker}'s char-per-token estimate. Replaces the old message-list
     * trimming this class used when it resent full history — see class javadoc's "engine is
     * stateful" note for why only the single newest message's size matters here now.
     *
     * <p>A budget of 0 or less still keeps a small tail rather than an empty string, so the
     * model always sees at least a fragment of the user's actual latest message instead of
     * nothing.
     */
    private String truncateStringToBudget(String content, int budgetTokens) {
        if (content == null || content.isEmpty()) return content == null ? "" : content;

        int maxChars = Math.max(budgetTokens, 1) * 4; // inverse of TokenBudgetChecker's 4-chars-per-token estimate
        if (content.length() <= maxChars) return content;

        return "…(earlier part of this message was trimmed to fit the on-device model)…\n"
                + content.substring(content.length() - maxChars);
    }

    /**
     * Scans {@code text} for the first {@code {...}} object with balanced braces, ignoring
     * braces that appear inside JSON string literals (so a value like {@code "note": "use {x}"}
     * doesn't throw off the count). Returns null if no balanced object is found.
     */
    private String extractBalancedJsonObject(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        if (start < 0) return null;

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null; // unbalanced — never a valid tool call
    }

    /**
     * Looks for a tool call in {@code fullResponse} and parses it into a {@link ToolCall} — first
     * via the strict {@code <tool_call>{...}</tool_call>} tag pair, falling back to an untagged
     * scan when that's absent (see below). When {@code offeredTools} was non-empty for this
     * request, {@link #buildToolBlock} has already instructed the model to emit the tag pair; when
     * no tools were sent, nothing instructs the model to emit it, so a model that free-associates
     * into this tag pair on its own is treated the same as one that does so on purpose — an
     * acceptable edge case since it can only happen if the model coincidentally reproduces the
     * tag text verbatim.
     *
     * <p><b>Untagged-JSON fallback (field bug — some models drop the wrapper tags).</b> A field
     * report showed Gemma emitting a syntactically-valid {@code {"name": "describe_layout",
     * "arguments": {...}}} object narrated in prose (e.g. {@code "OK. Calling describe_layout...
     * tool_call {"name": ...}"}) but without the literal {@code <tool_call>}/{@code </tool_call>}
     * tags the strict path above requires — so it was silently treated as plain chat and the tool
     * was never actually invoked. When the strict tag-pair scan finds nothing, this method now
     * falls back to scanning {@code fullResponse} for any brace-balanced JSON object whose
     * {@code name} field exactly matches one of the tool names in {@code offeredTools} (the same
     * list {@link #buildToolBlock} built the prompt's tool list from for this call). Gating on a
     * name match against tools actually offered this turn — rather than parsing any JSON object
     * found anywhere in the response — is what keeps this fallback from misfiring on unrelated
     * JSON a model might legitimately include in a normal chat answer (e.g. an example payload the
     * user asked to see); an object that happens to have a {@code name} key isn't enough on its
     * own, it must name a tool this call actually made available.
     *
     * <p>Returns null — never a best-effort guess — when neither the strict tags nor the fallback
     * find a match, so a malformed or missing block is always treated as plain chat rather than a
     * misfired tool call.
     *
     * <p>The JSON payload is located with a brace-balance scan ({@link #extractBalancedJsonObject})
     * rather than a regex — {@code arguments} can itself contain nested {@code {}} objects, and a
     * non-greedy regex would cut the match off at the first inner {@code }} instead of the outer
     * one.
     */
    private ToolCall parseToolCall(String fullResponse, @Nullable List<ToolDefinition> offeredTools) {
        if (fullResponse == null || fullResponse.isEmpty()) return null;

        ToolCall strict = parseStrictTaggedToolCall(fullResponse);
        if (strict != null) return strict;

        // Fallback only runs when tools were actually offered this turn — with nothing offered,
        // there's no known tool name to gate against, so the fallback would have nothing safe to
        // match and is skipped entirely (same as returning null immediately).
        if (offeredTools == null || offeredTools.isEmpty()) return null;

        java.util.Set<String> offeredNames = new java.util.HashSet<>();
        for (ToolDefinition t : offeredTools) {
            if (t.getName() != null) offeredNames.add(t.getName());
        }
        return parseUntaggedToolCallMatchingKnownName(fullResponse, offeredNames);
    }

    /** Strict path: requires the literal {@code <tool_call>...</tool_call>} wrapper — see
     *  {@link #parseToolCall}'s javadoc for the full rationale. */
    @Nullable
    private ToolCall parseStrictTaggedToolCall(String fullResponse) {
        int openIdx = fullResponse.indexOf(TOOL_CALL_OPEN_TAG);
        if (openIdx < 0) return null;
        int closeIdx = fullResponse.indexOf(TOOL_CALL_CLOSE_TAG, openIdx + TOOL_CALL_OPEN_TAG.length());
        if (closeIdx < 0) return null;

        String between = fullResponse.substring(openIdx + TOOL_CALL_OPEN_TAG.length(), closeIdx);
        String jsonPayload = extractBalancedJsonObject(between);
        if (jsonPayload == null) return null;
        return parseToolCallJson(jsonPayload);
    }

    /** Fallback path: scans for a brace-balanced JSON object anywhere in {@code fullResponse}
     *  whose {@code name} matches an entry in {@code offeredNames} — see {@link #parseToolCall}'s
     *  "Untagged-JSON fallback" javadoc for why the name-match gate is required. Scans left to
     *  right and returns the first match, since a model that drops the wrapper tags still reliably
     *  emits at most one call per turn in observed field reports. */
    @Nullable
    private ToolCall parseUntaggedToolCallMatchingKnownName(String fullResponse, java.util.Set<String> offeredNames) {
        int searchFrom = 0;
        while (true) {
            int braceIdx = fullResponse.indexOf('{', searchFrom);
            if (braceIdx < 0) return null;

            String jsonPayload = extractBalancedJsonObject(fullResponse.substring(braceIdx));
            if (jsonPayload == null) {
                // No balanced object starting at this brace — move past it and keep scanning
                // rather than giving up on the whole response.
                searchFrom = braceIdx + 1;
                continue;
            }

            ToolCall candidate = parseToolCallJson(jsonPayload);
            if (candidate != null && offeredNames.contains(candidate.getName())) {
                return candidate;
            }
            searchFrom = braceIdx + jsonPayload.length();
        }
    }

    /** Shared JSON→{@link ToolCall} parsing used by both the strict and fallback paths — see
     *  {@link #parseToolCall}'s javadoc for why a malformed payload returns null rather than a
     *  best-effort guess. */
    @Nullable
    private ToolCall parseToolCallJson(String jsonPayload) {
        try {
            JsonElement parsed = JsonParser.parseString(jsonPayload);
            if (!parsed.isJsonObject()) return null;
            JsonObject obj = parsed.getAsJsonObject();

            if (!obj.has("name") || obj.get("name").isJsonNull()) return null;
            String name = obj.get("name").getAsString();
            if (name.isEmpty()) return null;

            JsonElement argumentsEl = obj.get("arguments");
            String argumentsJson = (argumentsEl != null && !argumentsEl.isJsonNull())
                    ? argumentsEl.toString()
                    : "{}";

            return new ToolCall(UUID.randomUUID().toString(), name, argumentsJson);
        } catch (JsonSyntaxException | IllegalStateException e) {
            // Malformed JSON — treat as no tool call rather than guessing at a partial/broken
            // payload.
            return null;
        }
    }

    @Override
    public void cancelAll() {
        engineBridge.cancelGeneration();
        cancelledTags.clear();
    }

    @Override
    public void cancelByTag(Object tag) {
        if (tag != null) cancelledTags.add(tag);
    }

    @Override
    public void shutdown() {
        cancelAll();
        engineBridge.close();
        lastSentMessages = null; // engine's own conversation memory is gone — force a reset next call
        knowledgeStore.close();
    }

    /** Exposed so a settings screen can read/edit persistent project knowledge without this
     *  class needing to know anything about that UI — same separation-of-concerns pattern
     *  {@code LocalModelManager#getContext()} already uses elsewhere in this package. */
    @NonNull
    public pro.sketchware.ai.offline.knowledge.KnowledgeStore getKnowledgeStore() {
        return knowledgeStore;
    }

    /**
     * Gates raw generation output so text that is (or might still turn out to be) part of a tool
     * call never reaches the chat UI as visible streamed text — only genuine conversational
     * content is forwarded live. Fixes the bug where the offline assistant appeared to just
     * "write" a tool call into the chat instead of running it: the tool execution itself
     * ({@link #parseToolCall} + {@code handler.onToolCall}) was already correct, but every raw
     * token — including the {@code <tool_call>{"name":...}</tool_call>} block — was being
     * streamed to the chat bubble unfiltered before that parsing ever ran.
     *
     * <p>Two things must be withheld, matching the two paths {@link #parseToolCall} itself
     * recognizes:
     * <ul>
     *   <li><b>Strict tagged form</b> — once {@code <tool_call>} (or any non-empty prefix of it
     *       at the end of the buffer, so the tag can't slip through split across two chunks) is
     *       seen, everything from that point on is withheld until {@code </tool_call>} closes,
     *       at which point the whole tagged block is dropped rather than forwarded.</li>
     *   <li><b>Untagged fallback form</b> — a model that drops the wrapper tags but still emits a
     *       bare {@code {"name": "<a tool actually offered this turn>", ...}} JSON object. This
     *       gate does not attempt to match the object's {@code name} against offered tools
     *       itself — that check is {@link #parseUntaggedToolCallMatchingKnownName}'s job, run
     *       separately on the complete response in {@code onComplete}. All this gate does is
     *       withhold conservatively from the first unclosed {@code {} onward so a false trailing
     *       fragment can't leak partial JSON into the chat; {@link #flush} forwards it as normal
     *       text if the full-response parse later decides it wasn't a real tool call after
     *       all.</li>
     * </ul>
     *
     * <p>Withheld text is never silently lost: if the final response turns out NOT to contain a
     * real tool call (tags never closed, or the untagged JSON's name didn't match anything
     * offered), {@link #flush} forwards the withheld tail so the user still sees the model's full
     * answer — this only suppresses text that really is tool-call machinery, never a false
     * positive that happens to contain a stray {@code {}.
     */
    private static final class ToolCallStreamGate {
        private final String openTag;
        private final java.util.function.Consumer<String> forward;
        private final StringBuilder held = new StringBuilder();
        /** True once a confirmed {@code <tool_call>} open tag has been seen — everything from
         *  there on is permanently withheld (this stream will never forward again), since the
         *  close tag is handled by dropping the whole block in {@link #flush}, not by resuming
         *  live forwarding mid-block. */
        private boolean insideTaggedBlock = false;

        ToolCallStreamGate(String openTag, java.util.function.Consumer<String> forward) {
            this.openTag = openTag;
            this.forward = forward;
        }

        void onDelta(String textDelta) {
            if (textDelta == null || textDelta.isEmpty()) return;
            held.append(textDelta);
            if (insideTaggedBlock) return; // fully withheld until flush() resolves it

            int openIdx = held.indexOf(openTag);
            if (openIdx >= 0) {
                // Confirmed tagged tool call starting at openIdx — forward everything before it,
                // withhold the rest permanently (flush() will drop it, not release it).
                if (openIdx > 0) forward.accept(held.substring(0, openIdx));
                held.delete(0, openIdx);
                insideTaggedBlock = true;
                return;
            }

            // No confirmed open tag yet. Two things could still be forming at the tail of the
            // buffer: (a) a partial "<tool_c..." prefix of the open tag split across chunks, or
            // (b) an untagged "{" that might grow into a fallback tool-call JSON object. Forward
            // everything up to the earliest such suspect position and hold the rest.
            int suspectIdx = earliestSuspectIndex(held);
            if (suspectIdx < 0) {
                forward.accept(held.toString());
                held.setLength(0);
            } else if (suspectIdx > 0) {
                forward.accept(held.substring(0, suspectIdx));
                held.delete(0, suspectIdx);
            }
            // else: suspectIdx == 0 — the whole held buffer starts at a suspect position, hold
            // it all and wait for more deltas (or flush()) to resolve it.
        }

        /** Smallest index in {@code buf} where either a partial prefix of {@link #openTag} or an
         *  unclosed {@code {} begins — i.e. the earliest point from which withholding must start
         *  because what follows might still turn into tool-call markup. Returns -1 if nothing in
         *  {@code buf} is suspect. */
        private int earliestSuspectIndex(StringBuilder buf) {
            int best = -1;
            // Partial prefix of the open tag at the very end of the buffer (e.g. buffer ends in
            // "<", "<t", "<tool_c", ...) — only the tail can be a split tag, so check suffixes.
            for (int len = Math.min(openTag.length() - 1, buf.length()); len >= 1; len--) {
                String suffix = buf.substring(buf.length() - len);
                if (openTag.startsWith(suffix)) {
                    best = buf.length() - len;
                    break;
                }
            }
            // Earliest unclosed "{" — conservatively suspect regardless of nesting depth, since
            // this gate only needs to know where to START withholding, not how to fully parse
            // the object (parseToolCall/extractBalancedJsonObject do that on flush()).
            int braceIdx = buf.indexOf("{");
            if (braceIdx >= 0 && (best < 0 || braceIdx < best)) {
                best = braceIdx;
            }
            return best;
        }

        /**
         * Called once generation finishes. Resolves whatever this gate is still holding:
         * <ul>
         *   <li>If a tagged block was confirmed ({@link #insideTaggedBlock}), its withheld text
         *       is dropped for good — it's real tool-call markup, already being handed to
         *       {@code parseToolCall} separately by the caller (on the complete response, not
         *       through this gate).</li>
         *   <li>Otherwise, whatever is held is either an untagged JSON object that turns out not
         *       to match an offered tool name once {@code parseToolCall} checks it (false alarm
         *       — forward it, it's just a JSON blob the model included in a normal answer) or an
         *       incomplete fragment (forward it too — nothing else will ever complete it now that
         *       generation is done).</li>
         * </ul>
         */
        void flush() {
            if (insideTaggedBlock) {
                held.setLength(0);
                return;
            }
            if (held.length() == 0) return;
            forward.accept(held.toString());
            held.setLength(0);
        }
    }
}
