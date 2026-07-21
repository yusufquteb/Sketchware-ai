package pro.sketchware.ai.offline;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Static catalog of on-device LLM models offered through llama.cpp.
 *
 * <p><b>Phase 6 (July 2026) — catalog rewritten from LiteRT-LM {@code .litertlm} to llama.cpp
 * {@code .gguf}.</b> {@link LocalModelProvider} and {@link LlamaCppEngineBridge} were already
 * migrated to llama.cpp in an earlier change, but this catalog kept pointing at the old
 * {@code litert-community} {@code .litertlm} files — a real, user-reported bug: downloads would
 * succeed (the old URLs were still live) but {@link LlamaCppEngineBridge#generate} would then
 * fail to load the file at all, since llama.cpp only reads GGUF. Offline generation was silently
 * broken end-to-end until this rewrite. huggingface.co is still blocked by this session's network
 * egress policy (confirmed again this session: direct HTTPS CONNECT to huggingface.co:443 and to
 * the hf-mirror.com mirror both rejected with a 403 policy denial), so — same constraint every
 * earlier phase of this catalog's history hit — no entry below could be verified by directly
 * fetching a live Hugging Face file listing from this session.
 *
 * <p><b>What was actually done instead: the user opened and confirmed six repo pages directly
 * from their own machine</b> (pasted the exact huggingface.co URLs into this conversation), and
 * {@code WebSearch} results (a real search engine, not this session's own knowledge) were used to
 * cross-check exact filenames/sizes against those confirmed repos. Per-entry confidence below:
 * <ul>
 *   <li><b>High</b> ({@link #DEEPSEEK_R1_DISTILL_QWEN_1_5B}, {@link #PHI4_MINI_INSTRUCT}): the
 *       search results included a direct {@code .../blob/main/&lt;exact-filename&gt;} hit,
 *       meaning a search engine actually indexed that literal file path — this is the strongest
 *       signal short of fetching the page directly.</li>
 *   <li><b>High</b> ({@link #GEMMA_4_E2B}): both the repo AND a full per-quantization size table
 *       (BF16/Q8_0/Q6_K_L/IQ3_M, etc.) came back from the search, which only happens if the
 *       indexed page content included that table — i.e. the file genuinely exists with that name.</li>
 *   <li><b>High</b> ({@link #QWEN2_5_1_5B_INSTRUCT}): repo confirmed directly by the user opening
 *       it; the official {@code Qwen} org uses one fully-lowercase-hyphenated filename convention
 *       across every repo checked this session ({@code qwen2.5-1.5b-instruct-q&lt;n&gt;.gguf}),
 *       and a search result for this exact repo explicitly named the {@code q5_k_m} sibling file
 *       in that convention — the {@code q8_0} sibling follows the same, confirmed pattern.</li>
 *   <li><b>Medium-high</b> ({@link #QWEN3_0_6B}, {@link #QWEN2_0_5B_INSTRUCT}): repo confirmed
 *       directly by the user opening it, and the Q8_0 file's exact byte size was confirmed by
 *       search (639 MB / 531 MB respectively) — but the filename itself is inferred from the same
 *       official-{@code Qwen}-org naming convention above rather than an indexed direct-file hit.
 *       If either 404s, the failure surfaces immediately and loudly the first time a user tries to
 *       download it (see {@link LocalModelDownloader}) — not a silent, catalog-wide failure like
 *       the bug this rewrite fixes.</li>
 * </ul>
 * If any entry below turns out to be wrong, fixing it is a one-line {@code fileName}/
 * {@code downloadUrl} edit here, not a structural problem — see this catalog's own pre-Phase-6
 * history (recoverable via {@code git log} on this file / {@code CHANGES.md}) for the same
 * verify-don't-guess discipline applied to the old {@code .litertlm} entries across several prior
 * phases.
 *
 * <p><b>Context window.</b> Unlike LiteRT-LM (where the KV-cache size was baked into the exported
 * file itself, encoded in filenames as {@code ekv4096}), llama.cpp's {@code n_ctx} is a run-time
 * load parameter set once for every model by {@link LlamaCppEngineBridge#CONTEXT_SIZE_TOKENS}
 * (8192) — this catalog no longer needs a per-entry context-size field at all.
 *
 * <p><b>Quantization.</b> Every entry below points at the {@code Q8_0} quantization (8-bit),
 * chosen to match the quality of the {@code int8}-class quantization the old {@code .litertlm}
 * catalog used — not the smaller {@code Q4_K_M}/{@code IQ3_M} variants those same repos also
 * publish, which would trade quality for a smaller download.
 *
 * <p><b>Gated status.</b> No gating/license-click-through language was found for any of the six
 * repos during this session's searches — all six are marked {@code gated = false}. As with the
 * pre-Phase-6 {@link #GEMMA_4_E2B} note this replaces: "no gate text found by a search" is not
 * the same claim as "a real download will return 200 and not 401" — {@link LocalModelDownloader}
 * already has working gated-model handling (fails fast with a clear message) for the day this
 * assumption turns out wrong for a specific entry.
 *
 * <p>Device-tier guidance ({@link #minRamGb}) is a recommendation, not a hard block —
 * {@link LocalModelManager} shows a warning below the recommended threshold but still
 * lets the user proceed, per the phase requirement that the final call belongs to the user.
 */
public enum LocalModelCatalog {

    QWEN3_0_6B(
            "qwen3-0.6b",
            "Qwen3 0.6B",
            "Qwen/Qwen3-0.6B-GGUF",
            "qwen3-0.6b-q8_0.gguf",
            "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/main/qwen3-0.6b-q8_0.gguf",
            639L * 1024 * 1024, // 639 MB — confirmed via search against the user-opened repo
            3,
            DeviceTier.LOW_END,
            false, // ungated — public download, official Qwen org, Apache-2.0
            "Lightest model here — very fast, but limited on coding and complex tasks. "
                    + "Good for short replies and simple edits."
    ),

    QWEN2_5_1_5B_INSTRUCT(
            "qwen2.5-1.5b-instruct",
            "Qwen2.5 1.5B Instruct",
            "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            "qwen2.5-1.5b-instruct-q8_0.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q8_0.gguf",
            1890L * 1024 * 1024, // ~1.89 GB — confirmed via search, cross-checked against the
                                  // bartowski build of the same params/quant (same size, as expected)
            6,
            DeviceTier.MID_RANGE,
            false, // ungated — public download, official Qwen org, Apache-2.0
            "★ Recommended default. Best balance of quality and size — strongest here at "
                    + "multi-step coding instructions."
    ),

    DEEPSEEK_R1_DISTILL_QWEN_1_5B(
            "deepseek-r1-distill-qwen-1.5b",
            "DeepSeek-R1-Distill-Qwen 1.5B",
            "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
            "DeepSeek-R1-Distill-Qwen-1.5B-Q8_0.gguf",
            "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q8_0.gguf",
            1890L * 1024 * 1024, // ~1.89 GB — a direct .../blob/main/<filename> search hit
                                  // confirmed both the exact filename and this size
            6,
            DeviceTier.MID_RANGE,
            false, // ungated — public download, MIT license
            "DeepSeek's R1 reasoning distilled onto a Qwen base — shows its reasoning step by "
                    + "step, which helps on multi-step logic but tends to run longer than the "
                    + "Qwen entries above before finishing a response."
    ),

    QWEN2_0_5B_INSTRUCT(
            "qwen2-0.5b-instruct",
            "Qwen2 0.5B Instruct",
            "Qwen/Qwen2-0.5B-Instruct-GGUF",
            "qwen2-0.5b-instruct-q8_0.gguf",
            "https://huggingface.co/Qwen/Qwen2-0.5B-Instruct-GGUF/resolve/main/qwen2-0.5b-instruct-q8_0.gguf",
            531L * 1024 * 1024, // 531 MB — confirmed via search against the user-opened repo
            3,
            DeviceTier.LOW_END,
            false, // ungated — public download, official Qwen org, Apache-2.0
            "Lighter and less capable than Qwen2.5 1.5B Instruct above — good fallback for "
                    + "low-RAM devices."
    ),

    PHI4_MINI_INSTRUCT(
            "phi-4-mini-instruct",
            "Phi-4-mini Instruct",
            "bartowski/microsoft_Phi-4-mini-instruct-GGUF",
            "microsoft_Phi-4-mini-instruct-Q8_0.gguf",
            "https://huggingface.co/bartowski/microsoft_Phi-4-mini-instruct-GGUF/resolve/main/microsoft_Phi-4-mini-instruct-Q8_0.gguf",
            4080L * 1024 * 1024, // 4.08 GB — a direct .../blob/main/<filename> search hit
                                  // confirmed both the exact filename and this size
            8,
            DeviceTier.HIGH_END,
            false, // ungated — public download, MIT license (Microsoft)
            "★ Strongest model in this catalog by parameter count (3.8B, Microsoft). Best raw "
                    + "reasoning/coding quality here, but the largest download and the slowest to "
                    + "load — needs a higher-RAM device to run comfortably."
    ),

    GEMMA_4_E2B(
            "gemma-4-e2b-it",
            "Gemma 4 E2B",
            "bartowski/google_gemma-4-E2B-it-GGUF",
            "google_gemma-4-E2B-it-Q8_0.gguf",
            "https://huggingface.co/bartowski/google_gemma-4-E2B-it-GGUF/resolve/main/google_gemma-4-E2B-it-Q8_0.gguf",
            4970L * 1024 * 1024, // 4.97 GB — confirmed via search, which returned this repo's
                                  // full per-quantization size table (BF16 9.31 GB, Q8_0 4.97 GB,
                                  // Q6_K_L 4.56 GB, IQ3_M 3.16 GB, ...)
            10,
            DeviceTier.HIGH_END,
            false, // ungated — public download; no gate text found for this repo this session
            "Google's newest small on-device model. The Q8_0 GGUF build here is noticeably "
                    + "larger than the old LiteRT-LM export was, so it needs more RAM than its "
                    + "size class would otherwise suggest."
    );

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
     *  otherwise) — false for every entry currently in this catalog. Kept as a per-entry field
     *  rather than removed so a future gated model can be added without re-plumbing this flag
     *  through {@link LocalModelManager} and the download UI. */
    public boolean isGated() { return gated; }
    /** Capability/recommendation note shown under the model row in Settings. */
    public String getCapabilityNote() { return capabilityNote; }

    /**
     * Direct link to this model's page on huggingface.co. For gated models this is the
     * exact page the user must open (while logged in) to click "Agree"/"Accept" on the
     * license — searching the site for a model name does not reliably surface these repos
     * since org names don't always match a plain-text search.
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
