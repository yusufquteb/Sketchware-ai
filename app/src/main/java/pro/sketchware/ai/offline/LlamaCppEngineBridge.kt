package pro.sketchware.ai.offline

import android.content.Context
import android.os.Build
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin Kotlin bridge around the vendored `:llama` module — `com.arm.aichat`, ggml-org/llama.cpp's
 * own `examples/llama.android/lib`, pulled in via the `third_party/llama.cpp` git submodule
 * (pinned to commit 571d0d5 / tag b10068). Replaces [LiteRtLmEngineBridge] (deleted).
 *
 * <p><b>Real API, but NOT build-verified.</b> Unlike the first draft of this class (written
 * before github.com access was available in this session and based on a guessed low-level JNI
 * surface), this version was written against the actual vendored source — {@code AiChat.kt},
 * {@code InferenceEngine.kt}, {@code InferenceEngineImpl.kt}, and {@code ai_chat.cpp}, all read
 * directly from the submodule. It has still never been compiled here: no Android NDK is
 * installed in this sandbox. Treat the control flow below as faithful to the real API shape, not
 * as a confirmed-working integration — first real Gradle sync + NDK build is that test.
 *
 * <p><b>The engine is stateful, not stateless-per-call.</b> This is the most important behavioral
 * difference from the old LiteRT-LM bridge and from every cloud provider client in this app.
 * {@code InferenceEngineImpl} keeps the running conversation (KV cache, chat-template-formatted
 * turn history, automatic context-shifting when full) entirely inside the native layer once
 * {@link InferenceEngine#setSystemPrompt} has been called — see {@code ai_chat.cpp}'s
 * {@code processSystemPrompt}/{@code processUserPrompt}/context-shift logic. The public API only
 * ever takes the *newest* user message ({@link InferenceEngine#sendUserPrompt}); there is no way
 * to bulk-seed prior turns the way LiteRT-LM's {@code ConversationConfig.initialMessages} did.
 * Per the approved migration decision (this session), {@link LocalModelProvider} was adapted to
 * this pattern rather than fighting it: it now tracks whether the incoming call is a
 * continuation of the same native conversation (same model, same system instruction, exactly one
 * new message since last call) and only replays {@link #resetConversation} + a fresh
 * {@link InferenceEngine#setSystemPrompt} when that's not the case — see that class's
 * {@code buildPromptAssembly} for the continuity heuristic. A provider switch mid-conversation
 * and back to local, or any other history discontinuity, starts the native engine fresh from
 * just the newest message; older turns are not replayed into it (the public API has no method to
 * do that in bulk). This is an accepted, documented trade-off, not an oversight.
 *
 * <p><b>Context size:</b> fixed at 8192 tokens (see [CONTEXT_SIZE_TOKENS]) — this exactly matches
 * {@code DEFAULT_CONTEXT_SIZE} hardcoded in {@code ai_chat.cpp}'s {@code init_context()}, which
 * is NOT exposed as a parameter through the public {@code load}/{@code prepare} JNI surface in
 * this version of the module. There is currently no way to configure it higher or lower without
 * patching the vendored native source directly.
 *
 * <p><b>Chat templating:</b> handled natively — {@code ai_chat.cpp} calls
 * {@code common_chat_templates_init}/{@code common_chat_format_single} on the GGUF's own embedded
 * template for every {@code processSystemPrompt}/{@code processUserPrompt} call, the same
 * "engine renders the model's real turn-boundary tokens, this app never hand-rolls them" property
 * [LocalModelProvider] already depended on with LiteRT-LM.
 *
 * <p><b>minSdk mismatch:</b> the vendored module declares {@code minSdk=33} and arm64-v8a/x86_64
 * only ABI filters in its own {@code build.gradle.kts} — this app's minSdk stays 26 (see
 * {@code AndroidManifest.xml}'s {@code tools:overrideLibrary} and {@code app/build.gradle}'s
 * dependency-block comment for the full reasoning). [isDeviceSupported] is the runtime half of
 * that: every public entry point on this class checks it first and fails fast with a clear error
 * rather than risking an {@link UnsatisfiedLinkError} or worse on an unsupported device.
 */
class LlamaCppEngineBridge(context: Context) {

    companion object {
        /** Matches `DEFAULT_CONTEXT_SIZE` in ai_chat.cpp — see class doc; not runtime-configurable
         *  through the current module's public API. */
        const val CONTEXT_SIZE_TOKENS = 8192

        /**
         * Same defensive stop-sequence backstop the old LiteRT-LM bridge used. The native layer
         * is expected to stop cleanly on the GGUF's own EOS token, but this app has no way to
         * verify that for every quantized export in the catalog from this sandbox, so the same
         * client-side text scan is kept as a safety net.
         */
        private val STOP_SEQUENCES = listOf(
            "<|endoftext|>", "<|im_end|>", "<|end|>", "<|eot_id|>", "</s>", "<end_of_turn>"
        )

        /**
         * True if this device can plausibly run the vendored `:llama` module at all —
         * Build.VERSION.SDK_INT >= 33 (the module's own minSdk) and a supported 64-bit ABI
         * (arm64-v8a or x86_64, the only ones the module's build.gradle.kts declares). Callers
         * (see [LocalModelProvider]) must check this before constructing/using this bridge —
         * see class doc's "minSdk mismatch" note for why this exists.
         *
         * @JvmStatic so the Java caller can invoke it as LlamaCppEngineBridge.isDeviceSupported()
         * rather than LlamaCppEngineBridge.Companion.isDeviceSupported() — CONTEXT_SIZE_TOKENS
         * above is already Java-static because it's a const val, but a companion fun needs this.
         */
        @JvmStatic
        fun isDeviceSupported(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
            val supportedAbis = Build.SUPPORTED_ABIS ?: return false
            return supportedAbis.any { it == "arm64-v8a" || it == "x86_64" }
        }
    }

    /** Plain-callback contract — kept Flow/suspend-free at this class's boundary so the Java
     *  caller ([LocalModelProvider]) never has to cross the Java/Kotlin coroutine-interop
     *  boundary, same rationale as the old bridge. */
    interface GenerationCallback {
        fun onChunk(textDelta: String)
        fun onComplete(fullResponse: String)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val engine: InferenceEngine by lazy { AiChat.getInferenceEngine(appContext) }

    @Volatile
    var loadedModelPath: String? = null
        private set

    /** The system instruction currently established in the native conversation, or null if
     *  none has been set yet for the currently-loaded model. Compared against the caller's
     *  requested instruction each call to decide whether a conversation reset is needed — see
     *  class doc's "stateful, not stateless-per-call" note. */
    @Volatile
    private var establishedSystemInstruction: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cancelled = AtomicBoolean(false)
    private var activeJob: Job? = null

    /**
     * Loads [modelFile] if it isn't already loaded, establishes [systemInstruction] as the
     * native conversation's system prompt if it differs from what's currently established (or
     * [forceReset] is true), then sends [latestUserMessage] and streams the response through
     * [callback]. Does NOT take a history list — see class doc for why; the native engine keeps
     * its own conversation memory across calls as long as the model stays loaded and the system
     * instruction stays unchanged.
     *
     * @param forceReset if true, re-establishes the system prompt (resetting the native
     *   conversation's long/short-term state, per `processSystemPrompt`'s
     *   `reset_long_term_states()`/`reset_short_term_states()`) even if [systemInstruction]
     *   textually matches what's already established — [LocalModelProvider] sets this when its
     *   own continuity heuristic detects a discontinuity (provider switch, non-sequential
     *   history, etc.) even though the instruction text happens to be identical.
     * @param preferGpu accepted for API-shape parity with the old bridge; unused — the vendored
     *   module's CMake config has no GPU backend wired in this version, CPU-only.
     */
    fun generate(
        modelFile: File,
        systemInstruction: String?,
        latestUserMessage: String,
        forceReset: Boolean,
        preferGpu: Boolean,
        callback: GenerationCallback
    ) {
        if (!isDeviceSupported()) {
            callback.onError(
                "Offline AI needs Android 13+ on a 64-bit device (arm64-v8a/x86_64) — " +
                        "this device doesn't meet that requirement."
            )
            return
        }
        cancelled.set(false)
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                val needsLoad = loadedModelPath != modelFile.absolutePath
                if (needsLoad) {
                    if (loadedModelPath != null) {
                        try {
                            engine.cleanUp()
                        } catch (_: Exception) {
                            // best-effort — loadModel below will surface any real failure
                        }
                    }
                    engine.loadModel(modelFile.absolutePath)
                    loadedModelPath = modelFile.absolutePath
                    establishedSystemInstruction = null
                }

                val needsSystemPromptReset = needsLoad || forceReset ||
                        systemInstruction != establishedSystemInstruction
                if (needsSystemPromptReset) {
                    // setSystemPrompt requires non-blank input (see InferenceEngine doc) and
                    // must be called right after load — a blank placeholder keeps that contract
                    // satisfiable even when this provider has no system prompt/tool/knowledge
                    // block to send for a given call.
                    val promptToSend = systemInstruction?.takeIf { it.isNotBlank() } ?: " "
                    engine.setSystemPrompt(promptToSend)
                    establishedSystemInstruction = systemInstruction
                }

                val full = StringBuilder()
                var stopped = false
                engine.sendUserPrompt(latestUserMessage, InferenceEngine.DEFAULT_PREDICT_LENGTH)
                    .catch { throwable ->
                        callback.onError(throwable.message ?: "Unknown local generation error")
                    }
                    .collect { token ->
                        if (cancelled.get() || stopped) return@collect
                        val combined = full.toString() + token
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
                            return@collect
                        }
                        full.append(token)
                        callback.onChunk(token)
                    }
                if (!cancelled.get() && !stopped) {
                    callback.onComplete(full.toString())
                }
            } catch (t: Throwable) {
                callback.onError(t.message ?: "Unknown local generation error")
            }
        }
    }

    /** Backwards-compatible overload — no forced reset, no GPU preference. */
    fun generate(
        modelFile: File,
        systemInstruction: String?,
        latestUserMessage: String,
        callback: GenerationCallback
    ) {
        generate(modelFile, systemInstruction, latestUserMessage, forceReset = false, preferGpu = false, callback = callback)
    }

    /** Signals the current generation to stop delivering further chunks and cancels the
     *  underlying coroutine — InferenceEngineImpl.sendUserPrompt's Flow observes cancellation
     *  via CancellationException (see its own try/catch) and returns to ModelReady state. */
    fun cancelGeneration() {
        cancelled.set(true)
        activeJob?.cancel()
    }

    /**
     * Releases the loaded model. Safe to call multiple times.
     *
     * <p>Two deliberate choices here (audit fix — both were real defects in the first version):
     * <ul>
     *   <li><b>cleanUp(), never destroy():</b> the vendored engine is a process-wide singleton
     *       ({@code InferenceEngineImpl.instance} is cached and never reset). {@code destroy()}
     *       cancels its internal coroutine scope permanently, so one bridge instance calling it
     *       would brick offline generation for every later bridge in the same process — each
     *       {@code AiClientFactory.createClient(LOCAL_LLM)} call makes a fresh
     *       {@code LocalModelProvider}/bridge, but they all share that one engine.
     *       {@code cleanUp()} unloads the model and returns the engine to its reusable
     *       Initialized state instead.</li>
     *   <li><b>Off-thread, not runBlocking:</b> this is reachable from
     *       {@code ChatViewModel.onCleared()} → {@code LocalModelProvider.shutdown()} on the
     *       main thread, and the vendored {@code cleanUp()} itself blocks on the engine's
     *       single-threaded dispatcher — running that inline on main risks a visible freeze
     *       (ANR) if a generation is still winding down.</li>
     * </ul>
     */
    fun close() {
        cancelGeneration()
        val hadModel = loadedModelPath != null
        loadedModelPath = null
        establishedSystemInstruction = null
        scope.cancel()
        if (hadModel) {
            Thread {
                try {
                    engine.cleanUp()
                } catch (_: Exception) {
                    // best-effort cleanup — an engine already in Error/Initialized state throws
                    // IllegalStateException from cleanUp(), which is fine to ignore here
                }
            }.start()
        }
    }
}
