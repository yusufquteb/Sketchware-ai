package pro.sketchware.ai.offline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin Kotlin bridge around the vendored llama.cpp JNI module (`:llama`, sourced from
 * `ggml-org/llama.cpp`'s `examples/llama.android`), replacing [LiteRtLmEngineBridge].
 *
 * <p><b>NOT VERIFIED AGAINST A REAL BUILD.</b> This class was written to the plan approved in
 * this session (LiteRT-LM → llama.cpp migration, see the plan's context for why: LiteRT-LM's
 * `.litertlm` catalog has a 4096-token KV cache baked into the file at export time, which is
 * the root cause of the offline path getting only the smallest tool subset). It has three
 * unmet prerequisites that this sandbox cannot satisfy:
 * <ol>
 *   <li>No Android NDK is installed here — this file has never been compiled.</li>
 *   <li>github.com is blocked by this session's egress policy — the `:llama` Gradle module
 *       (the actual `examples/llama.android` JNI wrapper + CMakeLists.txt this class calls
 *       into) has NOT been vendored into the project. The `LlamaNative` object below declares
 *       the `external fun` surface this bridge expects, matching the method names used by
 *       upstream's own `LLamaAndroid.kt` wrapper as of this class's writing — but that surface
 *       must be checked against the actual vendored source once network access allows cloning
 *       it, exactly as [LiteRtLmEngineBridge]'s own class doc discloses for its Kotlin API.</li>
 *   <li>huggingface.co is blocked here too — no GGUF model has actually been downloaded and
 *       loaded through this bridge.</li>
 * </ol>
 * Treat every native method signature below as a best-effort draft, not a confirmed contract,
 * until someone with NDK + GitHub + Hugging Face access builds and runs this for real.
 *
 * <p>Mirrors [LiteRtLmEngineBridge]'s external contract deliberately — same
 * [GenerationCallback] shape, same [HistoryTurn] data class, same plain-callback (no
 * Flow/suspend leaking to the Java caller) design — so [LocalModelProvider] only needs to swap
 * which bridge it constructs, not how it talks to one. See that class's doc for why the
 * plain-callback boundary matters (Java/Kotlin coroutine interop is a real source of
 * build-time breakage, and this class already carries enough unverified risk).
 *
 * <p><b>Context window:</b> unlike LiteRT-LM's file-baked `ekv4096`, llama.cpp takes context
 * size as a load-time parameter (`n_ctx`). Per the approved plan this is fixed at
 * [CONTEXT_SIZE_TOKENS] = 8192 for every model in v1 — no GPU backend, no per-device tiering
 * (both deferred, see the plan).
 *
 * <p><b>Chat templating:</b> llama.cpp applies the chat template embedded in the GGUF file's
 * own metadata (`tokenizer.chat_template`) via `llama_chat_apply_template` — the same
 * "engine renders the model's real turn-boundary tokens, the app never hand-rolls them" design
 * [LiteRtLmEngineBridge] relies on today, so [LocalModelProvider]'s prompt-assembly code does
 * not need to change how it builds history/system-prompt structure, only which bridge renders
 * it.
 */
class LlamaCppEngineBridge {

    companion object {
        /** Fixed context size for every model in v1 of this engine — see class doc. */
        const val CONTEXT_SIZE_TOKENS = 8192

        /**
         * Same defensive stop-sequence backstop [LiteRtLmEngineBridge] uses — llama.cpp is
         * expected to stop cleanly on the GGUF's own EOS token via `llama_vocab_is_eog`, but
         * this app has no way to verify that from this sandbox for every quantized export in
         * the catalog, so the same client-side text scan is kept as a safety net rather than
         * trusting engine-side stopping alone on the first real device this runs on.
         */
        private val STOP_SEQUENCES = listOf(
            "<|endoftext|>", "<|im_end|>", "<|end|>", "<|eot_id|>", "</s>", "<end_of_turn>"
        )
    }

    /** Plain-callback contract — see class doc for why this stays Flow/suspend-free. */
    interface GenerationCallback {
        fun onChunk(textDelta: String)
        fun onComplete(fullResponse: String)
        fun onError(message: String)
    }

    /**
     * One prior conversation turn. Same shape/semantics as
     * [LiteRtLmEngineBridge.HistoryTurn] — [isUser] true = past user turn, false = past
     * assistant turn. [LocalModelProvider] builds this list identically for either bridge.
     */
    data class HistoryTurn(val isUser: Boolean, val text: String)

    /** Opaque handle to the loaded native model+context, or null if nothing is loaded. */
    private var nativeHandle: Long = 0L

    @Volatile
    var loadedModelPath: String? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cancelled = AtomicBoolean(false)

    /**
     * Loads [modelFile] if it isn't already loaded, then streams a response to
     * [latestUserMessage] through [callback]. [preferGpu] is accepted for API parity with
     * [LiteRtLmEngineBridge] but is a no-op in v1 — CPU-only per the approved plan; wire it to
     * a Vulkan/OpenCL native backend flag in the deferred GPU follow-up, not here.
     *
     * @param systemInstruction system prompt text, or null/blank for none.
     * @param history prior turns, oldest first — passed to `llama_chat_apply_template` as the
     *   message array so the model's own chat template renders every turn boundary.
     * @param latestUserMessage the newest user turn, appended after [history] before applying
     *   the template.
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
            try {
                val needsReload = loadedModelPath != modelFile.absolutePath || nativeHandle == 0L
                if (needsReload) {
                    closeInternal()
                    nativeHandle = LlamaNative.loadModel(
                        modelPath = modelFile.absolutePath,
                        contextSize = CONTEXT_SIZE_TOKENS
                    )
                    if (nativeHandle == 0L) {
                        throw IllegalStateException(
                            "llama.cpp failed to load model: ${modelFile.absolutePath}"
                        )
                    }
                    loadedModelPath = modelFile.absolutePath
                }

                val messages = buildList {
                    if (!systemInstruction.isNullOrBlank()) {
                        add(LlamaNative.ChatMessage(role = "system", content = systemInstruction))
                    }
                    history.forEach { turn ->
                        add(
                            LlamaNative.ChatMessage(
                                role = if (turn.isUser) "user" else "assistant",
                                content = turn.text
                            )
                        )
                    }
                    add(LlamaNative.ChatMessage(role = "user", content = latestUserMessage))
                }

                val renderedPrompt = LlamaNative.applyChatTemplate(nativeHandle, messages)

                val full = StringBuilder()
                var stopped = false

                // completionSequence is expected to invoke the token callback synchronously,
                // token-by-token, on this coroutine's thread — see LlamaNative doc for why the
                // native side owns the decode loop rather than this class polling it.
                LlamaNative.runCompletion(
                    handle = nativeHandle,
                    prompt = renderedPrompt,
                    maxTokens = CONTEXT_SIZE_TOKENS,
                    isCancelled = { cancelled.get() || stopped }
                ) { tokenText ->
                    if (cancelled.get() || stopped) return@runCompletion
                    val combined = full.toString() + tokenText
                    val cutIndex = STOP_SEQUENCES.map { seq -> combined.indexOf(seq) }
                        .filter { it >= 0 }
                        .minOrNull()
                    if (cutIndex != null) {
                        val cleanCombined = combined.substring(0, cutIndex)
                        val cleanDelta = cleanCombined.removePrefix(full.toString())
                        if (cleanDelta.isNotEmpty()) callback.onChunk(cleanDelta)
                        full.setLength(0)
                        full.append(cleanCombined)
                        stopped = true
                        callback.onComplete(full.toString())
                        return@runCompletion
                    }
                    full.append(tokenText)
                    callback.onChunk(tokenText)
                }

                if (!cancelled.get() && !stopped) {
                    callback.onComplete(full.toString())
                }
            } catch (t: Throwable) {
                callback.onError(t.message ?: "Unknown local generation error")
            }
        }
    }

    /** Backwards-compatible overload — always uses CPU (the only backend in v1 anyway). */
    fun generate(
        modelFile: File,
        systemInstruction: String?,
        history: List<HistoryTurn>,
        latestUserMessage: String,
        callback: GenerationCallback
    ) {
        generate(modelFile, systemInstruction, history, latestUserMessage, preferGpu = false, callback = callback)
    }

    /** Signals the current generation to stop delivering further chunks. */
    fun cancelGeneration() {
        cancelled.set(true)
    }

    private fun closeInternal() {
        if (nativeHandle != 0L) {
            try {
                LlamaNative.freeModel(nativeHandle)
            } catch (_: Exception) {
                // best-effort cleanup
            }
        }
        nativeHandle = 0L
        loadedModelPath = null
    }

    /** Releases native resources. Safe to call multiple times. */
    fun close() {
        cancelGeneration()
        closeInternal()
    }
}

/**
 * `external fun` surface this bridge expects from the vendored `:llama` module's JNI layer.
 * Method names/shapes follow upstream `ggml-org/llama.cpp`'s `examples/llama.android` sample
 * wrapper (`LLamaAndroid.kt`) as a starting point — **must be reconciled against the actual
 * vendored source** once it's cloned in an environment with GitHub access (this sandbox is
 * blocked from github.com, see [LlamaCppEngineBridge]'s class doc). Do not assume this compiles
 * as-is; it is a draft contract, not a confirmed one.
 */
internal object LlamaNative {
    data class ChatMessage(val role: String, val content: String)

    init {
        // Loaded from the vendored :llama module once it exists in the project; will throw
        // UnsatisfiedLinkError until then.
        System.loadLibrary("llama-android")
    }

    /** Loads a GGUF model at [modelPath] with the given [contextSize]; returns 0 on failure. */
    external fun loadModel(modelPath: String, contextSize: Int): Long

    /** Releases the native model/context behind [handle]. */
    external fun freeModel(handle: Long)

    /**
     * Renders [messages] through the GGUF's embedded chat template
     * (`llama_chat_apply_template`), returning the final prompt string ready for tokenization.
     */
    external fun applyChatTemplate(handle: Long, messages: List<ChatMessage>): String

    /**
     * Runs the decode loop for [prompt] up to [maxTokens], invoking [onToken] synchronously for
     * each generated token's text and polling [isCancelled] between tokens so generation can be
     * aborted mid-stream without waiting for [maxTokens] to be reached.
     */
    external fun runCompletion(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        isCancelled: () -> Boolean,
        onToken: (String) -> Unit
    )
}
