// nikit overhaul — Tasks 4 5 6 7 — 2026-05
package pro.sketchware.ai.bottomsheet;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.activities.AiSettingsActivity;
import pro.sketchware.ai.adapters.ChatAdapter;
import pro.sketchware.ai.adapters.ModelSelectorAdapter;
import pro.sketchware.ai.engine.AgentExecutor;
import pro.sketchware.ai.integration.AiProjectIntegrationHelper;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.Conversation;
import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.ai.models.AiProviderModels;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.models.Workspace;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.storage.ConversationManager;
import pro.sketchware.databinding.DialogModelSelectorBinding;
import com.google.android.material.tabs.TabLayout;
import pro.sketchware.ai.storage.WorkspaceManager;

/**
 * AI Assistant panel embedded inside DesignActivity.
 *
 * Layout is a vertical LinearLayout (no BottomSheetBehavior needed):
 *   [Handle row — drag target]
 *   [Header 52dp fixed]
 *   [Divider]
 *   [Messages RecyclerView  weight=1  ← shrinks when keyboard appears]
 *   [Input bar  wrap_content          ← always visible above keyboard]
 *
 * Three snap states:
 *   HIDDEN   → fully below screen
 *   HALF     → ~60 % of sheet visible  (≈ 50 % of parent height)
 *   EXPANDED → fully visible (sheet top at translationY=0)
 *
 * Drag: the handle row supports both fling AND live drag (finger tracking),
 * so the user can pull the sheet up/down smoothly.
 *
 * Keyboard: ViewTreeObserver measures visible window frame.
 * When keyboard opens the sheet translates up by keyboard height.
 */
public class AiProjectBottomSheet
        implements AgentExecutor.AgentCallback, ChatAdapter.OnArtifactActionListener {

    public static final int STATE_HIDDEN   = 0;
    public static final int STATE_HALF     = 1;
    public static final int STATE_EXPANDED = 2;

    private static final int ANIM_MS = 280;

    private final Context context;
    private final String  scId;

    private View    sheetRoot;
    private int     parentHeight;
    private int     currentState    = STATE_HIDDEN;
    private int     lastKeyboardH   = 0;

    // Views
    private RecyclerView      messagesList;
    private boolean           userScrolledUp = false;
    private TextView          titleView;
    /** @deprecated v5: replaced by ai_sheet_provider_name. Kept for compat. */
    @Deprecated
    private TextView          subtitleView;
    private TextView          modelChipView;
    private LinearLayout      typingIndicator;
    private TextView          typingText;
    private LinearLayout      emptyState;
    private TextInputEditText inputView;
    private MaterialButton    btnSend;

    // AI
    private ChatAdapter         chatAdapter;
    private AgentExecutor       agentExecutor;

    // ── v5 redesign fields ────────────────────────────────────────────────────
    /** Currently selected activity XML name (e.g. "main.xml"). Kept in sync with DesignActivity. */
    private String currentActivityXmlName  = null;
    /** Sidebar RecyclerView for tools. */
    private androidx.recyclerview.widget.RecyclerView toolsSidebarRv;
    /** Sidebar container view (width toggled for expand/collapse). */
    private android.view.ViewGroup sidebarContainer;
    /** Toggle button icon for sidebar. */
    private android.widget.ImageView sidebarToggleIcon;
    // contextBannerText removed — Task 5 (banner replaced by undo button in header)
    /** Undo last AI layout change button. */
    private android.view.View undoLayoutBtn;

    // ── Task 6: Main-thread handler for voice partial results ─────────────────
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // ── Task 6: Mic button ref for icon state changes ─────────────────────────
    private com.google.android.material.button.MaterialButton micButton;
    /** Sidebar adapter. */
    private SidebarToolsAdapter sidebarAdapter;
    /** True when sidebar is in expanded (label-visible) state. */
    private boolean sidebarExpanded = true;  // open by default

    /** Voice input launcher — set by DesignActivity (same pattern as fileLauncher). */
    private androidx.activity.result.ActivityResultLauncher<Intent> voiceLauncher;
    /** Activity spinner row. */
    private android.view.View activitySelectorRow;
    /** Current activity name label. */
    private android.widget.TextView activityNameView;
    /** Provider name label. */
    private android.widget.TextView providerNameView;
    private AiPreferences       preferences;
    private ConversationManager conversationManager;
    private WorkspaceManager    workspaceManager;

    private String       workspaceId;
    private String       conversationId;
    private Workspace    workspace;
    private Conversation conversation;
    private AiProvider   currentProvider;
    private String       currentModelId;
    private boolean      isAgentRunning = false;

    // Speech + file picker bridge
    private SpeechRecognizer speechRecognizer;
    private androidx.activity.result.ActivityResultLauncher<Intent> fileLauncher;

    // ─────────────────────────────────────────────────────────────────────────

    public AiProjectBottomSheet(@NonNull Context context, @NonNull String scId) {
        this.context = context;
        this.scId    = scId;
    }

    /** Inflate and attach the sheet to parent. Call from DesignActivity.onCreate(). */
    public void attachToParent(@NonNull ViewGroup parent, int parentHeight) {
        this.parentHeight = parentHeight;

        sheetRoot = android.view.LayoutInflater.from(context)
                .inflate(R.layout.design_ai_bottom_sheet, parent, false);

        // Fixed height = 82 % of parent; positioned at bottom via RelativeLayout params
        int sheetH = (int) (parentHeight * 0.82f);

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, sheetH);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

        // Start off-screen
        sheetRoot.setTranslationY(sheetH);
        parent.addView(sheetRoot, lp);

        applyBackground();
        bindViews();
        setupToolsSidebar();
        setupAi();
        setupInput();
        setupButtons();
        setupDragAndSwipe();
        setupKeyboardListener();
        setupScrollToBottomFab();
    }

    // ── Background ────────────────────────────────────────────────────────

    private void applyBackground() {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorSurface, tv, true);
        sheetRoot.setBackgroundColor(tv.data);
        sheetRoot.setClipToOutline(true);
        sheetRoot.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                float r = v.getResources().getDisplayMetrics().density * 20;
                o.setRoundRect(0, 0, v.getWidth(), (int)(v.getHeight() + r), r);
            }
        });
    }

    // ── Bind views ────────────────────────────────────────────────────────

    private void bindViews() {
        titleView            = sheetRoot.findViewById(R.id.ai_sheet_title);
        modelChipView        = sheetRoot.findViewById(R.id.ai_sheet_model_chip);
        typingIndicator      = sheetRoot.findViewById(R.id.ai_sheet_typing_indicator);
        typingText           = sheetRoot.findViewById(R.id.ai_sheet_typing_text);
        emptyState           = sheetRoot.findViewById(R.id.ai_sheet_empty);
        inputView            = sheetRoot.findViewById(R.id.ai_sheet_input);
        btnSend              = sheetRoot.findViewById(R.id.ai_sheet_btn_send);
        messagesList         = sheetRoot.findViewById(R.id.ai_sheet_messages);
        // v5 redesign views
        activitySelectorRow  = sheetRoot.findViewById(R.id.ai_sheet_activity_selector);
        activityNameView     = sheetRoot.findViewById(R.id.ai_sheet_activity_name);
        providerNameView     = sheetRoot.findViewById(R.id.ai_sheet_provider_name);
        sidebarContainer     = sheetRoot.findViewById(R.id.ai_sidebar_container);
        toolsSidebarRv       = sheetRoot.findViewById(R.id.ai_tools_sidebar_rv);
        sidebarToggleIcon    = sheetRoot.findViewById(R.id.ai_sidebar_toggle_icon);
        undoLayoutBtn        = sheetRoot.findViewById(R.id.ai_btn_undo_layout);

        // Undo button: trigger Sketchware's built-in undo via broadcast
        if (undoLayoutBtn != null) {
            undoLayoutBtn.setOnClickListener(v -> {
                try {
                    android.content.Intent undoIntent =
                            new android.content.Intent("pro.sketchware.ACTION_UNDO_LAYOUT");
                    undoIntent.putExtra("sc_id", scId);
                    context.sendBroadcast(undoIntent);
                    undoLayoutBtn.setVisibility(android.view.View.GONE);
                } catch (Exception ignored) {}
            });
        }

        // v6: title = sc_id (project number) — as requested
        titleView.setText(scId);

        // Populate current activity chip
        updateActivityChip(currentActivityXmlName);

        // Setup activity selector dropdown
        if (activitySelectorRow != null) {
            activitySelectorRow.setOnClickListener(v -> showActivitySelector());
        }

        // Setup sidebar toggle
        android.view.View toggleRow = sheetRoot.findViewById(R.id.ai_sidebar_toggle);
        if (toggleRow != null) {
            toggleRow.setOnClickListener(v -> toggleSidebar());
        }

        // Setup quick-suggestion chips on empty state
        setupSuggestionChips();
    }

    /** Shows a dialog to pick the target activity. */
    private void showActivitySelector() {
        // Read activities from disk via ActivityTools-style file read
        java.util.List<String> actNames = loadProjectActivityNames();
        if (actNames.isEmpty()) {
            actNames.add("main");
        }
        String[] items = actNames.toArray(new String[0]);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Select screen to target")
                .setItems(items, (d, which) -> {
                    String picked = items[which];
                    setCurrentActivityXmlName(picked.endsWith(".xml") ? picked : picked + ".xml");
                })
                .show();
    }

    /** Reads activity names from Sketchware's "file" data file. */
    /**
     * Loads activity names from Sketchware's in-memory FileManager (jC.b(sc_id)).
     * This is the correct approach — jC is already loaded and avoids any
     * encrypted file parsing issues.
     */
    /**
     * Returns the list of activity/screen names from Sketchware's in-memory FileManager.
     * Uses jC.b(sc_id).b() — exactly the same API as ViewSelectorActivity.
     */
    private java.util.List<String> loadProjectActivityNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        try {
            // Activities
            java.util.ArrayList<com.besome.sketch.beans.ProjectFileBean> activities =
                    a.a.a.jC.b(scId).b();
            if (activities != null) {
                for (com.besome.sketch.beans.ProjectFileBean bean : activities) {
                    if (bean != null && bean.fileName != null && !bean.fileName.isEmpty()) {
                        names.add(bean.fileName);
                    }
                }
            }
            // Custom views / fragments
            java.util.ArrayList<com.besome.sketch.beans.ProjectFileBean> customs =
                    a.a.a.jC.b(scId).c();
            if (customs != null) {
                for (com.besome.sketch.beans.ProjectFileBean bean : customs) {
                    if (bean != null && bean.fileName != null && !bean.fileName.isEmpty()) {
                        names.add(bean.fileName);
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.w("AiBottomSheet", "loadProjectActivityNames: " + e.getMessage());
        }
        if (names.isEmpty()) names.add("main");
        return names;
    }

    /** Updates the activity chip text to reflect the currently targeted screen. */
    private void updateActivityChip(String xmlName) {
        String actName = (xmlName == null || xmlName.isEmpty()) ? "main" : xmlName.replace(".xml", "");
        if (activityNameView != null) activityNameView.setText(actName);
        // contextBannerText removed — Task 5
    }

    /** Shows the Undo button in the context banner after an AI layout change. */
    public void showUndoButton() {
        if (undoLayoutBtn != null) {
            undoLayoutBtn.setVisibility(android.view.View.VISIBLE);
        }
    }

    /** Toggle sidebar between icon-only (48dp) and expanded (220dp). */
    private void toggleSidebar() {
        sidebarExpanded = !sidebarExpanded;
        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(
                sidebarExpanded ? dpToPx(48) : dpToPx(220),
                sidebarExpanded ? dpToPx(220) : dpToPx(48));
        anim.setDuration(220);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        anim.addUpdateListener(va -> {
            android.view.ViewGroup.LayoutParams lp = sidebarContainer.getLayoutParams();
            lp.width = (int) va.getAnimatedValue();
            sidebarContainer.setLayoutParams(lp);
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (sidebarAdapter != null) sidebarAdapter.setSidebarExpanded(sidebarExpanded);
                if (sidebarToggleIcon != null) {
                    sidebarToggleIcon.setRotation(sidebarExpanded ? 180f : 0f);
                }
            }
        });
        anim.start();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /** Wires the quick-suggestion chips on the empty state. */
    private void setupSuggestionChips() {
        if (emptyState == null) return;
        bindChip(R.id.ai_chip_generate_ui,
                "Generate a modern UI for the current screen");
        bindChip(R.id.ai_chip_fix_bugs,
                "Fix build errors and review the code");
        bindChip(R.id.ai_chip_add_feature,
                "Add a new feature to the current screen");
        bindChip(R.id.ai_chip_explain,
                "Explain the code on the current screen");
    }

    private void bindChip(int chipId, String prompt) {
        android.view.View chip = emptyState.findViewById(chipId);
        if (chip == null) return;
        chip.setOnClickListener(v -> {
            if (inputView != null) {
                inputView.setText(prompt);
                inputView.setSelection(inputView.getText().length());
            }
        });
    }

    /** Initialises the tools sidebar RecyclerView. */
    private void setupToolsSidebar() {
        if (toolsSidebarRv == null) return;
        java.util.List<SidebarToolsAdapter.CategoryEntry> cats =
                SidebarToolsAdapter.buildCategories();
        sidebarAdapter = new SidebarToolsAdapter(context, cats);
        sidebarAdapter.setSidebarExpanded(true);  // labels visible from the start
        sidebarAdapter.setOnToolClickListener(tool -> {
            // Auto-trigger tools bypass the input field and run immediately
            if ("analyze_build_error".equals(tool.name) || "check_project_health".equals(tool.name)) {
                triggerErrorRepairMode("analyze_build_error".equals(tool.name));
            } else {
                // All other tools: fill input field so user can review before sending
                String prompt = buildToolPrompt(tool);
                if (prompt != null && inputView != null) {
                    inputView.setText(prompt);
                    inputView.setSelection(inputView.getText().length());
                }
            }
            // Collapse sidebar so the full chat area is visible after selecting a tool
            if (sidebarExpanded) toggleSidebar();
        });
        toolsSidebarRv.setLayoutManager(
                new androidx.recyclerview.widget.LinearLayoutManager(context));
        toolsSidebarRv.setAdapter(sidebarAdapter);

        // Apply open state immediately (220dp, labels visible)
        if (sidebarContainer != null) {
            android.view.ViewGroup.LayoutParams lp = sidebarContainer.getLayoutParams();
            lp.width = dpToPx(220);
            sidebarContainer.setLayoutParams(lp);
        }
        if (sidebarToggleIcon != null) sidebarToggleIcon.setRotation(180f);
    }

    /** Builds a context-aware prompt for a sidebar tool click. */
    private String buildToolPrompt(SidebarToolsAdapter.ToolEntry tool) {
        String actName = currentActivityXmlName != null
                ? currentActivityXmlName.replace(".xml", "") : "main";
        switch (tool.name) {
            case "generate_layout":
                // Don't put text in input — trigger the inline Dialog-style generator directly
                mainHandler.postDelayed(() -> showInlineLayoutGenerator(actName), 100);
                return null;  // signal: don't fill input

            case "edit_layout":
                // Edit Layout: first read current layout, then let user describe the edit
                mainHandler.postDelayed(() -> showInlineLayoutEditor(actName), 100);
                return null;
            case "describe_layout":
                return "Describe the current layout of the '" + actName + "' screen";
            case "add_view_xml":
                return "Add the following view to the '" + actName + "' screen: ";
            case "add_view":
                return "Add a new view to the '" + actName + "' screen. View type: , id: , text: ";
            case "modify_view":
                return "Modify the view with id '' on the '" + actName + "' screen. Change: ";
            case "remove_view":
                return "Remove the view with id '' from the '" + actName + "' screen";
            case "get_activity_events":
                return "List all events on the '" + actName + "' screen";
            case "get_event_blocks":
                return "Show the logic blocks for event '' on '" + actName + "'";
            case "add_block":
                return "Add a block to event '' on '" + actName + "': ";
            case "build_project":
                return "Build the project and fix any errors";
            case "build_project_clean":
                return "Build the project with a clean cache (use when there are unexplained build errors)";
            case "analyze_build_error":
                return null;  // handled by triggerErrorRepairMode()
            case "get_compile_logs":
                return "Show the latest compile logs and fix any errors";
            case "check_project_health":
                return null;  // handled by triggerErrorRepairMode()
            case "analyze_code":
                return "Analyze the code quality and suggest improvements";
            case "create_drawable":
                return "Create a drawable resource. Name: , template: rounded_button, fill_color: #";
            case "extract_strings":
                return "Scan the '" + actName + "' screen for hardcoded strings and move them all to strings.xml";
            case "create_locale_strings":
                return "Translate the app strings to Arabic (ar) — read all strings first, then translate and create values-ar/strings.xml";
            default:
                return "Use " + tool.name + " to help me: ";
        }
    }

    // ── Keyboard handling ─────────────────────────────────────────────────
    /**
     * Keyboard-awareness — two-step fix:
     *
     * 1. The sheet root gets a bottom padding equal to the IME height so that the
     *    inner LinearLayout's content (including the input bar) is pushed UP inside
     *    the fixed-height container and stays visible.
     *
     * 2. The sheet itself is also animated upward so the full input area clears the
     *    soft keyboard edge.
     *
     * This avoids the previous bug where the sheet translated up but the input bar
     * was still hidden because the CONTENT inside the fixed-height sheet didn't move.
     */
    private void setupKeyboardListener() {
        sheetRoot.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (sheetRoot == null || sheetRoot.getParent() == null) return;

                Rect visibleFrame = new Rect();
                sheetRoot.getRootView().getWindowVisibleDisplayFrame(visibleFrame);

                int screenH   = sheetRoot.getRootView().getHeight();
                int keyboardH = screenH - visibleFrame.bottom;

                // Clamp: navigation bar inset is usually < 150 px — ignore it
                if (keyboardH < 0) keyboardH = 0;
                if (Math.abs(keyboardH - lastKeyboardH) < 50) return;
                lastKeyboardH = keyboardH;

                float baseY = targetTranslationY(currentState);

                if (keyboardH > 150) {
                    // Step 1 — push content inside the sheet up via padding
                    sheetRoot.setPadding(
                            sheetRoot.getPaddingLeft(),
                            sheetRoot.getPaddingTop(),
                            sheetRoot.getPaddingRight(),
                            keyboardH);

                    // Step 2 — also slide the whole sheet up so the top of
                    // the keyboard doesn't overlap the input bar
                    float newY = baseY - keyboardH;
                    // Don't go above screen top
                    if (newY < 0) newY = 0;
                    sheetRoot.animate()
                            .translationY(newY)
                            .setDuration(180)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                } else {
                    // Keyboard closed — remove padding and restore position
                    sheetRoot.setPadding(
                            sheetRoot.getPaddingLeft(),
                            sheetRoot.getPaddingTop(),
                            sheetRoot.getPaddingRight(),
                            0);
                    sheetRoot.animate()
                            .translationY(baseY)
                            .setDuration(180)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
            }
        });
    }

    // ── Snap animation ────────────────────────────────────────────────────

    private int sheetHeight() {
        return sheetRoot.getHeight() > 0 ? sheetRoot.getHeight()
                : (int) (parentHeight * 0.82f);
    }

    private float targetTranslationY(int state) {
        int h = sheetHeight();
        switch (state) {
            // HALF: 40 % of sheet height = sheet peek is 60 % visible
            // This means ~50 % of parent is occupied (0.82 * 0.60 ≈ 0.49)
            case STATE_HALF:     return h * 0.40f;
            case STATE_EXPANDED: return 0f;
            default:             return h;  // fully hidden
        }
    }

    public void animateTo(int state) {
        currentState = state;
        lastKeyboardH = 0;
        float target = targetTranslationY(state);
        ValueAnimator anim = ValueAnimator.ofFloat(sheetRoot.getTranslationY(), target);
        anim.setDuration(ANIM_MS);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> sheetRoot.setTranslationY((float) a.getAnimatedValue()));
        anim.start();
    }

    // ── Drag + Swipe gesture on handle row ────────────────────────────────

    private void setupDragAndSwipe() {
        View handleRow = sheetRoot.findViewById(R.id.ai_sheet_handle_row);

        // Tap: toggle between states
        handleRow.setOnClickListener(v -> toggle());

        // Track drag start Y and current translationY when drag begins
        final float[] dragStartY     = {0f};
        final float[] sheetStartTransY = {0f};
        final boolean[] isDragging   = {false};

        GestureDetector gestureDetector = new GestureDetector(context,
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                    if (e1 == null || e2 == null) return false;
                    float dy = e2.getRawY() - e1.getRawY();
                    if (Math.abs(dy) < 40 || Math.abs(vY) < 100) return false;
                    if (dy < 0) { // swipe up
                        if      (currentState == STATE_HIDDEN) animateTo(STATE_HALF);
                        else if (currentState == STATE_HALF)   animateTo(STATE_EXPANDED);
                    } else {     // swipe down
                        if      (currentState == STATE_EXPANDED) animateTo(STATE_HALF);
                        else                                     animateTo(STATE_HIDDEN);
                    }
                    return true;
                }
            });

        handleRow.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);

            int h = sheetHeight();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartY[0]       = event.getRawY();
                    sheetStartTransY[0] = sheetRoot.getTranslationY();
                    isDragging[0]       = false;
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dy = event.getRawY() - dragStartY[0];
                    if (!isDragging[0] && Math.abs(dy) > 8) isDragging[0] = true;
                    if (isDragging[0]) {
                        float newY = sheetStartTransY[0] + dy;
                        // Clamp: don't allow going above top or below full-hidden
                        newY = Math.max(0f, Math.min(newY, (float) h));
                        sheetRoot.setTranslationY(newY);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging[0]) {
                        // Snap to nearest state based on final position
                        float finalY = sheetRoot.getTranslationY();
                        float halfY  = targetTranslationY(STATE_HALF);
                        float expandY = targetTranslationY(STATE_EXPANDED);
                        float hiddenY = (float) h;

                        // Determine closest snap point
                        float distHalf   = Math.abs(finalY - halfY);
                        float distExpand = Math.abs(finalY - expandY);
                        float distHidden = Math.abs(finalY - hiddenY);

                        int snapTo;
                        if (distExpand <= distHalf && distExpand <= distHidden) {
                            snapTo = STATE_EXPANDED;
                        } else if (distHalf <= distHidden) {
                            snapTo = STATE_HALF;
                        } else {
                            snapTo = STATE_HIDDEN;
                        }
                        animateTo(snapTo);
                    } else {
                        // It was a tap, not a drag — performClick handled by onClickListener
                        v.performClick();
                    }
                    isDragging[0] = false;
                    break;
            }
            return true;
        });
    }

    // ── Public controls ───────────────────────────────────────────────────

    public void toggle() {
        switch (currentState) {
            case STATE_HIDDEN:    animateTo(STATE_HALF);     break;
            case STATE_HALF:      animateTo(STATE_EXPANDED); break;
            default:              animateTo(STATE_HIDDEN);   break;
        }
    }

    public boolean isVisible()       { return currentState != STATE_HIDDEN; }
    public int     getCurrentState() { return currentState; }

    /** Call from DesignActivity to wire the file picker result back to this sheet. */
    public void setFileLauncher(
            @NonNull androidx.activity.result.ActivityResultLauncher<Intent> launcher) {
        this.fileLauncher = launcher;
    }

    /** Set by DesignActivity so mic uses RecognizerIntent dialog (same as all other pages). */
    public void setVoiceLauncher(
            @NonNull androidx.activity.result.ActivityResultLauncher<Intent> launcher) {
        this.voiceLauncher = launcher;
    }

    /** Called by DesignActivity when RecognizerIntent result arrives. */
    public void onVoiceResult(@NonNull String spokenText) {
        if (inputView != null && !spokenText.isEmpty()) {
            inputView.setText(spokenText);
            inputView.setSelection(spokenText.length());
        }
    }

    /** Called by DesignActivity when a file was picked via fileLauncher. */
    public void onFileSelected(@NonNull Uri uri) {
        try {
            java.io.InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return;
            java.io.BufferedReader reader =
                    new java.io.BufferedReader(new java.io.InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            int charCount = 0;
            while ((line = reader.readLine()) != null && charCount < 8000) {
                sb.append(line).append("\n");
                charCount += line.length();
            }
            reader.close();
            String fileName = uri.getLastPathSegment();
            String fileContent = "```\n// File: " + fileName + "\n"
                    + sb.toString().trim() + "\n```";
            String current = inputView.getText() != null
                    ? inputView.getText().toString().trim() : "";
            inputView.setText(current.isEmpty() ? fileContent : current + "\n\n" + fileContent);
            // Toast removed: "File attached: " + fileName
        } catch (Exception e) {
            // Toast removed: "Could not read file: " + e.getMessage()
        }
    }

    // ── AI setup ──────────────────────────────────────────────────────────

    private void setupAi() {
        preferences         = AiPreferences.getInstance(context);
        conversationManager = new ConversationManager(context);
        workspaceManager    = new WorkspaceManager(context);

        workspace   = AiProjectIntegrationHelper.ensureProjectWorkspace(context, scId, null);
        workspaceId = workspace.getId();

        List<Conversation> existing = conversationManager.getConversationsForWorkspace(workspaceId);
        List<ChatMessage>  history  = new ArrayList<>();

        if (!existing.isEmpty()) {
            conversation   = existing.get(existing.size() - 1);
            conversationId = conversation.getId();
            history        = conversationManager.getMessages(conversationId);
        } else {
            conversation   = AiProjectIntegrationHelper.createConversation(
                    context, workspace, "New Chat");
            conversationId = conversation.getId();
        }

        currentProvider = preferences.getSelectedProvider();
        currentModelId  = preferences.getSelectedModel(currentProvider);
        if (currentModelId == null) {
            List<ModelInfo> m = preferences.getCachedModels(currentProvider);
            if (m != null && !m.isEmpty()) currentModelId = m.get(0).getId();
        }
        updateModelChip();

        LinearLayoutManager lm = new LinearLayoutManager(context);
        lm.setStackFromEnd(true);
        messagesList.setLayoutManager(lm);
        chatAdapter = new ChatAdapter();
        chatAdapter.setArtifactActionListener(this);
        messagesList.setAdapter(chatAdapter);

        if (!history.isEmpty()) {
            chatAdapter.setMessages(history);
            emptyState.setVisibility(View.GONE);
            scrollToBottom();
        } else {
            emptyState.setVisibility(View.VISIBLE);
        }
    }

    private void updateModelChip() {
        if (currentModelId == null) {
            if (modelChipView != null) modelChipView.setText("Model");
            if (providerNameView != null) providerNameView.setText("Select provider");
            return;
        }
        // Show compact model name (last segment after / or the full id if short)
        String label = currentModelId;
        if (label.contains("/")) label = label.substring(label.lastIndexOf('/') + 1);
        if (label.length() > 22) label = label.substring(0, 20) + "…";
        if (modelChipView != null) modelChipView.setText(label);
        if (providerNameView != null && currentProvider != null) {
            providerNameView.setText(currentProvider.getDisplayName());
        }
    }

    /**
     * Sets the currently open activity's XML name so the AI knows which
     * screen to target when the user asks for UI changes.
     * Call this from DesignActivity whenever projectFile changes.
     */
    public void setCurrentActivityXmlName(String xmlName) {
        if (xmlName == null || xmlName.isEmpty()) xmlName = "main.xml";
        if (xmlName.equals(this.currentActivityXmlName)) return;  // no change
        this.currentActivityXmlName = xmlName;
        updateActivityChip(xmlName);
        // Switch to the per-activity conversation (or create one)
        if (conversationManager != null && workspace != null) {
            switchToActivityConversation(xmlName);
        }
    }

    /**
     * Switches the chat area to the conversation dedicated to the given activity.
     * Each activity in the project gets its own conversation saved in the workspace.
     * Conversation title = activity name (e.g. "main").
     */
    private void switchToActivityConversation(String xmlName) {
        String actTitle = xmlName.replace(".xml", "");
        // Look for existing conversation for this activity
        List<Conversation> all = conversationManager.getConversationsForWorkspace(workspaceId);
        Conversation found = null;
        for (Conversation cv : all) {
            if (actTitle.equals(cv.getTitle())) {
                found = cv;
                break;
            }
        }
        if (found == null) {
            // Create new conversation named after the activity
            found = AiProjectIntegrationHelper.createConversation(context, workspace, actTitle);
        }
        conversation   = found;
        conversationId = conversation.getId();
        // Reload messages for this conversation
        List<ChatMessage> history = conversationManager.getMessages(conversationId);
        if (chatAdapter != null) {
            chatAdapter.setMessages(history);
            // For new (empty) conversations, show the system context banner
            if (history.isEmpty()) {
                showSystemContextMessage(xmlName);
            }
        }
        // Show/hide empty state
        if (emptyState != null) {
            emptyState.setVisibility(history.isEmpty()
                    ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        scrollToBottom();
    }

    // ── Input ─────────────────────────────────────────────────────────────

    private void setupInput() {
        inputView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                refreshSendState();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        refreshSendState();
    }

    private void setupButtons() {
        btnSend.setOnClickListener(v -> {
            if (isAgentRunning) stopAgent(); else sendMessage();
        });
        modelChipView.setOnClickListener(v -> showModelSelector());
        sheetRoot.findViewById(R.id.ai_sheet_btn_settings).setOnClickListener(v -> {
            Intent i = new Intent(context, AiSettingsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        });
        sheetRoot.findViewById(R.id.ai_sheet_btn_close)
                .setOnClickListener(v -> animateTo(STATE_HIDDEN));
        sheetRoot.findViewById(R.id.ai_sheet_handle_row)
                .setOnClickListener(v -> toggle());

        // Mic button — RecognizerIntent dialog (auto-language, same as all other pages)
        micButton = sheetRoot.findViewById(R.id.ai_sheet_btn_mic);
        if (micButton != null) micButton.setOnClickListener(v -> launchVoiceDialog());

        // Tools button — shows the AI tools catalogue
        View btnTools = sheetRoot.findViewById(R.id.ai_sheet_btn_tools);
        if (btnTools != null) {
            btnTools.setOnClickListener(v ->
                    pro.sketchware.ai.bottomsheet.AiToolsBottomSheet.show(context, tool -> {
                        String prompt = "Use the \"" + tool.name + "\" tool to help me: ";
                        inputView.setText(prompt);
                        inputView.setSelection(inputView.getText().length());
                        if (currentState == STATE_HIDDEN) animateTo(STATE_HALF);
                    })
            );
        }

        // Clear chat button (real visible button in header)
        View btnClearReal = sheetRoot.findViewById(R.id.ai_sheet_btn_clear_real);
        if (btnClearReal != null) {
            btnClearReal.setOnClickListener(v -> confirmClearSheet());
        }

        // FAB scroll-to-bottom
        com.google.android.material.floatingactionbutton.FloatingActionButton fabDown =
                sheetRoot.findViewById(R.id.ai_sheet_fab_scroll_down);
        if (fabDown != null) {
            fabDown.setOnClickListener(v -> scrollToBottom());
            setupFabScrollListener(fabDown);
        }

        // Clear button
        sheetRoot.findViewById(R.id.ai_sheet_btn_clear).setOnClickListener(v ->
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Clear conversation")
                .setMessage("This will delete all messages in this conversation. This cannot be undone.")
                .setPositiveButton("Clear", (d, w) -> {
                    conversationManager.deleteMessages(conversationId);
                    chatAdapter.setMessages(new ArrayList<>());
                    emptyState.setVisibility(View.VISIBLE);
                    // Toast removed: "Conversation cleared."
                })
                .setNegativeButton("Cancel", null)
                .show()
        );

        // Attach button — use fileLauncher bridge registered in DesignActivity
        sheetRoot.findViewById(R.id.ai_sheet_btn_attach).setOnClickListener(v -> {
            if (fileLauncher != null) {
                Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
                pickIntent.setType("*/*");
                pickIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "text/plain", "application/json", "text/x-java-source", "text/xml"
                });
                pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    fileLauncher.launch(Intent.createChooser(pickIntent, "Select file to attach"));
                } catch (android.content.ActivityNotFoundException e) {
                    // Toast removed: "No file manager found."
                }
            } else {
                // Toast removed: "File picker not ready yet."
            }
        });
    }

    // ── Voice input via RecognizerIntent dialog (preferred — same as all pages) ─

    private void launchVoiceDialog() {
        if (voiceLauncher != null) {
            // Preferred: RecognizerIntent shows OS dialog — auto-language
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            String langTag = java.util.Locale.getDefault().toLanguageTag();
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag);
            intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message…");
            try {
                voiceLauncher.launch(intent);
            } catch (Exception e) {
                startListening(); // fallback to inline SpeechRecognizer
            }
        } else {
            startListening(); // fallback: voiceLauncher not set yet
        }
    }

    // ── Direct SpeechRecognizer (fallback only) ───────────────────────────────

    private void startListening() {
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
                    activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)) {
                activity.requestPermissions(
                    new String[]{android.Manifest.permission.RECORD_AUDIO}, 0x1234);
                return;
            }
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return;
        stopListening();

        // Task 6: Show recording state on mic button
        if (micButton != null) micButton.setIconResource(R.drawable.ic_mic_voice);

        android.content.Context speechCtx = (context instanceof android.app.Activity)
                ? context : context.getApplicationContext();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(speechCtx);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}

            // Task 6: Real-time partial results — text appears while speaking
            @Override
            public void onPartialResults(Bundle partialResults) {
                java.util.List<String> partial = partialResults.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial == null || partial.isEmpty()) return;
                String partialText = partial.get(0);
                if (partialText == null || partialText.isEmpty()) return;
                mainHandler.post(() -> {
                    if (inputView == null) return;
                    inputView.setText(partialText);
                    inputView.setSelection(partialText.length());
                });
            }

            // Task 6: Final result — replace text (don't append; partial already filled it)
            @Override
            public void onResults(Bundle results) {
                java.util.List<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spoken = matches.get(0);
                    if (spoken != null) {
                        mainHandler.post(() -> {
                            if (inputView == null) return;
                            inputView.setText(spoken);
                            inputView.setSelection(spoken.length());
                        });
                    }
                }
                stopListening();
                restoreMicIcon();
            }

            @Override
            public void onError(int error) {
                stopListening();
                restoreMicIcon();
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // Task 6: Multi-language — auto-switch between all installed device languages
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                java.util.Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                java.util.Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
        intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[0]);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizer.startListening(intent);
    }

    /** Task 6: Restores mic icon to default (not-recording) state on the main thread. */
    private void restoreMicIcon() {
        mainHandler.post(() -> {
            if (micButton != null) micButton.setIconResource(R.drawable.ic_mtrl_mic);
        });
    }

    private void stopListening() {
        if (speechRecognizer != null) {
            try {
                // Task 6: cancel() before destroy() prevents memory leak / ANR
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        restoreMicIcon();
    }

    private void refreshSendState() {
        if (isAgentRunning) {
            btnSend.setEnabled(true);
            btnSend.setIconResource(R.drawable.ic_mtrl_cancel);
        } else {
            boolean has = inputView.getText() != null && inputView.getText().length() > 0;
            btnSend.setEnabled(has);
            btnSend.setIconResource(R.drawable.ic_send);
        }
    }

    // ── Send / Stop ───────────────────────────────────────────────────────

    private void sendMessage() {
        if (inputView.getText() == null) return;
        String text = inputView.getText().toString().trim();
        if (text.isEmpty() || isAgentRunning) return;

        // ── Task 7: Guard against sending bare edit-mode prefix without a description ──
        if (isAwaitingLayoutPrompt) {
            String actName = currentActivityXmlName != null
                    ? currentActivityXmlName.replace(".xml", "") : "";
            String prefix   = "Edit " + actName + ": ";
            String userDesc = text.startsWith(prefix)
                    ? text.substring(prefix.length()).trim()
                    : text.trim();
            if (userDesc.isEmpty()) {
                inputView.setError("Please describe the change you want to make");
                inputView.requestFocus();
                return;  // keep isAwaitingLayoutPrompt = true
            }
            // Strip prefix — use only the user's description
            text = userDesc;

            isAwaitingLayoutPrompt = false;
            inputView.setText("");
            pro.sketchware.ai.models.ChatMessage userMsg =
                    new pro.sketchware.ai.models.ChatMessage(conversationId, text);
            conversationManager.saveMessage(conversationId, userMsg);
            chatAdapter.addUserMessage(userMsg);
            scrollToBottom();
            handleLayoutGenerationPrompt(text);
            return;
        }

        if (currentModelId == null || currentModelId.isEmpty()) {
            // Toast removed: "Please select a model first"
            showModelSelector();
            return;
        }
        String apiKey = preferences.getApiKey(currentProvider);
        if (currentProvider.requiresApiKey() && (apiKey == null || apiKey.isEmpty())) {
            // Toast removed: "No API key for " + currentProvider.getDisplayName()
            Intent i = new Intent(context, AiSettingsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return;
        }

        if (currentState == STATE_HIDDEN) animateTo(STATE_HALF);
        inputView.setText("");
        emptyState.setVisibility(View.GONE);

        ChatMessage userMsg = new ChatMessage(conversationId, text);
        conversationManager.saveMessage(conversationId, userMsg);
        chatAdapter.addUserMessage(userMsg);
        scrollToBottom();

        if ("New Chat".equals(conversation.getTitle())) {
            conversation.setTitle(text.length() > 50
                    ? text.substring(0, 50) + "\u2026" : text);
            conversationManager.saveConversation(conversation);
        }

        ChatMessage placeholder = new ChatMessage(conversationId, "");
        placeholder.setStreaming(true);
        chatAdapter.addAssistantMessage(placeholder);
        scrollToBottom();

        setAgentRunning(true);

        List<ChatMessage> history    = conversationManager.getMessages(conversationId);
        List<String>      projectIds = workspace.getProjectIds();

        // Build rich page context so AI uses generate_layout (not Python/write_file)
        String pageCtx = buildDesignEditorContext(scId, currentActivityXmlName, projectIds);

        agentExecutor = new AgentExecutor(context, projectIds, workspaceId,
                AgentExecutor.SCOPE_PROJECT, scId);
        // Wire pulse: after every N tool steps, show Continue/Cancel with 10s countdown
        agentExecutor.setPulseCallback((stepSummary, onContinue, onCancel) ->
                showPulseConfirmation(stepSummary, onContinue, onCancel));
        agentExecutor.execute(history, currentModelId, currentProvider,
                preferences.getSystemPrompt(), projectIds, workspaceId, pageCtx, this);
    }

    /**
     * Directly triggers the AI agent in error-repair mode without requiring user input.
     * The agent receives an "error_repair" page context with mandatory tool-execution
     * instructions — it will call analyze_build_error → patch tools → build_project
     * automatically, with pulse checkpoints between stages.
     *
     * @param isBuildFix true = fix build errors; false = run project health check
     */
    private void triggerErrorRepairMode(boolean isBuildFix) {
        if (isAgentRunning) return;
        if (currentModelId == null || currentModelId.isEmpty()) {
            showModelSelector();
            return;
        }
        String apiKey = preferences.getApiKey(currentProvider);
        if (currentProvider.requiresApiKey() && (apiKey == null || apiKey.isEmpty())) {
            context.startActivity(new Intent(context, AiSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }

        if (currentState == STATE_HIDDEN) animateTo(STATE_HALF);
        if (inputView != null) inputView.setText("");
        if (emptyState != null) emptyState.setVisibility(View.GONE);

        String userText = isBuildFix
                ? "Fix all build errors automatically"
                : "Run a full health check and fix all issues";

        ChatMessage userMsg = new ChatMessage(conversationId, userText);
        conversationManager.saveMessage(conversationId, userMsg);
        chatAdapter.addUserMessage(userMsg);

        ChatMessage placeholder = new ChatMessage(conversationId, "");
        placeholder.setStreaming(true);
        chatAdapter.addAssistantMessage(placeholder);
        scrollToBottom();

        setAgentRunning(true);

        List<ChatMessage> history   = conversationManager.getMessages(conversationId);
        List<String>      projectIds = workspace.getProjectIds();

        // "error_repair" context triggers the mandatory repair pipeline in AgentExecutor
        String pageCtx = "error_repair"
                + "\nsc_id: " + (scId != null ? scId : "")
                + "\nmode: " + (isBuildFix ? "build_fix" : "health_check");

        agentExecutor = new AgentExecutor(context, projectIds, workspaceId,
                AgentExecutor.SCOPE_PROJECT, scId);
        agentExecutor.setPulseCallback((stepSummary, onContinue, onCancel) ->
                showPulseConfirmation(stepSummary, onContinue, onCancel));
        agentExecutor.execute(history, currentModelId, currentProvider,
                preferences.getSystemPrompt(), projectIds, workspaceId, pageCtx, this);
    }

    /**
     * Builds the design_editor page context string injected into the system prompt.
     * This tells the AI to use generate_layout (not write_file or Python) for UI edits.
     */
    private String buildDesignEditorContext(String scId, String xmlName, java.util.List<String> projectIds) {
        StringBuilder ctx = new StringBuilder("design_editor");
        ctx.append("\nsc_id: ").append(scId != null ? scId : "");
        if (xmlName != null && !xmlName.isEmpty()) {
            String actName = xmlName.replace(".xml", "");
            ctx.append("\ncurrent_activity: ").append(actName);
            ctx.append("\ncurrent_xml: ").append(xmlName);
        }
        ctx.append("\nproject_count: ")
           .append((projectIds != null && projectIds.size() == 1) ? "1" : "multiple");
        return ctx.toString();
    }

    /**
     * Shows the active system context as an informational assistant message at the
     * top of a new conversation — helps the user see what the AI knows and why
     * generate_layout (not Python/write_file) is the correct tool for UI edits.
     */
    private void showSystemContextMessage(String activityName) {
        if (chatAdapter == null) return;
        String actName = (activityName != null) ? activityName.replace(".xml", "") : "main";
        // Plain readable text — no markdown symbols (they render as ** ` etc. in some builds)
        pro.sketchware.ai.models.ChatMessage sysMsg =
                pro.sketchware.ai.models.ChatMessage.assistantMessage(
                    "📋 Ready for screen: " + actName + "\n"
                    + "Project: " + scId + "\n"
                    + "Tip: Use the sidebar to pick a tool, or just describe what you want.",
                    null);
        chatAdapter.addAssistantMessage(sysMsg);
    }


    // ─── Inline Layout Generator (Dialog flow inside BottomSheet) ──────────────

    private String pendingGeneratedXml = null;
    private volatile boolean isAwaitingLayoutPrompt = false;
    private boolean isEditMode = false;
    private String cachedCurrentLayout = null;

    private void showInlineLayoutGenerator(String actName) {
        if (inputView == null || chatAdapter == null) return;
        pendingGeneratedXml = null;
        isAwaitingLayoutPrompt = true;
        pro.sketchware.ai.models.ChatMessage promptMsg =
                pro.sketchware.ai.models.ChatMessage.assistantMessage(
                    "🎨 Generate Layout for: " + actName + "\n\n"
                    + "Describe what you want. Be specific about widgets and layout.\n\n"
                    + "Example: A calculator with a display at top and 4x4 button grid.",
                    null);
        chatAdapter.addAssistantMessage(promptMsg);
        scrollToBottom();
        inputView.setText("Layout for " + actName + ": ");
        inputView.setSelection(inputView.getText().length());
        inputView.requestFocus();
    }

    private void handleLayoutGenerationPrompt(String prompt) {
        String actName = currentActivityXmlName != null
                ? currentActivityXmlName.replace(".xml", "") : "main";
        mainHandler.post(() -> {
            if (typingText != null) typingText.setText("Generating layout…");
            if (typingIndicator != null) typingIndicator.setVisibility(android.view.View.VISIBLE);
        });
        new Thread(() -> {
            String xml = null; String error = null;
            try {
                xml = new pro.sketchware.ia.GeradorDeLayoutPro(context, prompt).generateLayout();
            } catch (Exception e) { error = e.getMessage(); }
            final String finalXml = xml; final String finalError = error;
            mainHandler.post(() -> {
                if (typingIndicator != null)
                    typingIndicator.setVisibility(android.view.View.GONE);
                if (finalError != null || finalXml == null || finalXml.isEmpty()) {
                    chatAdapter.addAssistantMessage(
                        pro.sketchware.ai.models.ChatMessage.assistantMessage(
                            "❌ Generation failed: " + (finalError != null ? finalError
                                : "empty result") + "\n\nTry a more detailed description.", null));
                    scrollToBottom(); return;
                }
                pendingGeneratedXml = finalXml;
                // Parse component names from XML
                java.util.List<String> comps = new java.util.ArrayList<>();
                try {
                    org.xmlpull.v1.XmlPullParserFactory fac =
                            org.xmlpull.v1.XmlPullParserFactory.newInstance();
                    fac.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                    org.xmlpull.v1.XmlPullParser p = fac.newPullParser();
                    p.setInput(new java.io.StringReader(finalXml));
                    int ev = p.getEventType();
                    while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        if (ev == org.xmlpull.v1.XmlPullParser.START_TAG) {
                            String id = p.getAttributeValue(null, "android:id");
                            if (id != null) {
                                String tag = p.getName();
                                int dot = tag.lastIndexOf('.');
                                comps.add("• " + (dot >= 0 ? tag.substring(dot+1) : tag)
                                        + "  @" + id.replace("@+id/", ""));
                            }
                        }
                        ev = p.next();
                    }
                } catch (Exception ignored) {}
                // Preview (mirrors AiUiGeneratorDialog.showPreview)
                StringBuilder sb = new StringBuilder();
                sb.append("✅ ").append(comps.size()).append(" views generated for ")
                  .append(actName).append("\n\n");
                for (String s : comps) sb.append(s).append("\n");
                sb.append("\nTap Apply to update the canvas.");
                chatAdapter.addAssistantMessage(
                        pro.sketchware.ai.models.ChatMessage.assistantMessage(
                                sb.toString(), null));
                showLayoutActionButtons(actName, prompt);
                scrollToBottom();
            });
        }, "GeradorLayout-BS").start();
    }

    private void showLayoutActionButtons(String actName, String originalPrompt) {
        if (sheetRoot == null) return;
        android.widget.LinearLayout row = new android.widget.LinearLayout(context);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        row.setTag("layout_action_row");

        com.google.android.material.button.MaterialButton btnApply =
                new com.google.android.material.button.MaterialButton(context);
        btnApply.setText("✅  Apply to Canvas");
        btnApply.setIconResource(0); // no icon — text only
        android.widget.LinearLayout.LayoutParams lp1 =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMarginEnd(dpToPx(8)); btnApply.setLayoutParams(lp1);

        com.google.android.material.button.MaterialButton btnRegen =
                new com.google.android.material.button.MaterialButton(context,
                        null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnRegen.setText("🔄  Retry");

        row.addView(btnApply); row.addView(btnRegen);

        android.view.View typingArea = sheetRoot.findViewById(R.id.ai_sheet_typing_indicator);
        if (typingArea != null && typingArea.getParent() instanceof android.view.ViewGroup) {
            android.view.ViewGroup parent = (android.view.ViewGroup) typingArea.getParent();
            android.view.View ex = parent.findViewWithTag("layout_action_row");
            if (ex != null) parent.removeView(ex);
            parent.addView(row, parent.indexOfChild(typingArea));
        }

        btnApply.setOnClickListener(v -> applyPendingLayout(actName, row));
        btnRegen.setOnClickListener(v -> {
            if (row.getParent() instanceof android.view.ViewGroup)
                ((android.view.ViewGroup) row.getParent()).removeView(row);
            pendingGeneratedXml = null;
            handleLayoutGenerationPrompt(originalPrompt);
        });
    }

    /**
     * Applies the pending XML via the exact same path as AiUiGeneratorDialog → DesignActivity:
     *   broadcast layout_xml → applyAiGeneratedLayoutToEditor()
     *   → ViewBeanParser.parse → InjectRootLayoutManager.set → HistoryViewBean.actionOverride
     *   → jC.a(sc_id).c.put → viewTabAdapter.i() + refreshViewTabAdapter()
     */
    private void applyPendingLayout(String actName, android.view.View actionRow) {
        if (pendingGeneratedXml == null) return;
        if (actionRow.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) actionRow.getParent()).removeView(actionRow);
        String xmlKey = actName.endsWith(".xml") ? actName : actName + ".xml";
        try {
            android.content.Intent i =
                    new android.content.Intent("pro.sketchware.ai.ACTION_LIVE_LAYOUT_RELOAD");
            i.putExtra("sc_id", scId);
            i.putExtra("activity_xml", xmlKey);
            i.putExtra("layout_xml", pendingGeneratedXml);
            context.sendBroadcast(i);
        } catch (Exception ignored) {}
        chatAdapter.addAssistantMessage(
            pro.sketchware.ai.models.ChatMessage.assistantMessage(
                "✅ Layout applied to " + actName + ". Canvas updated.\n"
                + "Use the Undo button ↩ in the banner to revert.", null));
        scrollToBottom();
        showUndoButton();
        pendingGeneratedXml = null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PULSE SYSTEM — Continue / Cancel after each step group
    // ─────────────────────────────────────────────────────────────────────────

    private android.os.CountDownTimer pulseCountdownTimer;
    private android.view.View pulseRowView;

    /**
     * Shows Continue / Cancel row with animated 10-second countdown.
     * If user doesn't tap Continue within 10s, auto-continues.
     * Called on main thread by AgentExecutor.
     */
    private void showPulseConfirmation(String stepSummary, Runnable onContinue, Runnable onCancel) {
        if (sheetRoot == null) return;

        // Remove any previous pulse row
        dismissPulseRow();

        android.widget.LinearLayout row = new android.widget.LinearLayout(context);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        row.setTag("pulse_row");
        pulseRowView = row;

        // Step label
        android.widget.TextView label = new android.widget.TextView(context);
        label.setText("✓ " + stepSummary);
        label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall);
        android.widget.LinearLayout.LayoutParams llp =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        llp.setMarginEnd(dpToPx(8));
        label.setLayoutParams(llp);

        // Continue button with countdown badge
        com.google.android.material.button.MaterialButton btnContinue =
                new com.google.android.material.button.MaterialButton(context);
        btnContinue.setText("Continue (10)");
        android.widget.LinearLayout.LayoutParams lp1 =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp1.setMarginEnd(dpToPx(6));
        btnContinue.setLayoutParams(lp1);

        // Cancel button
        com.google.android.material.button.MaterialButton btnCancel =
                new com.google.android.material.button.MaterialButton(context,
                        null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnCancel.setText("Stop");

        row.addView(label);
        row.addView(btnContinue);
        row.addView(btnCancel);

        // Insert above typing indicator
        android.view.View typingArea = sheetRoot.findViewById(R.id.ai_sheet_typing_indicator);
        if (typingArea != null && typingArea.getParent() instanceof android.view.ViewGroup) {
            android.view.ViewGroup parent = (android.view.ViewGroup) typingArea.getParent();
            parent.addView(row, parent.indexOfChild(typingArea));
        }

        // Countdown timer — auto continues after 10s
        pulseCountdownTimer = new android.os.CountDownTimer(10_000L, 1_000L) {
            @Override public void onTick(long ms) {
                long secs = ms / 1000L;
                btnContinue.setText("Continue (" + secs + ")");
            }
            @Override public void onFinish() {
                dismissPulseRow();
                onContinue.run();
            }
        }.start();

        btnContinue.setOnClickListener(v -> {
            if (pulseCountdownTimer != null) pulseCountdownTimer.cancel();
            dismissPulseRow();
            onContinue.run();
        });
        btnCancel.setOnClickListener(v -> {
            if (pulseCountdownTimer != null) pulseCountdownTimer.cancel();
            dismissPulseRow();
            onCancel.run();
        });
    }

    private void dismissPulseRow() {
        if (pulseCountdownTimer != null) { pulseCountdownTimer.cancel(); pulseCountdownTimer = null; }
        if (pulseRowView != null && pulseRowView.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) pulseRowView.getParent()).removeView(pulseRowView);
        }
        pulseRowView = null;
    }

    /**
     * Edit Layout mode: reads the current layout XML from jC memory,
     * shows it as context in the chat, then lets the user describe the change.
     * Next message is intercepted → GeradorDeLayoutPro(desc, currentLayout).
     */
    private void showInlineLayoutEditor(String actName) {
        if (inputView == null || chatAdapter == null) return;
        pendingGeneratedXml = null;
        isAwaitingLayoutPrompt = false;

        // Read current layout from jC
        String xmlName = actName.endsWith(".xml") ? actName : actName + ".xml";
        String currentXml = null;
        try {
            java.util.ArrayList<com.besome.sketch.beans.ViewBean> beans =
                    a.a.a.jC.a(scId).d(xmlName);
            if (beans != null && !beans.isEmpty()) {
                // Convert beans back to simple XML summary
                StringBuilder xmlSb = new StringBuilder();
                for (com.besome.sketch.beans.ViewBean b : beans) {
                    if ("root".equals(b.parent)) {
                        xmlSb.append("<").append(b.id).append("/>");
                    }
                }
                currentXml = xmlSb.length() > 0 ? xmlSb.toString() : null;
            }
        } catch (Exception ignored) {}

        final String capturedXml = currentXml;
        chatAdapter.addAssistantMessage(
                pro.sketchware.ai.models.ChatMessage.assistantMessage(
                    "✏️ Edit Layout: " + actName + "\n\n"
                    + (capturedXml != null
                        ? "Current views: " + capturedXml + "\n\n"
                        : "")
                    + "Describe the change you want (e.g. 'change the button color to blue' "
                    + "or 'add a search bar at the top').",
                    null));
        scrollToBottom();

        // Set edit mode — next message passes currentLayout to GeradorDeLayoutPro
        isAwaitingLayoutPrompt = true;
        isEditMode = capturedXml != null;
        cachedCurrentLayout = capturedXml;

        inputView.setText("Edit " + actName + ": ");
        inputView.setSelection(inputView.getText().length());
        inputView.requestFocus();
    }

    private void stopAgent() {
        dismissPulseRow();
        if (agentExecutor != null) agentExecutor.cancel();
        setAgentRunning(false);
    }

    private void setAgentRunning(boolean running) {
        isAgentRunning = running;
        inputView.setEnabled(!running);
        typingIndicator.setVisibility(running ? View.VISIBLE : View.GONE);
        refreshSendState();
    }

    // ── Model Selector ────────────────────────────────────────────────────

    private void showModelSelector() {
        List<AiProvider> availableProviders = new ArrayList<>();
        for (AiProvider p : AiProvider.values()) {
            if (!preferences.isProviderEnabled(p)) continue;
            if (!p.requiresApiKey() || preferences.hasApiKey(p)) availableProviders.add(p);
        }
        if (availableProviders.isEmpty()) {
            context.startActivity(new Intent(context, AiSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        DialogModelSelectorBinding db = DialogModelSelectorBinding.inflate(
                android.view.LayoutInflater.from(context));
        dialog.setContentView(db.getRoot());

        for (AiProvider p : availableProviders) {
            db.providerTabs.addTab(db.providerTabs.newTab().setText(p.getSelectorLabel()).setTag(p));
        }

        ModelSelectorAdapter modelAdapter = new ModelSelectorAdapter(model -> {
            currentProvider = model.getProvider();
            currentModelId  = model.getId();
            preferences.setSelectedModel(currentProvider, currentModelId);
            preferences.setSelectedProvider(currentProvider);
            updateModelChip();
            conversation.setModelId(currentModelId);
            conversation.setProviderName(currentProvider.name());
            conversationManager.saveConversation(conversation);
            dialog.dismiss();
        });
        modelAdapter.setOnModelLongClickListener(model -> {
            dialog.dismiss(); showModelInfo(model); return true;
        });
        modelAdapter.setSelectedModelId(currentModelId);
        db.modelsList.setAdapter(modelAdapter);

        AiProvider initial = availableProviders.get(0);
        for (int i = 0; i < availableProviders.size(); i++) {
            if (availableProviders.get(i) == currentProvider) {
                TabLayout.Tab tab = db.providerTabs.getTabAt(i);
                if (tab != null) { tab.select(); initial = currentProvider; }
                break;
            }
        }
        loadModelsForProvider(initial, modelAdapter, db);

        db.providerTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                AiProvider p = (AiProvider) tab.getTag();
                if (p != null) loadModelsForProvider(p, modelAdapter, db);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        dialog.show();
    }

    private void loadModelsForProvider(AiProvider provider, ModelSelectorAdapter adapter,
                                        DialogModelSelectorBinding db) {
        List<ModelInfo> cached = preferences.getCachedModels(provider);
        if (cached != null && !cached.isEmpty()) {
            adapter.setModels(cached);
            db.modelsList.setVisibility(android.view.View.VISIBLE);
            db.emptyState.setVisibility(android.view.View.GONE);
        } else {
            List<String> staticIds = AiProviderModels.getStaticModels(provider);
            if (!staticIds.isEmpty()) {
                List<ModelInfo> staticModels = new ArrayList<>();
                for (String id : staticIds) staticModels.add(new ModelInfo(id, id, provider, 0, null));
                adapter.setModels(staticModels);
                db.modelsList.setVisibility(android.view.View.VISIBLE);
                db.emptyState.setVisibility(android.view.View.GONE);
            } else {
                adapter.setModels(new ArrayList<>());
                db.modelsList.setVisibility(android.view.View.GONE);
                db.emptyState.setVisibility(android.view.View.VISIBLE);
                db.emptyText.setText("No models for " + provider.getSelectorLabel()
                        + ".\nRefresh in AI Settings ↻");
            }
        }
    }

    private void showModelInfo(ModelInfo model) {
        AiProvider p   = model.getProvider();
        String     name = (model.getName() != null && !model.getName().isEmpty())
                ? model.getName() : model.getId();
        StringBuilder sb = new StringBuilder();
        sb.append("Provider: ").append(p.getSelectorLabel()).append("\n\n");
        if (model.getContextLength() > 0) {
            long ctx = model.getContextLength();
            sb.append("Context: ").append(ctx >= 1000 ? (ctx/1000)+"k" : ctx)
              .append(" tokens\n\n");
        }
        sb.append(p.getDescription());
        new MaterialAlertDialogBuilder(context)
                .setTitle(name).setMessage(sb.toString())
                .setPositiveButton("Use this model", (d, w) -> {
                    currentProvider = p; currentModelId = model.getId();
                    preferences.setSelectedModel(currentProvider, currentModelId);
                    preferences.setSelectedProvider(currentProvider);
                    updateModelChip();
                    conversation.setModelId(currentModelId);
                    conversation.setProviderName(currentProvider.name());
                    conversationManager.saveConversation(conversation);
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ── AgentCallback ─────────────────────────────────────────────────────

    @Override public void onStreamingChunk(String chunk) {
        chatAdapter.updateLastAssistantMessage(chunk); scrollToBottom();
    }
    @Override public void onAssistantMessage(ChatMessage msg) {
        conversationManager.saveMessage(conversationId, msg);
        conversation.setUpdatedAt(System.currentTimeMillis());
        conversationManager.saveConversation(conversation);
        chatAdapter.replaceStreamingAssistantMessage(msg); scrollToBottom();
    }
    @Override public void onToolCallStarted(ToolCall tc) {
        chatAdapter.addToolCall(tc); scrollToBottom();
    }
    @Override public void onToolCallProgress(String id, String status,
                                             int progress, boolean indeterminate) {
        chatAdapter.updateToolCallProgress(id, status, progress, indeterminate);
        if (status != null && !status.isEmpty()) typingText.setText(status);
    }
    @Override public void onToolCallCompleted(ToolCall tc, ToolResult r) {
        chatAdapter.updateToolCallResult(tc.getId(), r); scrollToBottom();
        // When generate_layout / add_view_xml succeeds, show the Undo button
        if (r != null && r.isSuccess() && tc.getName() != null
                && (tc.getName().equals("generate_layout")
                    || tc.getName().equals("generate_layout_from_description")
                    || tc.getName().equals("add_view_xml"))) {
            showUndoButton();
        }
    }
    @Override public void onToolMessage(ChatMessage msg) {
        conversationManager.saveMessage(conversationId, msg);
    }
    @Override public void onResponseComplete(ChatMessage msg) { setAgentRunning(false); }
    @Override public void onCancelled() {
        dismissPulseRow();
        setAgentRunning(false);
        if (typingText != null) typingText.setText("Stopped");
    }
    @Override public void onError(String error) {
        dismissPulseRow();
        setAgentRunning(false);
        // Build user-friendly message — shown inline in the chat, no Toast
        String displayError = (error != null && !error.isEmpty()) ? error : "An unexpected error occurred.";
        String hint = null;
        if (displayError.contains("403") || displayError.contains("rate limit") || displayError.contains("Rate Limit"))
            hint = "\uD83D\uDCA1 Tip: Switch to Groq \u221e (unlimited) or AirForce \uD83C\uDD13 \u2014 tap the model chip.";
        else if (displayError.contains("401") || displayError.contains("Invalid API Key"))
            hint = "\uD83D\uDCA1 Check your API key in AI Settings.";
        else if (displayError.contains("timeout") || displayError.contains("failed to connect")
                || displayError.contains("Unable to resolve host"))
            hint = "\uD83D\uDCA1 Check your internet connection and try again.";
        else if (displayError.contains("404") || displayError.contains("Model Not Found"))
            hint = "\uD83D\uDCA1 The selected model may be unavailable. Try refreshing models in AI Settings.";
        else if (displayError.contains("503") || displayError.contains("Service Unavailable"))
            hint = "\uD83D\uDCA1 The AI provider is temporarily overloaded. Please try again in a moment.";

        StringBuilder msgBuilder = new StringBuilder("\u26a0\ufe0f ").append(displayError);
        if (hint != null) msgBuilder.append("\n\n").append(hint);

        ChatMessage err = new ChatMessage(conversationId, msgBuilder.toString());
        conversationManager.saveMessage(conversationId, err);
        chatAdapter.replaceStreamingAssistantMessage(err);
        scrollToBottom();
        // No Toast — error is displayed inline in the chat for a cleaner UX
    }
    @Override
    public void onThinking(String status) {
        if (typingText != null) typingText.setText(status);
    }

    @Override
    public void onFailover(String fromProvider, String toProvider, String toModel) {
        // Update the provider name + model chip in the header
        if (providerNameView != null) providerNameView.setText(toProvider);
        if (modelChipView != null) {
            String display = toModel != null && toModel.length() > 22
                    ? toModel.substring(0, 19) + "…" : toModel;
            modelChipView.setText(display != null ? display : toProvider);
        }
        // Post a visible switch notification in the chat
        ChatMessage note = new ChatMessage(conversationId,
                "⚡ Switched to " + toModel + " (" + toProvider + ")");
        chatAdapter.addAssistantMessage(note);
        scrollToBottom();
    }

    // ── OnArtifactActionListener ──────────────────────────────────────────

    @Override public void onInstallArtifact(@NonNull String artifactPath) {
        try {
            File apk = new File(artifactPath);
            Uri  uri = FileProvider.getUriForFile(
                    context, context.getPackageName() + ".provider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            context.startActivity(intent);
        } catch (Exception e) {
            // Show error inline in chat instead of Toast
            String errMsg = "\u26a0\ufe0f Cannot install APK: "
                    + (e.getMessage() != null ? e.getMessage() : "Unknown error")
                    + "\n\n\uD83D\uDCA1 Make sure you have allowed installing from unknown sources.";
            ChatMessage errChat = new ChatMessage(conversationId, errMsg);
            chatAdapter.addAssistantMessage(errChat);
            scrollToBottom();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    // ── Scroll-to-bottom FAB ──────────────────────────────────────────────
    // fabScrollDown field removed — FAB is declared in XML (@id/ai_sheet_fab_scroll_down)
    // and wired via setupButtons() → setupFabScrollListener(). No field needed.

    /**
     * Task 4: No-op — FAB is declared in XML (@id/ai_sheet_fab_scroll_down) and wired
     * via setupButtons() -> setupFabScrollListener(). Programmatic duplicate removed.
     */
    private void setupScrollToBottomFab() {
        // FAB handled by setupButtons(). Nothing to do here.
    }

    /** Task 4: Always scroll to bottom unconditionally. */
    private void scrollToBottom() {
        int count = chatAdapter != null ? chatAdapter.getItemCount() : 0;
        if (count > 0)
            messagesList.post(() -> messagesList.smoothScrollToPosition(count - 1));
    }

    private void confirmClearSheet() {
        String actName = currentActivityXmlName != null
                ? currentActivityXmlName.replace(".xml", "") : "this screen";
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Clear \"" + actName + "\" chat")
                .setMessage("Delete all messages for the \"" + actName + "\" screen? This cannot be undone.")
                .setPositiveButton("Clear", (d, w) -> {
                    if (conversationManager != null && conversationId != null) {
                        conversationManager.deleteMessages(conversationId);
                    }
                    if (chatAdapter != null) chatAdapter.setMessages(new java.util.ArrayList<>());
                    if (emptyState != null) emptyState.setVisibility(android.view.View.VISIBLE);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Task 4: Wires proper scroll-visibility logic to the XML FAB.
     * Shows when userScrolledUp AND itemCount > 3.
     * Hides when scroll reaches bottom (onScrollStateChanged + canScrollVertically).
     */
    private void setupFabScrollListener(
            com.google.android.material.floatingactionbutton.FloatingActionButton fab) {
        messagesList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy < 0) userScrolledUp = true;
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                boolean atBottom = !rv.canScrollVertically(1);
                if (atBottom) userScrolledUp = false;
                int count = chatAdapter != null ? chatAdapter.getItemCount() : 0;
                fab.setVisibility(
                        (userScrolledUp && count > 3) ? View.VISIBLE : View.GONE);
            }
        });
        fab.setOnClickListener(v -> {
            userScrolledUp = false;
            scrollToBottom();
        });
    }

        /** Call from DesignActivity.onResume() */
    public void onResume() {
        currentProvider = preferences.getSelectedProvider();
        String saved    = preferences.getSelectedModel(currentProvider);
        if (saved != null) currentModelId = saved;
        updateModelChip();
    }

    /** Call from DesignActivity.onDestroy() */
    public void onDestroy() {
        if (agentExecutor != null) { agentExecutor.shutdown(); agentExecutor = null; }
        // Task 6: cancel() + destroy() + clear main-thread callbacks
        stopListening();
        mainHandler.removeCallbacksAndMessages(null);
        if (messagesList != null) messagesList.clearOnScrollListeners();
    }
}
