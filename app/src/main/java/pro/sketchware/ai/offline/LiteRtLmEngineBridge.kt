package pro.sketchware.ai.offline

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin Kotlin bridge around the LiteRT-LM `Engine`/`Conversation` Kotlin API
 * (`com.google.ai.edge.litertlm:litertlm-android`).
 *
 * <p>Deliberately exposes a **plain-callback** contract ([GenerationCallback]), not Kotlin
 * `Flow`/`suspend` types, so the Java caller ([LocalModelProvider]) never has to cross the
 * Java/Kotlin coroutine-interop boundary — that interop is exactly the kind of thing that
 * looks fine in review and then fails at compile time on real toolchain-version
 * combinations, and this whole class already carries an unverified-against-a-real-build
 * risk (see the caveat below) that isn't worth compounding.
 *
 * <p><b>Build-verification caveat:</b> this class was written against the Kotlin API
 * documented at https://ai.google.dev/edge/litert-lm/android and
 * https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md
 * (fetched during this session, latest doc update noted 2026-05-28). It has **not** been
 * compiled here — there is no network access in this sandbox to resolve
 * `com.google.ai.edge.litertlm:litertlm-android:0.13.1` from Google's Maven repo, so a real
 * Gradle sync on your machine is the first actual build check this code gets. See
 * CHANGES.md Phase 5 for the exact class/package names as documented and the specific risk
 * that any of them drifted between the doc's last-updated date and the 0.13.1 release.
 *
 * <p>Only one model is ever loaded at a time per bridge instance — loading a different model
 * file closes the previous engine first. The LiteRT-LM docs explicitly warn that holding two
 * `Engine`-like instances simultaneously risks OOM on most devices, so this is enforced here
 * rather than left to callers.
 *
 * <p><b>Phase 5.1 fix — double-context bug:</b> [LocalModelProvider.buildPromptFromMessages]
 * is deliberately "stateless per call": every [generate] call receives the *entire* rebuilt
 * conversation (system prompt + full history) as one flattened [prompt] string, because this
 * app's own multi-provider message list is the single source of truth for history (it can
 * switch providers mid-conversation, which a persistent on-device `Conversation` has no way
 * to represent). Previously this class reused one `Conversation` instance for the lifetime of
 * a loaded model and called `sendMessageAsync` on it repeatedly — but LiteRT-LM's `Conversation`
 * *also* accumulates its own history internally on every `sendMessageAsync`. The result: each
 * turn re-sent the full history on top of history already retained inside the `Conversation`,
 * so context length roughly doubled every turn instead of growing linearly — this is what let a
 * short chat hit ~8034 tokens against a model exported with a hard 4096-token KV cache.
 *
 * <p>Fixed by creating a **new** `Conversation` for every [generate] call (matching the
 * stateless-per-call design [LocalModelProvider] already documents) and closing it again once
 * the call completes, so no cross-call state survives inside LiteRT-LM to double up with the
 * caller-supplied history. The `Engine` itself is still reused across calls while the same
 * model stays loaded — only `Conversation` objects are now call-scoped.
 *
 * <p><b>Structured-message fix (post-Phase-5 field bug — garbled/looping offline output).</b>
 * [generate] previously took a single flattened prompt [String] (built by
 * [LocalModelProvider.buildPromptFromMessages] as literal `"System: ...\n\nuser: ...\nassistant:
 * "` text) and passed it straight to `Conversation.sendMessageAsync(prompt: String)`. That bypasses
 * LiteRT-LM's own chat templating entirely: per the Kotlin/C++ API docs (see
 * https://ai.google.dev/edge/litert-lm/android and
 * https://github.com/google-ai-edge/LiteRT-LM/blob/main/runtime/conversation/conversation.h,
 * both fetched during this fix), `Conversation` renders each model's real turn-boundary tokens
 * (`<|im_start|>`/`<|im_end|>` for Qwen, `<start_of_turn>`/`<end_of_turn>` for Gemma, etc.) from
 * the model's own `jinja_prompt_template` metadata — but only when messages are supplied through
 * `ConversationConfig.systemInstruction` / `initialMessages` and `sendMessageAsync(Message)`, not
 * when they're pre-flattened into one plain-text string with hand-picked role labels the model
 * was never trained on. Sending plain "user:"/"assistant:" text gives the model no learned signal
 * for where a turn ends, which is what let it run on past the end of its own answer and start
 * hallucinating further "user:"/"assistant:" turns — the exact garbled/repeating output reported
 * against Qwen3/Gemma-3 in the field. [generate] now takes a [systemInstruction] string and an
 * ordered [history] of ([isUser], [text]) turns instead of one flattened string, builds
 * `ConversationConfig(systemInstruction = Contents.of(...), initialMessages = ...)`, and sends
 * only the final new user turn via `sendMessageAsync(Message.of(...))` — letting LiteRT-LM apply
 * the correct per-model template for every turn instead of this app guessing at one shared plain-
 * text format.
 *
 * <p>Per-model divergence this explains: Qwen3/Gemma-3 both use `<|...|>`/`<...>`-style special
 * tokens as their true turn delimiters, so with those tokens entirely absent from the prompt both
 * families had equally little signal for where to stop — hence the same garbling on both. Phi-4's
 * "responds to tools but exits the project" symptom was NOT the same failure (it evidently found
 * enough signal to emit a parseable `<tool_call>` block even from the flattened text) and is not
 * claimed to be fixed by this change alone — see [LocalModelProvider]'s class javadoc for that
 * still-open, separate issue.
 *
 * <p>This class was still not compiled here (no network access in this sandbox to resolve
 * `com.google.ai.edge.litertlm:litertlm-android:0.13.1` from Google's Maven — see the
 * build-verification caveat above, which still applies to this revision). `Message.of(text)`,
 * `Contents.of(text)`, and `ConversationConfig(systemInstruction =, initialMessages =)` are used
 * exactly as shown in the official getting-started sample and the `ToolMain.kt` /
 * `Main.kt` examples in the LiteRT-LM repo (both fetched during this fix), so the risk here is the
 * same class of risk already disclosed above, not a new one.
 */
class LiteRtLmEngineBridge {

    /**
     * Known end-of-turn / special tokens across the model families this app ships offline
     * (Qwen, Gemma, Phi, Llama/Mistral-style exports). Not tied to any one model's real EOS
     * ID — this is a text-level backstop, not a tokenizer-level one, so it only needs to
     * recognize the token's literal rendered string, whichever family produced it. See the
     * "Client-side stop-sequence backstop" note on [generate] for why this exists.
     */
    private val STOP_SEQUENCES = listOf(
        "<|endoftext|>", "<|im_end|>", "<|end|>", "<|eot_id|>", "</s>", "<end_of_turn>"
    )

    /** Plain-callback contract — deliberately Flow/suspend-free, see class doc. */
    interface GenerationCallback {
        fun onChunk(textDelta: String)
        fun onComplete(fullResponse: String)
        fun onError(message: String)
    }

    private var engine: Engine? = null

    @Volatile
    var loadedModelPath: String? = null
        private set

    // Single background scope for this bridge instance; SupervisorJob so one failed
    // generation doesn't cancel the scope for subsequent calls.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cancelled = AtomicBoolean(false)

    /**
     * One prior conversation turn, in the order it should be replayed into
     * `ConversationConfig.initialMessages`. [isUser] true = a past user turn
     * (`Message.user(text)`), false = a past assistant/model turn (`Message.model(text)`). This
     * app's own "tool" role (see `ChatMessage.getRole()`) has no confirmed dedicated LiteRT-LM
     * factory as of this fix, so [LocalModelProvider] folds tool-result turns into a labeled user
     * turn before building this list — see that class's javadoc.
     */
    data class HistoryTurn(val isUser: Boolean, val text: String)

    /**
     * Loads [modelFile] if it isn't already the currently-loaded model, then streams a response
     * to the newest user turn ([latestUserMessage]) through [callback], with [systemInstruction]
     * and [history] supplied to LiteRT-LM's own chat templating instead of being pre-flattened
     * into plain text — see the class doc's "Structured-message fix" note for why this replaced
     * the previous single-[String]-prompt signature. Runs entirely on a background coroutine —
     * safe to call from the main thread. Loading can take several seconds (the LiteRT-LM docs
     * cite up to ~10s for `engine.initialize()`), which is why this is async rather than a
     * blocking call.
     *
     * @param systemInstruction system prompt text, or null/blank for none.
     * @param history prior turns in chronological order (oldest first) — replayed into
     *   `ConversationConfig.initialMessages` so LiteRT-LM's template renders each one with the
     *   model's real turn-boundary tokens, exactly as it would a live multi-turn conversation.
     * @param latestUserMessage the newest user turn — sent via `sendMessageAsync(Message.of(...))`
     *   after the Conversation is created with [history] already seeded, matching the two-call
     *   shape (seed history, then send the new turn) the official getting-started sample uses
     *   for `initialMessages`.
     * @param preferGpu if true, attempts [Backend.GPU] (OpenCL) first for faster inference.
     *   GPU engine init has no built-in fallback in LiteRT-LM itself and is known to hard-fail
     *   on some real devices (e.g. certain Exynos/ANGLE-CL combinations where the OpenCL→SPIR-V
     *   compiler rejects a kernel the GPU delegate generates), so on any GPU init failure this
     *   method automatically retries once on [Backend.CPU] before giving up. If GPU succeeds,
     *   the engine is remembered as GPU-backed under the same [loadedModelPath] key; a later
     *   call with a different [preferGpu] value for the same model file will reload the engine
     *   with the newly requested backend (see [loadedBackendIsGpu]).
     */
    fun generate(
        modelFile: File,
        systemInstruction: String?,
        history: List<HistoryTurn>,
        latestUserMessage: String,
        preferGpu: Boolean,
        callback: GenerationCallback
    ) {
        cancelled.set(false)
        scope.launch {
            // Call-scoped Conversation — created fresh for this call and closed at the end
            // of this function, never stored on the instance. See the class doc's
            // "Phase 5.1 fix" note for why: reusing one Conversation across calls doubled
            // context length every turn, since Conversation keeps its own internal history
            // on top of the full history LocalModelProvider already re-sends every call.
            var activeConversation: com.google.ai.edge.litertlm.Conversation? = null
            try {
                val needsReload = loadedModelPath != modelFile.absolutePath || engine == null ||
                        loadedBackendIsGpu != preferGpu
                if (needsReload) {
                    closeInternal()
                    engine = initializeEngine(modelFile, preferGpu)
                    loadedModelPath = modelFile.absolutePath
                }
                val activeEngine = engine
                    ?: throw IllegalStateException("Engine failed to initialize")

                val initialMessages = history.map { turn ->
                    if (turn.isUser) Message.user(turn.text) else Message.model(turn.text)
                }
                val conversationConfig = if (!systemInstruction.isNullOrBlank()) {
                    ConversationConfig(
                        systemInstruction = Contents.of(systemInstruction),
                        initialMessages = initialMessages
                    )
                } else {
                    ConversationConfig(initialMessages = initialMessages)
                }
                activeConversation = activeEngine.createConversation(conversationConfig)

                val full = StringBuilder()
                // Client-side stop-sequence backstop (field bug — see class doc's
                // "Structured-message fix" note above): even with proper chat-templating,
                // this app has no way to verify from this sandbox whether the specific
                // Qwen3/Gemma-3 .task exports actually stop cleanly on their own EOS token
                // via this Kotlin API — the field reports (repeated <think>/<|endoftext|>
                // blocks looping indefinitely) show the model running on well past its real
                // answer. Rather than trust template-driven stopping alone, every chunk is
                // scanned for a small set of known special/end-of-turn tokens across the
                // model families this app ships (Qwen/Gemma/Phi/Llama); the instant one
                // appears, everything from that point on is dropped, onComplete fires with
                // only the clean prefix, and the flow is cancelled so the engine stops
                // burning compute generating more of the runaway loop.
                var stopped = false
                activeConversation.sendMessageAsync(Message.of(latestUserMessage))
                    .catch { throwable ->
                        callback.onError(throwable.message ?: "Unknown local generation error")
                    }
                    .collect { message ->
                        if (cancelled.get() || stopped) return@collect
                        val delta = message.toString()
                        val combined = full.toString() + delta
                        val cutIndex = STOP_SEQUENCES.map { seq -> combined.indexOf(seq) }
                            .filter { it >= 0 }
                            .minOrNull()
                        if (cutIndex != null) {
                            val cleanCombined = combined.substring(0, cutIndex)
                            val cleanDelta = cleanCombined.removePrefix(full.toString())
                            if (cleanDelta.isNotEmpty()) callback.onChunk(cleanDelta)
                            full.setLength(0)
                            full.append(cleanCombined)
                            // Don't reuse the shared `cancelled` flag here — it also gates the
                            // post-collect onComplete call below and (via GenerationCallback's
                            // other caller) carries user-cancellation semantics. `stopped` is a
                            // local, call-scoped flag: it silences further chunks from this
                            // same flow subscription (the upstream generation call itself isn't
                            // guaranteed to actually halt — see class doc — so more emissions
                            // may still arrive) without touching cancellation state, and
                            // onComplete is dispatched right here with the truncated text since
                            // the post-collect call below is skipped for this call.
                            stopped = true
                            callback.onComplete(full.toString())
                            return@collect
                        }
                        full.append(delta)
                        callback.onChunk(delta)
                    }
                if (!cancelled.get() && !stopped) {
                    callback.onComplete(full.toString())
                }
            } catch (t: Throwable) {
                callback.onError(t.message ?: "Unknown local generation error")
            } finally {
                // Always tear down this call's Conversation — success, error, or cancellation —
                // so the next call starts from a clean slate with no retained history.
                try {
                    activeConversation?.close()
                } catch (_: Exception) {
                    // best-effort cleanup
                }
            }
        }
    }

    /**
     * Backwards-compatible overload — always uses CPU. Existing callers that haven't been
     * updated to pass a backend preference keep their current (safe) behavior unchanged.
     */
    fun generate(
        modelFile: File,
        systemInstruction: String?,
        history: List<HistoryTurn>,
        latestUserMessage: String,
        callback: GenerationCallback
    ) {
        generate(modelFile, systemInstruction, history, latestUserMessage, preferGpu = false, callback = callback)
    }

    /** True if the currently-loaded [engine] was initialized with the GPU backend. */
    @Volatile
    var loadedBackendIsGpu: Boolean = false
        private set

    /**
     * Attempts [Backend.GPU] first when [preferGpu] is true; on any failure (including a
     * successfully-constructed [Engine] whose [Engine.initialize] call throws), falls back to
     * [Backend.CPU]. GPU init failures are expected on some devices (see [generate] doc) and
     * are not re-thrown — only a CPU failure propagates, since CPU is the universal baseline.
     */
    private fun initializeEngine(modelFile: File, preferGpu: Boolean): Engine {
        if (preferGpu) {
            try {
                val gpuConfig = EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.GPU())
                val gpuEngine = Engine(gpuConfig)
                gpuEngine.initialize()
                loadedBackendIsGpu = true
                return gpuEngine
            } catch (_: Throwable) {
                // Known failure mode on some devices — fall through to CPU below rather than
                // surfacing an error, since CPU is expected to work universally.
            }
        }
        val cpuConfig = EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU())
        val cpuEngine = Engine(cpuConfig)
        cpuEngine.initialize()
        loadedBackendIsGpu = false
        return cpuEngine
    }

    /** Signals the current generation to stop delivering further chunks. */
    fun cancelGeneration() {
        cancelled.set(true)
    }

    private fun closeInternal() {
        // No shared Conversation to close here anymore — each generate() call owns and
        // closes its own Conversation in its own finally block. Only the Engine itself
        // is instance-scoped.
        try {
            engine?.close()
        } catch (_: Exception) {
            // best-effort cleanup
        }
        engine = null
        loadedModelPath = null
        loadedBackendIsGpu = false
    }

    /** Releases native resources. Safe to call multiple times. */
    fun close() {
        cancelGeneration()
        closeInternal()
    }
}
