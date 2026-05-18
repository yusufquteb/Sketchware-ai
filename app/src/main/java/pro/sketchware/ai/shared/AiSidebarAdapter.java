package pro.sketchware.ai.shared;

import android.animation.ObjectAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;

/**
 * Unified sidebar adapter for AiAssistantBottomSheet.
 *
 * Reuses item_sidebar_tool_category.xml and item_sidebar_tool_child.xml
 * (same layouts as the Design-page sidebar).
 *
 * Categories start collapsed. User taps category header to expand/collapse.
 * When sidebar width < 100dp, labels are hidden (icon-only mode).
 */
public class AiSidebarAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnToolTapListener {
        void onToolTapped(AiPageConfig.Tool tool);
    }

    private static final int TYPE_CATEGORY = 0;
    private static final int TYPE_CHILD    = 1;

    private final List<AiPageConfig.Tool> allTools;
    private final List<AiPageConfig.Tool> visible = new ArrayList<>();
    private final List<Boolean>           catExpanded;
    private final OnToolTapListener       listener;
    private boolean showLabels = true; // true when sidebar width ≥ 100dp

    public AiSidebarAdapter(List<AiPageConfig.Tool> tools, OnToolTapListener listener) {
        this.allTools    = tools;
        this.listener    = listener;
        this.catExpanded = new ArrayList<>();
        for (AiPageConfig.Tool t : tools) if (t.type == AiPageConfig.ToolType.CATEGORY) catExpanded.add(false);
        rebuildVisible();
    }

    public void setShowLabels(boolean show) {
        this.showLabels = show;
        notifyItemRangeChanged(0, getItemCount());
    }

    private void toggleCategory(int visiblePos) {
        int catIdx = -1;
        for (int i = 0; i <= visiblePos; i++) {
            if (visible.get(i).type == AiPageConfig.ToolType.CATEGORY) catIdx++;
        }
        if (catIdx < 0 || catIdx >= catExpanded.size()) return;
        boolean expanding = !catExpanded.get(catIdx);
        catExpanded.set(catIdx, expanding);

        // Count children that will be inserted/removed after visiblePos
        int insertPos = visiblePos + 1;
        int childCount = 0;
        for (int i = visiblePos + 1; i < visible.size(); i++) {
            if (visible.get(i).type == AiPageConfig.ToolType.CATEGORY) break;
            childCount++;
        }

        rebuildVisible();

        if (expanding) {
            // After rebuild visible has the new children; count how many were added
            int newChildCount = 0;
            for (int i = visiblePos + 1; i < visible.size(); i++) {
                if (visible.get(i).type == AiPageConfig.ToolType.CATEGORY) break;
                newChildCount++;
            }
            notifyItemChanged(visiblePos);
            if (newChildCount > 0) notifyItemRangeInserted(insertPos, newChildCount);
        } else {
            notifyItemChanged(visiblePos);
            if (childCount > 0) notifyItemRangeRemoved(insertPos, childCount);
        }
    }

    private void rebuildVisible() {
        visible.clear();
        int catIdx = -1;
        for (AiPageConfig.Tool t : allTools) {
            if (t.type == AiPageConfig.ToolType.CATEGORY) {
                catIdx++;
                visible.add(t);
            } else if (catIdx >= 0 && catIdx < catExpanded.size() && catExpanded.get(catIdx)) {
                visible.add(t);
            }
        }
    }

    @Override public int getItemViewType(int pos) {
        return visible.get(pos).type == AiPageConfig.ToolType.CATEGORY ? TYPE_CATEGORY : TYPE_CHILD;
    }

    @Override public int getItemCount() { return visible.size(); }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_CATEGORY) {
            return new CatHolder(inf.inflate(R.layout.item_sidebar_tool_category, parent, false));
        } else {
            return new ChildHolder(inf.inflate(R.layout.item_sidebar_tool_child, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        AiPageConfig.Tool tool = visible.get(pos);

        if (holder instanceof CatHolder) {
            CatHolder h = (CatHolder) holder;
            h.icon.setImageResource(tool.iconRes);
            h.label.setText(tool.label);
            h.label.setVisibility(showLabels ? View.VISIBLE : View.GONE);

            // Determine expansion state
            int ci = -1;
            for (int i = 0; i <= pos; i++) if (visible.get(i).type == AiPageConfig.ToolType.CATEGORY) ci++;
            boolean expanded = ci >= 0 && ci < catExpanded.size() && catExpanded.get(ci);

            if (showLabels) {
                h.arrow.setVisibility(View.VISIBLE);
                h.arrow.setRotation(expanded ? 90f : 0f);
            } else {
                h.arrow.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> {
                int p = holder.getAdapterPosition();
                if (p != RecyclerView.NO_ID) {
                    float from = h.arrow.getRotation();
                    ObjectAnimator.ofFloat(h.arrow, "rotation", from, from == 90f ? 0f : 90f)
                        .setDuration(180).start();
                    toggleCategory(p);
                }
            });

        } else {
            ChildHolder h = (ChildHolder) holder;
            h.icon.setImageResource(tool.iconRes);
            h.label.setText(tool.label);
            h.label.setVisibility(showLabels ? View.VISIBLE : View.GONE);

            // Direct tools show a "⚡" indicator on arrow
            if (showLabels) {
                h.arrow.setVisibility(View.VISIBLE);
                h.arrow.setImageResource(tool.type == AiPageConfig.ToolType.DIRECT
                    ? R.drawable.ic_mtrl_check
                    : R.drawable.ic_mtrl_arrow_right);
                h.arrow.setAlpha(0.4f);
            } else {
                h.arrow.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onToolTapped(tool);
            });
        }
    }

    static class CatHolder extends RecyclerView.ViewHolder {
        final ImageView icon, arrow; final TextView label;
        CatHolder(View v) {
            super(v);
            icon  = v.findViewById(R.id.sidebar_cat_icon);
            label = v.findViewById(R.id.sidebar_cat_label);
            arrow = v.findViewById(R.id.sidebar_cat_arrow);
        }
    }

    static class ChildHolder extends RecyclerView.ViewHolder {
        final ImageView icon, arrow; final TextView label;
        ChildHolder(View v) {
            super(v);
            icon  = v.findViewById(R.id.sidebar_tool_icon);
            label = v.findViewById(R.id.sidebar_tool_label);
            arrow = v.findViewById(R.id.sidebar_tool_arrow);
        }
    }
}
