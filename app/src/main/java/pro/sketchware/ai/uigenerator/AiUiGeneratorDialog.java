package pro.sketchware.ai.uigenerator;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

import pro.sketchware.R;

/**
 * AiUiGeneratorDialog — Bottom Sheet dialog for the AI → UI Generator feature.
 *
 * <p>Flow:
 * <ol>
 *   <li>User types a prompt describing the desired screen</li>
 *   <li>Hits "Generate" → {@link AiUiGeneratorService} is called</li>
 *   <li>Status bar shows progress ("Sending…", "Validating…")</li>
 *   <li>Generated XML is displayed as a component list preview</li>
 *   <li>User can "Apply" to confirm or "Regenerate" to retry</li>
 * </ol>
 *
 * <p>The caller must implement {@link OnApplyListener} to receive the final XML.
 *
 * <p>Usage:
 * <pre>
 *   AiUiGeneratorDialog dialog = AiUiGeneratorDialog.newInstance();
 *   dialog.setOnApplyListener((xml, components) -> applyLayoutToEditor(xml));
 *   dialog.show(getSupportFragmentManager(), AiUiGeneratorDialog.TAG);
 * </pre>
 *
 * <p>FIXED: {@link AiUiGeneratorService.GenerationCallback#onSuccess} updated to match
 * the new 4-parameter signature introduced by the AIEngine refactor.
 */
public class AiUiGeneratorDialog extends BottomSheetDialogFragment {

    public static final String TAG = "AiUiGeneratorDialog";

    /** Callback for when the user presses "Apply" after preview. */
    public interface OnApplyListener {
        void onApply(String layoutXml, List<AiUiGeneratorService.UiComponent> components);
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private AiUiGeneratorService generatorService;
    private OnApplyListener      applyListener;

    /** The last successfully generated XML (null if none yet). */
    private String                                   pendingXml;
    private List<AiUiGeneratorService.UiComponent>  pendingComponents;

    // ── Views ─────────────────────────────────────────────────────────────────

    private TextInputEditText         etPrompt;
    private TextInputLayout           tilPrompt;
    private MaterialButton            btnGenerate;
    private MaterialButton            btnApply;
    private MaterialButton            btnRegenerate;
    private CircularProgressIndicator progressIndicator;
    private TextView                  tvStatus;
    private LinearLayout              layoutPreview;
    private TextView                  tvPreviewTitle;
    private LinearLayout              layoutComponentList;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static AiUiGeneratorDialog newInstance() {
        return new AiUiGeneratorDialog();
    }

    public void setOnApplyListener(OnApplyListener listener) {
        this.applyListener = listener;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_ai_ui_generator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        generatorService = new AiUiGeneratorService(requireContext());
        bindViews(view);
        setupListeners();
        setPreviewVisible(false);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Expand the bottom sheet to half-screen on open
        View bottomSheet = getDialog() != null
                ? getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet)
                : null;
        if (bottomSheet != null) {
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }
    }

    @Override
    public void onDestroyView() {
        // Cancel any in-flight generation to avoid leaking callbacks
        if (generatorService != null) generatorService.cancel();
        super.onDestroyView();
    }

    // ── View binding ──────────────────────────────────────────────────────────

    private void bindViews(View root) {
        etPrompt            = root.findViewById(R.id.et_ai_prompt);
        tilPrompt           = root.findViewById(R.id.til_ai_prompt);
        btnGenerate         = root.findViewById(R.id.btn_ai_generate);
        btnApply            = root.findViewById(R.id.btn_ai_apply);
        btnRegenerate       = root.findViewById(R.id.btn_ai_regenerate);
        progressIndicator   = root.findViewById(R.id.progress_ai_gen);
        tvStatus            = root.findViewById(R.id.tv_ai_gen_status);
        layoutPreview       = root.findViewById(R.id.layout_ai_preview);
        tvPreviewTitle      = root.findViewById(R.id.tv_preview_title);
        layoutComponentList = root.findViewById(R.id.layout_component_list);
    }

    private void setupListeners() {
        etPrompt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                tilPrompt.setError(null);
                btnGenerate.setEnabled(s != null && s.toString().trim().length() > 5);
            }
        });

        btnGenerate.setEnabled(false);
        btnGenerate.setOnClickListener(v -> startGeneration());
        btnRegenerate.setOnClickListener(v -> startGeneration());
        btnApply.setOnClickListener(v -> applyResult());
    }

    // ── Generation ────────────────────────────────────────────────────────────

    private void startGeneration() {
        String prompt = etPrompt.getText() != null
                ? etPrompt.getText().toString().trim()
                : "";

        if (prompt.length() < 5) {
            tilPrompt.setError(getString(R.string.ai_ui_gen_error_prompt_too_short));
            return;
        }
        tilPrompt.setError(null);

        setLoadingState(true);
        setPreviewVisible(false);
        pendingXml        = null;
        pendingComponents = null;

        // FIXED: GenerationCallback now uses 4-param onSuccess to match AIEngine-refactored
        // AiUiGeneratorService. fromCache and wasAutoFixed are informational only here.
        generatorService.generate(prompt, new AiUiGeneratorService.GenerationCallback() {

            @Override
            public void onProgress(String statusMessage) {
                tvStatus.setText(statusMessage);
            }

            /**
             * FIX: Method signature updated to match the new interface:
             * onSuccess(String, List<UiComponent>, boolean fromCache, boolean wasAutoFixed)
             *
             * @param layoutXml    validated, auto-fixed Android XML
             * @param components   parsed views with android:id
             * @param fromCache    true if served from cache (no AI call was made)
             * @param wasAutoFixed true if XMLValidator applied automatic corrections
             */
            @Override
            public void onSuccess(String layoutXml,
                                  List<AiUiGeneratorService.UiComponent> components,
                                  boolean fromCache,
                                  boolean wasAutoFixed) {
                pendingXml        = layoutXml;
                pendingComponents = components;
                setLoadingState(false);

                // Optional: show a badge if result came from cache or was auto-fixed
                if (fromCache) {
                    tvStatus.setText("Loaded from cache");
                } else if (wasAutoFixed) {
                    tvStatus.setText("Generated (auto-fixed)");
                } else {
                    tvStatus.setText(getString(R.string.ai_ui_gen_status_ready));
                }

                showPreview(layoutXml, components);
            }

            @Override
            public void onError(String errorMessage) {
                setLoadingState(false);
                tvStatus.setText(errorMessage);
                showSnackbar(errorMessage);
            }

            @Override
            public void onStreamingChunk(String chunk) {
                // Optional: could update a live token preview here
            }
        });
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    private void showPreview(String layoutXml,
                             List<AiUiGeneratorService.UiComponent> components) {
        tvPreviewTitle.setText(getString(R.string.ai_ui_gen_preview_title, components.size()));
        layoutComponentList.removeAllViews();

        Context ctx = requireContext();
        for (AiUiGeneratorService.UiComponent comp : components) {
            TextView chip = new TextView(ctx);
            chip.setText("• " + comp.type + "  @" + comp.id);
            chip.setTextSize(13f);
            int pad = (int) (6 * ctx.getResources().getDisplayMetrics().density);
            chip.setPadding(0, pad / 2, 0, pad / 2);
            chip.setTextColor(
                    androidx.core.content.ContextCompat.getColor(ctx, R.color.color_primary));
            layoutComponentList.addView(chip);
        }

        setPreviewVisible(true);
    }

    // ── Apply ─────────────────────────────────────────────────────────────────

    private void applyResult() {
        if (pendingXml == null) {
            showSnackbar(getString(R.string.ai_ui_gen_error_nothing_to_apply));
            return;
        }
        if (applyListener != null) {
            applyListener.onApply(pendingXml, pendingComponents);
        }
        dismiss();
    }

    // ── State helpers ─────────────────────────────────────────────────────────

    private void setLoadingState(boolean loading) {
        progressIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnGenerate.setEnabled(!loading);
        btnRegenerate.setEnabled(!loading && pendingXml != null);
        btnApply.setEnabled(!loading && pendingXml != null);
    }

    private void setPreviewVisible(boolean visible) {
        layoutPreview.setVisibility(visible ? View.VISIBLE : View.GONE);
        btnApply.setVisibility(visible ? View.VISIBLE : View.GONE);
        btnRegenerate.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showSnackbar(String message) {
        View root = getView();
        if (root != null) Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
    }
}
