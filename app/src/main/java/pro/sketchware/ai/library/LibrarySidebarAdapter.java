package pro.sketchware.ai.library;

import android.animation.ObjectAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;

/**
 * Sidebar adapter for LibraryAiBottomSheet.
 * Uses item_sidebar_tool_category.xml and item_sidebar_tool_child.xml
 * (same layouts as Design page sidebar).
 *
 * Categories are expanded/collapsed independently.
 * Each tool has:
 *   - prompt  → fill input (user edits + sends)
 *   - isDirectAction → execute without AI (offline-capable)
 */
public class LibrarySidebarAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // ── Tool model ────────────────────────────────────────────────────────────
    public static class Tool {
        public final String label;
        public final int    iconRes;
        public final boolean isCategory;
        public final String inputTemplate;  // fills chat input; null for category headers
        public final boolean isDirectAction; // true = execute immediately without AI

        public Tool(String label, @DrawableRes int iconRes, boolean isCategory,
                    String inputTemplate, boolean isDirectAction) {
            this.label         = label;
            this.iconRes       = iconRes;
            this.isCategory    = isCategory;
            this.inputTemplate = inputTemplate;
            this.isDirectAction = isDirectAction;
        }
        /** Category constructor */
        public Tool(String label, @DrawableRes int iconRes) {
            this(label, iconRes, true, null, false);
        }
        /** AI-assisted tool */
        public Tool(String label, @DrawableRes int iconRes, String inputTemplate) {
            this(label, iconRes, false, inputTemplate, false);
        }
        /** Direct-action tool (no AI needed) */
        public Tool(String label, @DrawableRes int iconRes, String inputTemplate, boolean direct) {
            this(label, iconRes, false, inputTemplate, direct);
        }
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────
    public interface OnToolListener {
        /** Fill the chat input with template; user can edit before sending. */
        void onFillInput(String template);
        /** Execute tool directly without AI (offline-capable). */
        void onDirectAction(String actionKey);
    }

    // ── View types ────────────────────────────────────────────────────────────
    private static final int TYPE_CATEGORY = 0;
    private static final int TYPE_CHILD    = 1;

    // ── State ─────────────────────────────────────────────────────────────────
    private final List<Tool>   allTools;   // all items (categories + children)
    private final List<Tool>   visible;    // currently shown items
    private final List<Boolean> catExpanded; // expansion state per category
    private final OnToolListener listener;
    private boolean sidebarExpanded = true; // sidebar is open by default

    public LibrarySidebarAdapter(List<Tool> tools, OnToolListener listener) {
        this.allTools    = tools;
        this.listener    = listener;
        this.visible     = new ArrayList<>();
        this.catExpanded = new ArrayList<>();
        // Compute initial cat expansion states (all categories collapsed)
        for (Tool t : tools) if (t.isCategory) catExpanded.add(false);
        rebuildVisible();
    }

    /** Call when sidebar width changes (collapsed ↔ expanded). */
    public void setSidebarExpanded(boolean expanded) {
        this.sidebarExpanded = expanded;
        notifyItemRangeChanged(0, getItemCount());
    }

    /** Toggle a category's expanded/collapsed state. */
    private void toggleCategory(int visiblePos) {
        int catIdx = 0;
        for (int i = 0; i <= visiblePos; i++) {
            if (visible.get(i).isCategory) catIdx++;
        }
        catIdx--;
        if (catIdx < 0 || catIdx >= catExpanded.size()) return;
        boolean expanding = !catExpanded.get(catIdx);
        catExpanded.set(catIdx, expanding);

        int insertPos = visiblePos + 1;
        int childCount = 0;
        for (int i = visiblePos + 1; i < visible.size(); i++) {
            if (visible.get(i).isCategory) break;
            childCount++;
        }

        rebuildVisible();

        if (expanding) {
            int newChildCount = 0;
            for (int i = visiblePos + 1; i < visible.size(); i++) {
                if (visible.get(i).isCategory) break;
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
        for (Tool t : allTools) {
            if (t.isCategory) {
                catIdx++;
                visible.add(t);
            } else {
                if (catIdx >= 0 && catIdx < catExpanded.size() && catExpanded.get(catIdx)) {
                    visible.add(t);
                }
            }
        }
    }

    @Override public int getItemViewType(int pos) {
        return visible.get(pos).isCategory ? TYPE_CATEGORY : TYPE_CHILD;
    }

    @Override public int getItemCount() { return visible.size(); }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_CATEGORY) {
            return new CategoryHolder(inf.inflate(R.layout.item_sidebar_tool_category, parent, false));
        } else {
            return new ChildHolder(inf.inflate(R.layout.item_sidebar_tool_child, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Tool tool = visible.get(position);

        if (holder instanceof CategoryHolder) {
            CategoryHolder h = (CategoryHolder) holder;
            h.icon.setImageResource(tool.iconRes);
            h.label.setText(tool.label);
            h.label.setVisibility(sidebarExpanded ? View.VISIBLE : View.GONE);

            // Determine if expanded to rotate arrow
            int catIdx = 0;
            for (int i = 0; i <= position; i++) if (visible.get(i).isCategory) catIdx++;
            catIdx--;
            boolean isExpanded = catIdx >= 0 && catIdx < catExpanded.size() && catExpanded.get(catIdx);

            if (sidebarExpanded) {
                h.arrow.setVisibility(View.VISIBLE);
                h.arrow.setRotation(isExpanded ? 90f : 0f);
            } else {
                h.arrow.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_ID) {
                    // Rotate arrow
                    float from = h.arrow.getRotation();
                    float to   = from == 90f ? 0f : 90f;
                    ObjectAnimator.ofFloat(h.arrow, "rotation", from, to).setDuration(180).start();
                    toggleCategory(pos);
                }
            });

        } else {
            ChildHolder h = (ChildHolder) holder;
            h.icon.setImageResource(tool.iconRes);
            h.label.setText(tool.label);
            h.label.setVisibility(sidebarExpanded ? View.VISIBLE : View.GONE);
            h.arrow.setVisibility(sidebarExpanded && !tool.isDirectAction ? View.VISIBLE : View.GONE);

            // Direct-action tools get a different arrow (play icon) hint
            if (tool.isDirectAction && sidebarExpanded) {
                h.arrow.setVisibility(View.VISIBLE);
                h.arrow.setImageResource(R.drawable.ic_mtrl_check);
                h.arrow.setAlpha(0.5f);
            }

            h.itemView.setOnClickListener(v -> {
                if (listener == null || tool.inputTemplate == null) return;
                if (tool.isDirectAction) {
                    listener.onDirectAction(tool.label);
                } else {
                    listener.onFillInput(tool.inputTemplate);
                }
            });
        }
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────
    static class CategoryHolder extends RecyclerView.ViewHolder {
        final ImageView icon, arrow; final TextView label;
        CategoryHolder(View v) {
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
