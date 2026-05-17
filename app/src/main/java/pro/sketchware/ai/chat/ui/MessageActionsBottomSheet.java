package pro.sketchware.ai.chat.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import pro.sketchware.R;
import pro.sketchware.ai.chat.model.ChatMessage;

/**
 * MessageActionsBottomSheet — Stage 3 custom long-press action sheet.
 *
 * <p>Replaces the Android default text selection toolbar.
 *
 * <p><b>Triggered on LONG PRESS of any message bubble.</b>
 *
 * <p>Actions:
 * <ul>
 *   <li><b>Copy</b>   → copies full text to {@link ClipboardManager}</li>
 *   <li><b>Share</b>  → fires {@link Intent#ACTION_SEND}</li>
 *   <li><b>Select All</b> → highlights full text in the originating TextView</li>
 *   <li><b>Cancel</b> → dismisses the sheet</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>
 * // In adapter long-press listener:
 * MessageActionsBottomSheet.show(fragmentManager, message);
 * </pre>
 *
 * <p><b>Design rules (Material 3):</b>
 * <ul>
 *   <li>Layout: bottom_sheet_message_actions.xml</li>
 *   <li>No hardcoded colors.</li>
 *   <li>All touch targets ≥ 48dp.</li>
 *   <li>Message preview truncated to 2 lines.</li>
 * </ul>
 */
public class MessageActionsBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "MessageActionsBottomSheet";

    // ─── Arguments ────────────────────────────────────────────────────────────

    private static final String ARG_MESSAGE_TEXT = "message_text";
    private static final String ARG_MESSAGE_ID   = "message_id";

    // ─── Static factory ───────────────────────────────────────────────────────

    /**
     * Creates and shows the bottom sheet for the given message.
     *
     * @param manager the FragmentManager from the hosting Activity/Fragment
     * @param message the message that was long-pressed
     */
    public static MessageActionsBottomSheet show(
            @NonNull androidx.fragment.app.FragmentManager manager,
            @NonNull ChatMessage message
    ) {
        MessageActionsBottomSheet sheet = new MessageActionsBottomSheet();

        Bundle args = new Bundle();
        args.putString(ARG_MESSAGE_TEXT, message.getText());
        args.putString(ARG_MESSAGE_ID,   message.getId());
        sheet.setArguments(args);

        sheet.show(manager, TAG);
        return sheet;
    }

    // ─── State ────────────────────────────────────────────────────────────────

    @Nullable private String messageText;
    @Nullable private String messageId;

    // ─── Optional callback for "Select All" ───────────────────────────────────

    /**
     * Optional callback: invoked when the user taps "Select All".
     * The hosting adapter/ViewHolder should use this to set selection on the TextView.
     */
    public interface OnSelectAllListener {
        void onSelectAll(@NonNull String messageId);
    }

    @Nullable private OnSelectAllListener selectAllListener;

    public void setOnSelectAllListener(@Nullable OnSelectAllListener listener) {
        this.selectAllListener = listener;
    }

    // ─── Fragment lifecycle ───────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(
                R.layout.bottom_sheet_message_actions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Retrieve arguments
        Bundle args = getArguments();
        if (args != null) {
            messageText = args.getString(ARG_MESSAGE_TEXT);
            messageId   = args.getString(ARG_MESSAGE_ID);
        }

        // ── Bind views ────────────────────────────────────────────────────
        TextView tvPreview = view.findViewById(R.id.msg_actions_preview);

        // Message preview (truncated, hint to user what they're acting on)
        if (messageText != null && !messageText.isEmpty()) {
            tvPreview.setVisibility(View.VISIBLE);
            tvPreview.setText(messageText);
        } else {
            tvPreview.setVisibility(View.GONE);
        }

        // ── Copy ──────────────────────────────────────────────────────────
        view.findViewById(R.id.msg_action_copy).setOnClickListener(v -> {
            performCopy();
            dismiss();
        });

        // ── Share ─────────────────────────────────────────────────────────
        view.findViewById(R.id.msg_action_share).setOnClickListener(v -> {
            performShare();
            dismiss();
        });

        // ── Select All ────────────────────────────────────────────────────
        view.findViewById(R.id.msg_action_select_all).setOnClickListener(v -> {
            if (selectAllListener != null && messageId != null) {
                selectAllListener.onSelectAll(messageId);
            }
            dismiss();
        });

        // ── Cancel ────────────────────────────────────────────────────────
        view.findViewById(R.id.msg_action_cancel).setOnClickListener(v -> dismiss());
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    /**
     * Copies the message text to the system clipboard.
     * Shows a short Toast confirmation.
     */
    private void performCopy() {
        Context ctx = requireContext();
        if (messageText == null || messageText.isEmpty()) return;

        ClipboardManager clipboard =
                (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("chat_message", messageText);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(ctx, R.string.chat_copied_to_clipboard,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Fires a standard Android share intent with the message text.
     */
    private void performShare() {
        if (messageText == null || messageText.isEmpty()) return;

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, messageText);

        startActivity(Intent.createChooser(intent,
                getString(R.string.chat_share_message)));
    }
}
