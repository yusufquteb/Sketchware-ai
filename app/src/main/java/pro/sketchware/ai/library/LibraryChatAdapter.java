package pro.sketchware.ai.library;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import pro.sketchware.ai.models.ChatMessage;   // ← correct package

/**
 * Chat bubble adapter for LibraryAiBottomSheet.
 * Uses pro.sketchware.ai.models.ChatMessage (same package as AiApiClient).
 * User role → right-aligned purple bubble.
 * Other → left-aligned surface bubble.
 */
public class LibraryChatAdapter extends RecyclerView.Adapter<LibraryChatAdapter.ViewHolder> {

    private final List<ChatMessage> messages;

    public LibraryChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout container = new LinearLayout(parent.getContext());
        container.setLayoutParams(new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, 4, 0, 4);

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
        container.addView(tv);
        return new ViewHolder(container, tv);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        boolean isUser = "user".equals(msg.getRole());

        holder.tv.setText(msg.getContent() != null ? msg.getContent() : "");

        GradientDrawable bg = (GradientDrawable) holder.tv.getBackground();
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) holder.tv.getLayoutParams();

        if (isUser) {
            bg.setColor(0xFF6750A4);            // M3 colorPrimary
            holder.tv.setTextColor(0xFFFFFFFF);
            lp.gravity = Gravity.END;
        } else {
            bg.setColor(0xFFECE6F0);            // M3 surfaceContainerHigh
            holder.tv.setTextColor(0xFF1C1B1F); // M3 colorOnSurface
            lp.gravity = Gravity.START;
        }
        holder.tv.setLayoutParams(lp);
    }

    @Override public int getItemCount() { return messages.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tv;
        ViewHolder(LinearLayout c, TextView tv) { super(c); this.tv = tv; }
    }
}
