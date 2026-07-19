package pro.sketchware.ai.offline;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Static catalog of on-device LLM models offered through llama.cpp (previously LiteRT-LM).
 *
 * <p><b>llama.cpp migration (this session) — catalog rewrite BLOCKED, entries below are STILL
 * LiteRT-LM {@code .litertlm} files.</b> {@link LocalModelProvider} and {@link
 * LlamaCppEngineBridge} now target llama.cpp, which loads {@code .gguf} files, not
 * {@code .litertlm}. The approved migration plan calls for rewriting every entry below to a
 * verified GGUF equivalent (same model families are available as GGUF from quantizers like
 * {@code bartowski}/{@code unsloth}), but doing that from this session was not possible:
 * huggingface.co is blocked by this session's network egress policy (confirmed: a direct CONNECT
 * to huggingface.co:443 was rejected with a 403 policy denial), so no GGUF filename, quantization
 * variant, or byte size below could be verified against a real file listing. This catalog's own
 * history (see the Phase 5.4/5.5 notes further down) already documents two real incidents of
 * guessed filenames 404ing for actual users — fabricating GGUF URLs here would repeat exactly
 * that mistake at the whole-catalog scale instead of one entry. <b>Do not treat the entries below
 * as llama.cpp-compatible until someone with Hugging Face access rewrites them</b> — until then,
 * {@link LocalModelDownloader} will keep downloading real {@code .litertlm} files that
 * {@link LlamaCppEngineBridge} cannot load, i.e. offline generation will fail post-migration
 * until this catalog is actually updated. This is the single largest remaining gap in the
 * migration; see the plan's "What gets replaced → LocalModelCatalog.java" section for the
 * per-entry verification discipline to follow when unblocked.
 *
 * <p>Every entry maps to a real, publicly downloadable {@code .litertlm} file on Hugging Face,
 * all currently in the "litert-community" org (https://huggingface.co/litert-community). File
 * names/sizes were (re-)confirmed via web search during the Phase 5.4 session (July 2026) after
 * two of the four original entries turned out to reference stale filenames — see
 * {@code CHANGES.md} Phase 5.4 section for the full search trail and what changed. Sizes are the
 * quantized (int8/int4) variant actually linked, not the original full-precision model size.
 *
 * <p><b>Phase 5.7 (July 2026) — Gemma 4 E2B activated per explicit user instruction.</b>
 * {@link #GEMMA_4_E2B} was added as a live entry, overriding the Phase 5.5/5.6 hold-back that
 * kept it as documentation-only pending a live download test. The user was told directly that
 * this repo's "no gate text found" is not the same claim as "the download doesn't require a
 * token" and that this session has no way to verify the latter (huggingface.co is unreachable
 * from this environment); the user chose to activate anyway based on the documentation available.
 * {@code gated} is set to {@code false} on this entry accordingly — if real-world downloads start
 * 401ing, that field is the one to flip, and {@code LocalModelDownloader} already has working
 * gated-model handling in place for exactly that case (fails fast with a clear message instead of
 * a raw 401). See {@link #GEMMA_4_E2B}'s own javadoc for the full reasoning.
 *
 * <p><b>Phase 5.6 (July 2026) — hidden model re-enabled; deep search for the strongest ungated
 * model.</b>
 * <ul>
 *   <li>{@link #DEEPSEEK_R1_DISTILL_QWEN_1_5B} was re-enabled (per explicit user instruction —
 *       activate hidden/unverified entries or remove them outright). It was never actually
 *       broken; see its own javadoc for the full reasoning on why the original hide-reason
 *       (weaker tool use) no longer applies now that the local-model path sends no tools at all.
 *       {@link #all()} now simply returns every {@link #values()} entry — no filtering.</li>
 *   <li>{@link #PHI4_MINI_INSTRUCT} was re-added after a deep pass through every entry in
 *       litert-community's "Android Models" collection (17 models, all fetched and reviewed this
 *       session) looking for the strongest ungated model within the shared {@code ekv4096}
 *       ceiling. At 3.8B parameters and MIT-licensed, it's the largest ungated model available —
 *       see its own javadoc for the full re-verification and for why the Phase 5.4 removal reason
 *       (tool-call crash reports) no longer applies now that tool-calling was separately removed
 *       from the local-model path entirely.</li>
 * </ul>
 *
 * <p><b>Phase 5.5 re-verification (July 2026).</b> Every entry below was re-checked against its
 * live Hugging Face file listing this session (not assumed correct from memory):
 * <ul>
 *   <li>{@link #QWEN2_0_5B_INSTRUCT} — confirmed unchanged: {@code Qwen2_0.5B_Instruct.litertlm},
 *       647.4 MB, matches this catalog exactly. There was no actual bug in this entry; it was
 *       requested as "Qwen2.5-0.5B-Instruct" this session, which is a <i>different</i>, newer
 *       repo — see the note on that name immediately below.</li>
 *   <li>{@code litert-community/Qwen2.5-0.5B-Instruct} (the newer Qwen2.5-generation 0.5B repo,
 *       distinct from the Qwen2 entry already in this catalog) was checked and rejected: every
 *       file in that repo is {@code .tflite} or {@code .task} (MediaPipe LLM Inference API), and
 *       there is no {@code .litertlm} file in it at all. {@link LocalModelProvider}'s
 *       {@code LiteRtLmEngineBridge} loads {@code .litertlm} specifically, so this repo cannot be
 *       added without a different loading path this app doesn't have. Not added.</li>
 *   <li>Kimi (Moonshot AI K2) — re-searched this session, same result as before: no
 *       {@code .litertlm}/LiteRT conversion exists anywhere (litert-community org, LiteRT-LM
 *       GitHub repo, or Google's docs). Not added; see the standing note further down.</li>
 *   <li>DeepSeek — re-searched this session for any new litert-community DeepSeek repo beyond
 *       {@link #DEEPSEEK_R1_DISTILL_QWEN_1_5B} (re-enabled in Phase 5.6, see its own javadoc —
 *       no longer hidden). No second DeepSeek repo was found; the only other DeepSeek
 *       reference located was an open Hugging Face "Inference Providers" feature-request thread
 *       ({@code litert-community/Deepseek}, discussion #2487) asking for a conversion to be made
 *       — not an actual published model repo, so nothing to link to. Not added.</li>
 *   <li>A new family, <b>Gemma 4</b>, was found this session:
 *       {@code litert-community/gemma-4-E2B-it-litert-lm}. Unlike Gemma 3/3n (both gated, see
 *       below), Google's own docs state plainly "Gemma 4 is licensed under the Apache-2.0
 *       license" with no gated/{@code extra_gated_prompt} language found on the model card —
 *       this looks like a genuine licensing change for this generation, not just a re-read of the
 *       same gate. The general-purpose file is {@code gemma-4-E2B-it.litertlm}, 2.58 GB
 *       (confirmed via Google AI Edge's own docs page, which also lists the same file for a 3.65
 *       GB E4B variant) — supports native function-calling per Google's model card, which would
 *       make it the first tool-capable entry in this catalog if the tool-block-removal in this
 *       provider (see the "hi" 11,203-token incident write-up in {@code CHANGES.md} Phase 5.4) is
 *       ever revisited. <b>Activated in Phase 5.7</b> as {@link #GEMMA_4_E2B} per explicit user
 *       instruction, despite this session still having no way to verify the download itself
 *       (huggingface.co unreachable from this environment) — see that entry's own javadoc for
 *       exactly what is and isn't confirmed before relying on this for every user's device.</li>
 * </ul>
 *
 * <p><b>Lineup change (post-Phase-5.4 field feedback).</b> {@code GEMMA3_1B}, {@code
 * PHI4_MINI_INSTRUCT}, and {@code GEMMA_3N_E2B} were removed and {@link #DEEPSEEK_R1_DISTILL_QWEN_1_5B}
 * was added:
 * <ul>
 *   <li>Gemma 3 1B and Gemma 3n E2B removed — both required a gated Hugging Face repo (license
 *       acceptance + personal access token) on top of everything else in this list, and field use
 *       didn't show either pulling ahead of the ungated alternatives enough to justify that extra
 *       friction for most people setting this up.</li>
 *   <li>Phi-4-mini-instruct removed (Phase 5.4) — field reports showed it consistently causing
 *       the app to exit/restart after a tool call. Its 3.8B parameter count is notably larger
 *       than every other model that was in this list, which was the leading (though not fully
 *       confirmed — no on-device crash log was available to inspect) explanation: {@code
 *       android:largeHeap="true"} in the manifest only extends the Java/Dalvik heap, not the
 *       native memory a loaded {@code .litertlm} model occupies via JNI, so a native
 *       out-of-memory condition on this size of model wouldn't surface as a catchable Java
 *       exception anywhere in this app's code. <b>Re-added in Phase 5.6</b> as
 *       {@link #PHI4_MINI_INSTRUCT} — see that entry's own javadoc for why the tool-call crash
 *       trigger no longer applies now that the local-model path sends no tools at all.</li>
 *   <li>DeepSeek-R1-Distill-Qwen-1.5B added — a real, public, ungated {@code .litertlm} build
 *       exists (confirmed via web search during this change: {@code litert-community/
 *       DeepSeek-R1-Distill-Qwen-1.5B}, MIT-licensed, same {@code ekv4096} ceiling as every other
 *       entry here), at a similar size/RAM tier to the Qwen2.5 default.</li>
 *   <li>Qwen2-0.5B-Instruct added — requested as "Qwen2-1.5B-Instruct", but a web search of
 *       litert-community's Hugging Face org during this change found no such repo (only a 0.5B
 *       Qwen2 entry). Added the real repo as the closest ungated alternative, with its file
 *       listing fetched and confirmed directly ({@code Qwen2_0.5B_Instruct.litertlm}, 647.4 MB)
 *       — an earlier version of this entry guessed the filename by pattern-matching the other
 *       catalog rows instead of checking, which 404'd for a real user; see that entry's own
 *       javadoc for the correction. A "Qwen2.5-Coder-3B-Instruct" entry was also requested;
 *       {@code litert-community/Qwen2.5-3B-Instruct} exists (general-purpose, not Coder) but its
 *       exact artifact filename/size could not be confirmed this session, so it was deliberately
 *       left out rather than risk another guessed URL — see the comment after the enum constant
 *       list for how to add it once confirmed.</li>
 * </ul>
 * <p><b>Tool calling.</b> {@link LocalModelProvider}'s tool-call parsing is generic and applies
 * uniformly to every entry in this catalog — there is no per-model or per-family gate. DeepSeek
 * gets the same {@code <tool_call>} parsing path as Qwen; nothing in {@code AgentExecutor} or
 * {@code ToolExecutionGuard} special-cases either family.
 *
 * A Kimi (Moonshot AI K2) entry was requested but is not available: Kimi K2 is a very large
 * mixture-of-experts model with no public {@code .litertlm}/LiteRT conversion by Moonshot AI or
 * the community as of this change (confirmed via web search — LiteRT-LM's own documented model
 * families are Gemma, Llama, Phi-4, and Qwen; nothing named Kimi appears in the litert-community
 * org, the LiteRT-LM GitHub repo, or Google's official LiteRT-LM docs). If a conversion is
 * published later, adding it here would follow the same shape as every other entry. Re-checked
 * in Phase 5.5 (July 2026) — same result, still nothing published.
 *
 * <p>A second DeepSeek entry (beyond {@link #DEEPSEEK_R1_DISTILL_QWEN_1_5B}, re-enabled in Phase
 * 5.6 and already in this catalog) was also requested in Phase 5.5 and searched for — no
 * additional litert-community DeepSeek repo was found. The only other DeepSeek-related result
 * was an open Hugging Face "Inference Providers" feature-request discussion thread titled
 * {@code litert-community/Deepseek} (#2487) asking Google/the community to publish one — a
 * request thread, not an actual model repo with files to link to. Nothing to add until that
 * request is fulfilled.
 *
 * <p><b>4096-token ceiling is a real upstream limit, not an app-imposed one.</b> Every model in
 * the "litert-community" org (Qwen2.5, Qwen3, DeepSeek-R1-Distill, etc.) is currently published
 * with a single KV-cache size, encoded in the filename as {@code ekv4096} (4096 tokens) —
 * confirmed across every model page checked in this session. There is no larger-context
 * {@code .litertlm} build of any of these models publicly available right now; a bigger
 * on-device context window would require Google/the community publishing a new export, which
 * this catalog cannot influence.
 *
 * <p><b>{@link #gated}</b>: with the Gemma-family entries removed, every model currently in this
 * catalog is an ungated, public download — {@code gated} is kept on each entry (rather than
 * removed as a field) so a future re-added gated model doesn't require re-plumbing this flag
 * through {@link LocalModelManager} and the download UI.
 *
 * <p>Device-tier guidance ({@link #minRamGb}) is a recommendation, not a hard block —
 * {@link LocalModelManager} shows a warning below the recommended threshold but still
 * lets the user proceed, per the phase requirement that the final call belongs to the user.
 */
public enum LocalModelCatalog {

    QWEN3_0_6B(
            "qwen3-0.6b",
            "Qwen3 0.6B",
            "litert-community/Qwen3-0.6B",
            "Qwen3-0.6B.litertlm",
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            700L * 1024 * 1024, // ~0.7 GB (dynamic INT8 artifact)
            3,
            DeviceTier.LOW_END,
            false, // ungated — public download, Apache-2.0 (Qwen license)
            "Lightest model here — very fast, but limited on coding and complex tasks. "
                    + "Good for short replies and simple edits."
    ),

    QWEN2_5_1_5B_INSTRUCT(
            "qwen2.5-1.5b-instruct",
            "Qwen2.5 1.5B Instruct",
            "litert-community/Qwen2.5-1.5B-Instruct",
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            1740L * 1024 * 1024, // ~1.7 GB
            6,
            DeviceTier.MID_RANGE,
            false, // ungated — public download, Apache-2.0
            "★ Recommended default. Best balance of quality and size — strongest here at "
                    + "multi-step coding instructions."
    ),

    /**
     * Re-enabled in Phase 5.6 (per explicit user request — "activate hidden models or they'll be
     * removed entirely"). Previously hidden via {@code @Deprecated} + exclusion in {@link #all()}
     * over field reports of weaker tool-use and runaway {@code <think>} chain-of-thought — that
     * concern was about output quality/behavior, not file validity: the repo/filename/size below
     * were independently re-confirmed as correct and unchanged in Phase 5.5's re-verification
     * pass. Since Phase 5.4 already removed tool-calling entirely from the local-model prompt
     * path (see {@code LocalModelProvider}'s tool-block removal, {@code CHANGES.md} Phase 5.4),
     * the "weaker at tool use" concern that justified hiding this no longer applies to how this
     * app actually uses any local model today — the original justification for hiding it is
     * largely moot. The unstructured {@code <think>} chain-of-thought behavior may still produce
     * longer/slower responses than the Qwen entries; that's reflected in the capability note
     * below rather than being a reason to hide the entry.
     */
    DEEPSEEK_R1_DISTILL_QWEN_1_5B(
            "deepseek-r1-distill-qwen-1.5b",
            "DeepSeek-R1-Distill-Qwen 1.5B",
            "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
            "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
            1830L * 1024 * 1024, // ~1.83 GB (confirmed exact file size on the repo)
            6,
            DeviceTier.MID_RANGE,
            false, // ungated — public download, MIT license
            "DeepSeek's R1 reasoning distilled onto a Qwen base — shows its reasoning step by "
                    + "step, which helps on multi-step logic but tends to run longer than the "
                    + "Qwen entries above before finishing a response."
    ),

    /**
     * Requested as "Qwen2-1.5B-Instruct" — searched litert-community's Hugging Face org during
     * this change and could not confirm a {@code Qwen2-1.5B-Instruct} repo there; only {@code
     * litert-community/Qwen2-0.5B-Instruct} (0.5B, not 1.5B) is published for the Qwen2
     * (non-.5) generation. Filled in with that real, ungated, confirmed repo rather than a
     * guessed 1.5B filename/URL that could 404. If a Qwen2-1.5B-Instruct .litertlm is published
     * later, swap the id/displayName/hfRepo/fileName/downloadUrl/size below to match it.
     *
     * <p><b>Correction (Phase 5.4):</b> the first version of this entry guessed the filename
     * by pattern-matching the other catalog entries ({@code
     * Qwen2-0.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm}) instead of actually checking
     * this repo's file listing — that guess was wrong and 404'd for a real user. Re-fetched the
     * repo's model card directly this time: its "Available Artifact" table lists exactly one
     * file, {@code Qwen2_0.5B_Instruct.litertlm} (647.4 MB, dynamic_wi8_afp32 quantization, no
     * context-size suffix in the name — this repo doesn't follow the
     * {@code _multi-prefill-seq_q8_ekv4096} naming convention the newer Qwen2.5/DeepSeek entries
     * use). id/fileName/downloadUrl/size below now match that confirmed listing exactly.
     *
     * <p><b>Re-verified (Phase 5.5):</b> checked again against the repo's live file listing this
     * session — {@code Qwen2_0.5B_Instruct.litertlm} at 647.4 MB is confirmed still correct,
     * unchanged since Phase 5.4; there was no actual bug in this entry. It was requested this
     * session as "Qwen2.5-0.5B-Instruct" (note the ".5" — a newer, different repo than the Qwen2
     * one this entry actually points to). That repo, {@code litert-community/Qwen2.5-0.5B-Instruct},
     * does exist and is ungated, but its entire file listing is {@code .tflite}/{@code .task}
     * (MediaPipe LLM Inference API) with no {@code .litertlm} file at all — this app's engine
     * ({@code LiteRtLmEngineBridge}) only loads {@code .litertlm}, so that repo cannot be wired in
     * without a different loader this app doesn't have. This Qwen2 (non-.5) entry remains the
     * closest real, ungated, {@code .litertlm} match at the 0.5B size tier.
     */
    QWEN2_0_5B_INSTRUCT(
            "qwen2-0.5b-instruct",
            "Qwen2 0.5B Instruct",
            "litert-community/Qwen2-0.5B-Instruct",
            "Qwen2_0.5B_Instruct.litertlm",
            "https://huggingface.co/litert-community/Qwen2-0.5B-Instruct/resolve/main/Qwen2_0.5B_Instruct.litertlm",
            647L * 1024 * 1024 + 400L * 1024, // 647.4 MB — exact size from the repo's file table
            3,
            DeviceTier.LOW_END,
            false, // ungated — public download, Apache-2.0 (Qwen license)
            "Requested as \"Qwen2-1.5B-Instruct\" — no such build is published by litert-community; "
                    + "this is their real Qwen2 entry (0.5B) as the closest ungated alternative. "
                    + "Lighter and less capable than Qwen2.5 1.5B Instruct above."
    ),

    /**
     * Re-added in Phase 5.6 — deep search across every text-generation model in
     * litert-community's own "Android Models" collection (17 entries, fetched and reviewed in
     * full this session) for the strongest ungated, real {@code .litertlm}-exporting model
     * within the {@code ekv4096} ceiling every other non-Gemma entry in this catalog shares.
     * Result: {@code litert-community/Phi-4-mini-instruct} — 3.8B parameters (Microsoft), the
     * largest parameter count of any ungated entry in the whole collection, MIT license
     * (confirmed directly on the repo page: {@code License: mit}, no gated/{@code
     * extra_gated_prompt} language anywhere on the card), file {@code
     * Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm} at 3.91 GB (cross-confirmed via
     * two independent sources this session: the repo's own file table, and a third-party app's
     * public release-notes page linking the exact same URL/filename for its own LiteRT-LM
     * integration).
     *
     * <p><b>Why this was previously removed (Phase 5.4), and why that reasoning no longer
     * holds.</b> The original removal was over field reports of the app exiting/restarting after
     * a tool call, with this model's larger native-memory footprint (see the manifest {@code
     * android:largeHeap} discussion in the class javadoc "Lineup change" note) as the leading
     * suspected cause. That same Phase 5.4 session <i>also</i> removed all tool-calling from the
     * local-model prompt path entirely — {@link LocalModelProvider} no longer builds or sends a
     * tool block to any on-device model, this one included. A "tool call" is no longer something
     * that happens against a local model in this app at all, so the specific failure trigger this
     * model was pulled over cannot currently occur here. The underlying native-memory concern for
     * a model this size is real and unchanged (hence the higher {@code minRamGb} below relative
     * to every other entry), but that's a general resource-sizing consideration reflected in the
     * device-tier guidance, not a reason to hide the entry outright.</p>
     *
     * <p>Deliberately NOT marked {@code gated} — {@code litert-community/Phi-4-mini-instruct}'s
     * page shows a plain {@code License: mit} with no click-through language found anywhere on
     * it, unlike the Gemma-family entries removed earlier in this catalog's history (those had an
     * explicit {@code extra_gated_prompt} block in their README). This is the same kind of
     * "License field says open, no gate text found" situation as the still-unactivated Gemma 4
     * candidate noted at the end of this enum's constant list — the difference here is MIT is a
     * fully unambiguous open license with no history of Hugging Face gated MIT repos, whereas
     * Gemma has a documented history of gated Apache-2.0-labeled repos, which is why Gemma 4 is
     * still being held back pending a live download test and this entry is not.</p>
     */
    PHI4_MINI_INSTRUCT(
            "phi-4-mini-instruct",
            "Phi-4-mini Instruct",
            "litert-community/Phi-4-mini-instruct",
            "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            3910L * 1024 * 1024, // 3.91 GB — confirmed exact size, cross-checked two sources
            8,
            DeviceTier.HIGH_END,
            false, // ungated — public download, MIT license (Microsoft)
            "★ Strongest model in this catalog by parameter count (3.8B, Microsoft). Best raw "
                    + "reasoning/coding quality here, but the largest download and the slowest to "
                    + "load — needs a higher-RAM device to run comfortably."
    ),

    /**
     * Activated in Phase 5.7 per explicit user instruction, overriding the Phase 5.5/5.6
     * hold-back. <b>Read this before assuming the model downloads cleanly for every user.</b>
     *
     * <p><b>What is and isn't actually confirmed.</b> Confirmed via direct fetch of Google's own
     * docs and the model card this session: the file is {@code gemma-4-E2B-it.litertlm}, size
     * 2.58 GB, and the license field reads "Gemma 4 is licensed under the Apache-2.0 license"
     * with no {@code extra_gated_prompt} block found anywhere on the card — structurally
     * different from how Gemma 2/3/3n's gating was documented (those had an explicit gated
     * block; this session found none for Gemma 4). What is <b>not</b> confirmed: an actual
     * download attempt. This session has no network access to huggingface.co, so nobody has
     * verified this URL returns 200 without a Hugging Face token rather than a 401. "No gate
     * text visible on the page" and "the download doesn't require a token" are two different
     * claims, and only the first one is verified here.
     *
     * <p><b>Set {@code gated = false}</b> per explicit instruction to activate based on the
     * documentation available now rather than wait for a live download test. If real users hit a
     * 401 on this repo, that's the signal this assumption was wrong for this specific model —
     * flip this to {@code gated = true} at that point (and see
     * {@code LocalModelDownloader}'s existing gated-model handling, already wired for exactly
     * this: it fails fast with an "requires Hugging Face access token" message instead of a
     * confusing raw 401 once {@code isGated()} is {@code true}).
     *
     * <p>Per Google's model card this family supports native function-calling — the first
     * tool-capable model in this catalog. That capability is currently inert here: Phase 5.4
     * removed all tool-block construction from {@link LocalModelProvider}'s local-model prompt
     * path, so no tools are sent to any on-device model regardless of what the model itself
     * supports. Using this model's function-calling would require separately reintroducing a
     * (likely much smaller, curated) tool block for local models — out of scope for this change.
     *
     * <p>This repo also hosts several device-specific NPU {@code .litertlm} builds (Intel/
     * Qualcomm/Tensor chip names baked into the filename, ~2.9-3.1 GB each) — this entry
     * deliberately points at {@code gemma-4-E2B-it.litertlm}, the general/CPU-portable build, not
     * those.
     */
    GEMMA_4_E2B(
            "gemma-4-e2b-it",
            "Gemma 4 E2B",
            "litert-community/gemma-4-E2B-it-litert-lm",
            "gemma-4-E2B-it.litertlm",
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            2580L * 1024 * 1024, // 2.58 GB — per Google AI Edge's own docs page
            6,
            DeviceTier.MID_RANGE,
            false, // documented as Apache-2.0 with no gate text found this session — NOT verified
                   // by an actual download attempt; see this entry's javadoc before trusting this
                   // blindly if users start reporting 401s
            "Google's newest small on-device model — supports native function-calling upstream "
                    + "(not currently used by this app's local-model path). Not yet verified by an "
                    + "actual download in this codebase; flag it if this fails to download."
    );

    // NOTE (this session): a "Qwen2.5-Coder-3B-Instruct" entry was also requested. A
    // litert-community/Qwen2.5-3B-Instruct repo does exist (general-purpose, not the
    // Coder-specific variant — same caveat as above), but this session could not fetch that
    // repo's exact file listing/artifact filename to confirm a working download URL (the same
    // mistake that produced a 404 for the Qwen2-0.5B entry above happened once already this
    // session and is not being repeated on unverified data). Deliberately left out of the
    // catalog rather than guessing another filename. To add it: open
    // https://huggingface.co/litert-community/Qwen2.5-3B-Instruct/tree/main, copy the exact
    // .litertlm filename and byte size from the file table, and add an entry here following the
    // same shape as the others.

    // NOTE (Phase 5.5, superseded by Phase 5.7): a Gemma 4 candidate was found and documented
    // here as inactive pending a live download test. Phase 5.7 activated it per explicit user
    // instruction — see GEMMA_4_E2B above for the full reasoning and what remains unverified.

    /** Rough device-tier bucket used only for the settings UI grouping/label — not a hard gate. */
    public enum DeviceTier {
        LOW_END("Low-end device"),
        MID_RANGE("Mid-range device"),
        HIGH_END("High-end device");

        public final String label;
        DeviceTier(String label) { this.label = label; }
    }

    private final String id;
    private final String displayName;
    private final String hfRepo;
    private final String fileName;
    private final String downloadUrl;
    private final long approxSizeBytes;
    private final int minRamGb;
    private final DeviceTier tier;
    private final boolean gated;
    private final String capabilityNote;

    LocalModelCatalog(String id, String displayName, String hfRepo, String fileName,
                       String downloadUrl, long approxSizeBytes, int minRamGb,
                       DeviceTier tier, boolean gated, String capabilityNote) {
        this.id = id;
        this.displayName = displayName;
        this.hfRepo = hfRepo;
        this.fileName = fileName;
        this.downloadUrl = downloadUrl;
        this.approxSizeBytes = approxSizeBytes;
        this.minRamGb = minRamGb;
        this.tier = tier;
        this.gated = gated;
        this.capabilityNote = capabilityNote;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getHfRepo() { return hfRepo; }
    public String getFileName() { return fileName; }
    public String getDownloadUrl() { return downloadUrl; }
    public long getApproxSizeBytes() { return approxSizeBytes; }
    public int getMinRamGb() { return minRamGb; }
    public DeviceTier getTier() { return tier; }
    /** True if this model requires a Hugging Face account + access token to download (401
     *  otherwise) — false for every entry currently in this catalog (the previously-included
     *  gated Gemma-family models were removed; see class javadoc "Lineup change" note). Kept as
     *  a per-entry field rather than removed so a future gated model can be added without
     *  re-plumbing this flag through {@link LocalModelManager} and the download UI. */
    public boolean isGated() { return gated; }
    /** Capability/recommendation note shown under the model row in Settings. */
    public String getCapabilityNote() { return capabilityNote; }

    /**
     * Direct link to this model's page on huggingface.co. For gated models this is the
     * exact page the user must open (while logged in) to click "Agree"/"Accept" on the
     * license — searching the site for "Gemma" does not reliably surface these repos
     * since org names like {@code litert-community} don't match a plain-text search.
     */
    public String getModelPageUrl() { return "https://huggingface.co/" + hfRepo; }

    /** Human-readable approximate size, e.g. "1.7 GB". */
    public String getApproxSizeLabel() {
        double gb = approxSizeBytes / (1024.0 * 1024.0 * 1024.0);
        return String.format(java.util.Locale.US, "%.1f GB", gb);
    }

    /** The default/recommended model shown pre-selected in the UI. */
    public static LocalModelCatalog getRecommendedDefault() {
        return QWEN2_5_1_5B_INSTRUCT;
    }

    @Nullable
    public static LocalModelCatalog fromId(@Nullable String id) {
        if (id == null) return null;
        for (LocalModelCatalog m : values()) {
            if (m.id.equals(id)) return m;
        }
        return null;
    }

    @NonNull
    public static List<LocalModelCatalog> all() {
        return Arrays.asList(values());
    }
}
