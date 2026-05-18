package pro.sketchware.ai.shared;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import pro.sketchware.ai.models.ChatMessage;

/**
 * Unified chat bubble adapter for AiAssistantBottomSheet.
 * Uses pro.sketchware.ai.models.ChatMessage — same package as AiApiClient.
 * User messages: right-aligned primary bubble.
 * AI / System / Direct: left-aligned surface bubble.
 */
public class AiChatAdapter extends RecyclerView.Adapter<AiChatAdapter.Holder> {

    private final List<ChatMessage> messages;

    public AiChatAdapter(List<ChatMessage> messages) { this.messages = messages; }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setLayoutParams(new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 6, 0, 6);

        TextView tv = new TextView(parent.getContext());
        tv.setId(android.R.id.text1);
        tv.setPadding(28, 18, 28, 18);
        tv.setTextSize(13f);
        tv.setLineSpacing(4f, 1.1f);
        tv.setMaxWidth(parent.getContext().getResources().getDisplayMetrics().widthPixels * 3 / 4);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(12, 0, 12, 0);
        tv.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(36f);
        tv.setBackground(bg);
        row.addView(tv);
        return new Holder(row, tv);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int pos) {
        ChatMessage msg = messages.get(pos);
        boolean isUser = "user".equals(msg.getRole());

        h.tv.setText(msg.getContent() != null ? msg.getContent() : "");
        GradientDrawable bg = (GradientDrawable) h.tv.getBackground();
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) h.tv.getLayoutParams();

        if (isUser) {
            bg.setColor(0xFF6750A4);            // M3 colorPrimary
            h.tv.setTextColor(0xFFFFFFFF);
            lp.gravity = Gravity.END;
        } else {
            bg.setColor(0xFFECE6F0);            // M3 surfaceContainerHigh
            h.tv.setTextColor(0xFF1C1B1F);      // M3 colorOnSurface
            lp.gravity = Gravity.START;
        }
        h.tv.setLayoutParams(lp);
    }

    @Override public int getItemCount() { return messages.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView tv;
        Holder(LinearLayout row, TextView tv) { super(row); this.tv = tv; }
    }
}
