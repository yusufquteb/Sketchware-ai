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
 * {@link AiApiClient} implementation that runs entirely on-device via {@link LiteRtLmEngineBridge}
 * — no network request is made for chat generation. Implements the same abstract contract every
 * cloud provider client implements (verified against the actual {@code AiApiClient.java} in this
 * archive before writing this class — see CHANGES.md Phase 5) so it drops into the existing
 * send path in {@code AiClientFactory} without changes anywhere else in the chat pipeline.
 *
 * <p><b>Context/Token MVP — tool calling re-enabled for the local model, essential subset only.</b>
 * Phase 5.4 disabled tools entirely because Phase 5.1's approach (reformat the live
 * {@code ToolRegistry} — 106 registered tools as of that phase — into a compact prompt block on
 * every call) was fundamentally incompatible with a 4096-token model: even a minimal
 * one-line-per-tool format for all 106 tools alone landed in the 8,000–11,000-token range,
 * confirmed from an actual field failure where a single "hi" message produced an estimated
 * ~11203 prompt tokens. Rather than sending the full registry, {@code AgentExecutor} now sends
 * only the 7-tool essential subset ({@code ToolRegistry.getEssentialTools()}), so {@link
 * #buildToolBlock} builds a small tool block from whatever it's given and its measured token
 * cost is reserved in the history budget the same way the system prompt's is (see {@link
 * #buildPromptAssembly}). {@link #parseToolCall}, {@code TOOL_CALL_OPEN_TAG}/{@code
 * TOOL_CALL_CLOSE_TAG}, and the {@code <tool_call>...</tool_call>} convention — previously kept
 * as dead-but-harmless — are now live again for this provider.
 *
 * <p><b>Phase 5.1 — history trimming</b>: {@link LiteRtLmEngineBridge} now creates a fresh
 * LiteRT-LM {@code Conversation} for every {@link #sendChatRequest} call instead of reusing one
 * for the lifetime of the loaded model (that reuse was the actual cause of context roughly
 * doubling every turn — see that class's javadoc). Fixing that stops the doubling, but the
 * model file is still hard-capped at a fixed KV-cache size baked in at export time (encoded in
 * the filename, e.g. {@code ekv4096} = 4096 tokens — see {@link LocalModelCatalog}; this is not
 * a runtime-configurable value). A long-running conversation still needs its own history capped
 * well under that ceiling, since {@link #buildPromptAssembly} keeps re-sending the full
 * history every call by design (see that method's javadoc). {@link #trimHistoryForLocalModel}
 * does that: it keeps the most recent messages that fit inside a conservative token budget,
 * estimated with {@link TokenBudgetChecker#estimateTokens} — the same char-count heuristic
 * already used elsewhere in this codebase for payload-size guards, so this isn't a new,
 * unverified estimation method.
 *
 * <p><b>Structured-message fix (field bug — garbled/looping offline output).</b> {@link
 * #buildPromptAssembly} previously flattened the whole conversation into one plain-text string
 * ({@code "System: ...\n\nuser: ...\nassistant: "}) that {@link LiteRtLmEngineBridge} sent
 * straight to {@code Conversation.sendMessageAsync(String)}. That bypassed LiteRT-LM's own chat
 * templating — the model never saw its real turn-boundary tokens (e.g. Qwen's {@code <|im_start|>}/
 * {@code <|im_end|>}, Gemma's {@code <start_of_turn>}/{@code <end_of_turn>}), so it had no learned
 * signal for where to stop and kept generating further hallucinated "user:"/"assistant:" turns —
 * the garbled, looping output reported against Qwen3 and Gemma-3 in the field. Fixed by having
 * {@link #buildPromptAssembly} return a {@link PromptAssembly} (system instruction + structured
 * {@link LiteRtLmEngineBridge.HistoryTurn} list + the newest user message, all still trimmed by
 * {@link #trimHistoryForLocalModel} exactly as before) instead of one string, and having {@link
 * LiteRtLmEngineBridge#generate} pass that through {@code ConversationConfig.systemInstruction} /
 * {@code initialMessages} and a final {@code sendMessageAsync(Message.of(...))} call — see that
 * class's "Structured-message fix" javadoc for the full evidence trail. This is the same class of
 * fix for both model families, since both use special-token turn delimiters this app was
 * previously sending as plain text.
 *
 * <p><b>Still open — Phi-4 "exits the project" after a tool call.</b> Reported separately from the
 * garbling above: Phi-4 evidently found enough signal in the old flattened prompt to emit a
 * parseable {@code <tool_call>} block (unlike Qwen3/Gemma-3), so it was not hitting the same
 * template-mismatch failure — this fix is not claimed to resolve that report. The reported
 * symptom (leaving/closing the project after a tool call completes) points at either the
 * tool-result being fed back in a shape Phi-4's own template doesn't expect, or something in how
 * {@code sc_id}/project scope is threaded through {@link AgentExecutor}'s shared tool-execution
 * loop once a call originates from this provider — neither has been isolated yet and needs its
 * own field reproduction now that the garbling is no longer a confound.
 *
 * <p><b>fetchModels()</b>: there is no network models-list endpoint for a local engine, so this
 * returns the single currently-selected {@link LocalModelCatalog} entry as one {@link ModelInfo}.
 *
 * <p><b>Not tested end-to-end</b>: no Android device/emulator is available in this environment.
 * See CHANGES.md Phase 5 for the explicit list of what still needs field verification. The
 * structured-message fix above carries the same not-yet-compiled caveat as {@link
 * LiteRtLmEngineBridge} — see that class's javadoc.
 */
public class LocalModelProvider extends AiApiClient {

    private final Context appContext;
    private final LocalModelManager modelManager;
    private final LiteRtLmEngineBridge engineBridge;
    /** Persistent project rules/env/tool notes — see class javadoc for why this exists
     *  separately from the trimmed chat history (see {@link #buildPromptAssembly}). */
    private final pro.sketchware.ai.offline.knowledge.KnowledgeStore knowledgeStore;

    /** Tags of requests the caller has asked to cancel — checked between chunks. */
    private final CopyOnWriteArraySet<Object> cancelledTags = new CopyOnWriteArraySet<>();

    // ── History / prompt budget ─────────────────────────────────────────────
    //
    // The model file is hard-capped at HARD_KV_CACHE_TOKENS (see class javadoc — this is
    // baked into the exported .task file, not a config value this app controls). Reserve a
    // slice for the model's own output, and fill the rest with the most recent history.
    // Deliberately conservative: TokenBudgetChecker's estimator is a coarse 4-chars-per-token
    // heuristic (see its own javadoc), and under-filling the true 4096-token cache is far safer
    // than an engine-level failure from overfilling it.
    //
    // Context/Token MVP: the tool-block reservation is back, but only for the small 7-tool
    // essential subset (see class javadoc) rather than the full 106+-tool registry Phase 5.4
    // found unaffordable. Both the system prompt's and the tool block's real measured sizes are
    // subtracted from the history budget in buildPromptAssembly.
    private static final int HARD_KV_CACHE_TOKENS = 4096;
    private static final int RESERVED_FOR_OUTPUT_TOKENS = 512;
    /** Safety margin subtracted before the final pre-flight check, on top of the
     *  RESERVED_FOR_OUTPUT_TOKENS already carved out — covers estimator error, since
     *  TokenBudgetChecker's char-count heuristic is approximate, not exact. */
    private static final int FINAL_CHECK_SAFETY_MARGIN_TOKENS = 64;
    /** Max size of the persistent-knowledge block (project rules/env/tool notes) — kept
     *  deliberately small since it competes with chat history for the same tight 4096-token
     *  cache. CRITICAL entries are still included in full even past this cap (see
     *  KnowledgeBlockBuilder javadoc) so a rule the user marked critical is never silently
     *  dropped; this cap mainly limits how many NORMAL (relevance-matched) entries get pulled in. */
    private static final int MAX_KNOWLEDGE_BLOCK_TOKENS = 300;

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
        this.engineBridge = new LiteRtLmEngineBridge();
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
                4096, // conservative context estimate; catalog file names encode ekv4096
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
        LocalModelCatalog requested = modelId != null ? LocalModelCatalog.fromId(modelId) : null;
        LocalModelCatalog selected = requested != null ? requested : modelManager.getSelectedModel();
        if (modelManager.getState(selected) != LocalModelState.READY) {
            handler.onError("Model \"" + selected.getDisplayName()
                    + "\" isn't downloaded on this device yet — download it first from AI Settings.");
            return;
        }

        PromptAssembly assembly = buildPromptAssembly(messages, systemPrompt, tools);

        // Phase 5.3 — final pre-flight check: buildPromptAssembly already trims history to a
        // computed budget, but that budget is itself an estimate (char-count heuristic) and the
        // always-kept newest message can alone exceed it (see trimHistoryForLocalModel).
        // Measuring the *actual* assembled content one last time — right before it reaches the
        // engine — catches whatever the budgeting step could still miss, so the user gets a
        // clear in-app message instead of the engine's raw "Input token ids are too long" error.
        // Estimated the same way regardless of the structured-message rewrite: summing every
        // piece's own text length is equivalent to estimating the old flattened string, since
        // TokenBudgetChecker's estimator is a plain character count with no per-call overhead.
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

        engineBridge.generate(modelFile, assembly.systemInstruction, assembly.history,
                assembly.latestUserMessage, modelManager.isGpuBackendPreferred(),
                new LiteRtLmEngineBridge.GenerationCallback() {
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
     * Holds the structured pieces {@link LiteRtLmEngineBridge#generate} needs, replacing the
     * single flattened prompt string this method used to return — see that class's
     * "Structured-message fix" javadoc for why a flattened string was the actual root cause of
     * the garbled/looping offline output reported in the field (Qwen3/Gemma-3: no learned
     * turn-boundary signal in plain "user:"/"assistant:" text, so generation ran past the end of
     * its own answer). {@code history} holds every trimmed message except the newest, which is
     * always sent separately as {@code latestUserMessage} — matching
     * {@code ConversationConfig.initialMessages} + a final {@code sendMessageAsync} call, per the
     * official getting-started sample. Despite the field's name, this is not always literally a
     * "user" turn: {@code AgentExecutor}'s multi-iteration tool-calling loop can re-call
     * {@code sendChatRequest} with the conversation ending on a "tool" role message (the result of
     * the previous iteration's tool call) — see {@link #buildPromptAssembly} for how that's
     * labeled before being sent.
     */
    private static final class PromptAssembly {
        @Nullable final String systemInstruction;
        @NonNull final List<LiteRtLmEngineBridge.HistoryTurn> history;
        @NonNull final String latestUserMessage;
        final int estimatedTokens;

        PromptAssembly(@Nullable String systemInstruction,
                        @NonNull List<LiteRtLmEngineBridge.HistoryTurn> history,
                        @NonNull String latestUserMessage, int estimatedTokens) {
            this.systemInstruction = systemInstruction;
            this.history = history;
            this.latestUserMessage = latestUserMessage;
            this.estimatedTokens = estimatedTokens;
        }

        int estimatedTotalTokens() {
            return estimatedTokens;
        }
    }

    /**
     * Builds the structured system instruction + history turns for a call, replacing the old
     * single-flattened-prompt-string approach. This phase keeps generation stateless per call —
     * each request re-sends full context — rather than trying to keep a long-lived LiteRT-LM
     * {@code Conversation} in sync with this app's own multi-provider message list (which can
     * switch providers mid-conversation, something a persistent on-device conversation object has
     * no way to represent). See {@link LiteRtLmEngineBridge}'s "Phase 5.1 fix" javadoc for why a
     * fresh {@code Conversation} per call is required to keep this assumption true at the
     * LiteRT-LM layer too, and its "Structured-message fix" javadoc for why the prompt itself is
     * now built as {@code systemInstruction}/{@code initialMessages}/{@code latestUserMessage}
     * instead of one flattened string with hand-picked role-label text the model was never
     * trained to recognise as a real turn boundary.
     *
     * <p><b>Context/Token MVP — small tool block reinstated.</b> {@code tools} is expected to be
     * the 7-tool essential subset ({@code ToolRegistry.getEssentialTools()}), not the full
     * 106+-tool registry Phase 5.4 found unaffordable (an all-106 compact block alone landed in
     * the 8,000–11,000-token range — confirmed from an actual failure where a single "hi"
     * message reported ~11203 estimated tokens against the 3520-token pre-flight limit). See
     * {@link #buildToolBlock} for how the block itself is built. It's folded into
     * {@code systemInstruction} (standing instructions for the whole conversation) rather than
     * appearing as a fake extra "turn" in {@code initialMessages} — a tool catalog is not
     * something either party "said," and putting it in {@code systemInstruction} keeps every
     * entry in {@code initialMessages} an actual past user/assistant turn, which is what lets
     * LiteRT-LM's template render them with the model's real turn-boundary tokens.
     *
     * <p><b>Persistent knowledge block.</b> Project rules/env/tool notes stored in {@link
     * #knowledgeStore} are folded into {@code systemInstruction} the same way, budgeted the same
     * way {@code toolBlock} is — see {@link pro.sketchware.ai.offline.knowledge.KnowledgeBlockBuilder}.
     * This exists precisely because {@link #trimHistoryForLocalModel} below drops the *oldest*
     * chat messages first: a rule stated early in a long conversation would otherwise be exactly
     * what gets trimmed away. Reading this block from a store outside {@code messages} means it
     * survives regardless of how much history is still in the trimming window.
     *
     * <p>Because this is resent on every single call, history is trimmed to what fits the
     * remaining budget after the system instruction (system prompt + tool block + knowledge
     * block) and output reserve are accounted for (see {@link #trimHistoryForLocalModel}).
     */
    private PromptAssembly buildPromptAssembly(List<ChatMessage> messages, String systemPrompt,
                                                List<ToolDefinition> tools) {
        int systemPromptTokens = TokenBudgetChecker.estimateTokens(systemPrompt);
        String toolBlock = buildToolBlock(tools);
        int toolBlockTokens = TokenBudgetChecker.estimateTokens(toolBlock);

        // Rank NORMAL knowledge entries against the newest user message — the same signal
        // trimHistoryForLocalModel always keeps whole, so relevance and "what's guaranteed to
        // survive trimming" point at the same message.
        //
        // RFC-001 review, recommendation 1: routed through KnowledgeRetriever rather than
        // calling KnowledgeBlockBuilder directly, per KnowledgeRetriever's own javadoc — "both
        // the online path (AgentExecutor.buildCompactSystemPrompt) and the offline path
        // (LocalModelProvider.buildPromptFromMessages) are expected to call this same method
        // once wired in, so the two engines never drift into two different notions of what the
        // assistant knows." pageContext isn't currently threaded into sendChatRequest's
        // signature (it would require changing the AiApiClient interface every provider
        // implements, out of scope here), so this passes null for it exactly as the prior direct
        // KnowledgeBlockBuilder.build() call did — behavior for this call site is unchanged,
        // only the entry point used to reach it.
        String latestUserMessage = latestMessageContent(messages);
        pro.sketchware.ai.offline.knowledge.KnowledgeBlockBuilder.Result knowledgeResult =
                pro.sketchware.ai.offline.knowledge.KnowledgeRetriever.buildContextBlock(
                        knowledgeStore, latestUserMessage, /* pageContext= */ null,
                        MAX_KNOWLEDGE_BLOCK_TOKENS);
        String knowledgeBlock = knowledgeResult.block;
        int knowledgeBlockTokens = knowledgeResult.estimatedTokens;

        int historyBudget = HARD_KV_CACHE_TOKENS - RESERVED_FOR_OUTPUT_TOKENS
                - systemPromptTokens - toolBlockTokens - knowledgeBlockTokens;
        // Guard against a pathologically large system prompt (or tool block, or a knowledge
        // block dominated by CRITICAL entries — see that builder's javadoc on why CRITICAL
        // entries are never trimmed) alone consuming the whole cache — trimHistoryForLocalModel
        // still always keeps (truncated if needed) the single newest message, so a
        // zero-or-negative budget here just means "keep only that newest message, as small as
        // it can be made."
        if (historyBudget < 0) historyBudget = 0;

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

        List<ChatMessage> trimmed = trimHistoryForLocalModel(messages, historyBudget);

        // The newest message is always sent as latestUserMessage (see PromptAssembly doc), so
        // it's excluded from initialMessages here even though trimHistoryForLocalModel keeps it
        // as the last element of `trimmed`.
        List<LiteRtLmEngineBridge.HistoryTurn> history = new ArrayList<>();
        int historyTokens = 0;
        String newestContent = "";
        for (int i = 0; i < trimmed.size(); i++) {
            ChatMessage m = trimmed.get(i);
            String content = m.getContent() != null ? m.getContent() : "";
            historyTokens += TokenBudgetChecker.estimateTokens(content);

            // "tool" is this app's own third role (see ChatMessage's constructors) with no
            // confirmed dedicated LiteRT-LM factory as of this fix (only Message.user/Message.model
            // are documented) — folded into a labeled user turn so the model still sees the tool
            // result as content, rather than guessing at an unconfirmed API shape.
            boolean isUser = !"assistant".equals(m.getRole());
            String text = "assistant".equals(m.getRole()) ? content
                    : "tool".equals(m.getRole()) ? "[Tool result: " + m.getToolName() + "] " + content
                    : content;

            if (i == trimmed.size() - 1) {
                // Newest turn — sent separately via sendMessageAsync, not added to `history`.
                // AgentExecutor's multi-iteration tool-calling loop can re-call sendChatRequest
                // with the list ending on a "tool" role message (the result of the previous
                // iteration's tool call), not only a "user" message — so this uses the same
                // role-labeled text as the history branch above, rather than assuming the newest
                // turn is always literal user text.
                newestContent = text;
                continue;
            }
            history.add(new LiteRtLmEngineBridge.HistoryTurn(isUser, text));
        }

        int totalTokens = systemPromptTokens + toolBlockTokens + knowledgeBlockTokens + historyTokens;
        return new PromptAssembly(systemInstruction, history, newestContent, totalTokens);
    }

    /** Content of the last message in {@code messages}, or null — used only to rank knowledge
     *  entries by relevance, never to decide what's kept in history (that's still
     *  {@link #trimHistoryForLocalModel}'s job, unchanged). */
    @Nullable
    private String latestMessageContent(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return null;
        return messages.get(messages.size() - 1).getContent();
    }

    /**
     * Builds a compact, one-line-per-tool prompt block for the essential tool subset
     * (see {@code ToolRegistry.getEssentialTools()} — 7 tools as of the Context/Token MVP fix,
     * versus the full 106+-tool catalog this class's Phase 5.4 javadoc describes as unaffordable).
     * Seven short schemas are small enough to plausibly coexist with a short conversation inside
     * the 4096-token KV cache, unlike the full registry. Returns an empty string when {@code tools}
     * is null or empty so callers can skip adding the block entirely.
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
     * Keeps the most recent messages that fit within {@code budgetTokens}, dropping the oldest
     * ones first. Walks from the newest message backwards so the cut always falls at the older
     * end of the conversation, then restores chronological order.
     *
     * <p>The history budget accounts for both the system prompt and the (now small, 7-tool)
     * tool block — see {@link #buildPromptAssembly}. In practice this still leaves most
     * single-turn exchanges with the current message plus one or more prior turns; the
     * truncation path below only engages when a single message is large enough to matter on its
     * own (e.g. a pasted file), not for ordinary short chat turns.
     *
     * <p>The newest message is always kept, truncated to fit {@code budgetTokens} when it alone
     * exceeds it, instead of being kept whole or dropped — see {@link #truncateToBudget}.
     *
     * <p>This still does not summarise or rewrite anything for messages that fit — dropped
     * older messages are simply omitted for this call, exactly like a context window naturally
     * would. {@code TokenOptimizer}'s richer summarisation pipeline is intentionally not reused
     * here: it's tuned for cloud-provider context windows (tens of thousands of tokens) and this
     * is a much smaller, purely local-model concern, per this phase's scope.
     */
    private List<ChatMessage> trimHistoryForLocalModel(List<ChatMessage> messages, int budgetTokens) {
        List<ChatMessage> result = new ArrayList<>();
        if (messages == null || messages.isEmpty()) return result;

        int budgetRemaining = budgetTokens;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            int cost = TokenBudgetChecker.estimateTokens(m.getContent());

            if (result.isEmpty() && cost > budgetRemaining) {
                // The single newest message alone exceeds the budget. Keep it — dropping it
                // entirely would silently discard the user's latest turn — but truncate its
                // content to fit, rather than sending it whole and letting the engine reject
                // the request outright.
                result.add(0, truncateToBudget(m, Math.max(budgetRemaining, 0)));
                break;
            }
            if (!result.isEmpty() && cost > budgetRemaining) {
                // Stop once the budget is exhausted for any older message; the newest one is
                // already safely in `result` from a prior iteration.
                break;
            }
            result.add(0, m);
            budgetRemaining -= cost;
        }
        return result;
    }

    /**
     * Returns a copy of {@code message} with its content truncated to roughly fit
     * {@code budgetTokens} (via {@link TokenBudgetChecker}'s char-per-token estimate), keeping
     * the tail of the content rather than the head. The original {@code message} object passed
     * in by the caller is never mutated — {@code messages} is owned by the caller (e.g. the
     * conversation's persisted history) and may still be sent to a different provider later in
     * the same session (see {@link #buildPromptAssembly} class-level rationale on
     * per-provider stateless prompts), so mutating it in place here would leak a local-model-only
     * truncation into that shared list.
     *
     * <p>A budget of 0 or less still keeps a small tail rather than an empty string, so the
     * model always sees at least a fragment of the user's actual latest message instead of
     * nothing.
     */
    private ChatMessage truncateToBudget(ChatMessage message, int budgetTokens) {
        String content = message.getContent();
        if (content == null || content.isEmpty()) return message;

        int maxChars = Math.max(budgetTokens, 1) * 4; // inverse of TokenBudgetChecker's 4-chars-per-token estimate
        if (content.length() <= maxChars) return message;

        String truncated = "…(earlier part of this message was trimmed to fit the on-device model)…\n"
                + content.substring(content.length() - maxChars);

        ChatMessage copy = new ChatMessage(message.getConversationId(), truncated);
        return copy;
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
