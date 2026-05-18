package pro.sketchware.ai.bottomsheet;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.manifest.AiCapabilityManifest;
import pro.sketchware.ai.manifest.AiCapabilityManifest.ToolEntry;

/**
 * AiToolsBottomSheet — Shows a categorised list of all AI tools.
 *
 * <p>When the user taps a tool, a callback fires with a suggested prompt
 * ("كيف تريد مساعدتك بهذه الأداة؟") inserted into the chat input.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AiToolsBottomSheet.show(context, toolEntry -> {
 *     inputView.setText(toolEntry.name + ": ");
 * });
 * }</pre>
 */
public final class AiToolsBottomSheet {

    /** Called when the user selects a tool. */
    public interface OnToolSelectedListener {
        void onToolSelected(@NonNull ToolEntry tool);
    }

    /**
     * Shows the tools bottom sheet.
     *
     * @param context  Android context.
     * @param listener Callback for tool selection.
     */
    public static void show(@NonNull Context context,
                            @NonNull OnToolSelectedListener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);

        View root = LayoutInflater.from(context)
                .inflate(R.layout.bottom_sheet_ai_tools, null);
        dialog.setContentView(root);

        RecyclerView rv = root.findViewById(R.id.rv_tools);
        rv.setLayoutManager(new LinearLayoutManager(context));

        // Build flat list with category headers
        List<Object> items = buildItems();
        ToolsAdapter adapter = new ToolsAdapter(items, tool -> {
            dialog.dismiss();
            listener.onToolSelected(tool);
        });
        rv.setAdapter(adapter);

        // Close button
        View btnClose = root.findViewById(R.id.btn_close_tools_sheet);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // ── Private: build categorised list ───────────────────────────────────

    @NonNull
    private static List<Object> buildItems() {
        List<Object> items = new ArrayList<>();
        for (String category : AiCapabilityManifest.getCategories()) {
            List<ToolEntry> tools = AiCapabilityManifest.getToolsByCategory(category);
            if (!tools.isEmpty()) {
                items.add(category);          // header (String)
                items.addAll(tools);          // items (ToolEntry)
            }
        }
        return items;
    }

    // ── Adapter ────────────────────────────────────────────────────────────

    private static final class ToolsAdapter
            extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_TOOL   = 1;

        private final List<Object>           items;
        private final OnToolSelectedListener listener;

        ToolsAdapter(@NonNull List<Object> items,
                     @NonNull OnToolSelectedListener listener) {
            this.items    = items;
            this.listener = listener;
        }

        @Override public int getItemViewType(int position) {
            return items.get(position) instanceof String ? TYPE_HEADER : TYPE_TOOL;
        }

        @Override public int getItemCount() { return items.size(); }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (type == TYPE_HEADER) {
                View v = inf.inflate(R.layout.item_tools_category_header, parent, false);
                return new HeaderVH(v);
            }
            View v = inf.inflate(R.layout.item_ai_tool_entry, parent, false);
            return new ToolVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
            if (holder instanceof HeaderVH) {
                ((HeaderVH) holder).bind((String) items.get(pos));
            } else {
                ((ToolVH) holder).bind((ToolEntry) items.get(pos), listener);
            }
        }
    }

    // ── ViewHolders ────────────────────────────────────────────────────────

    private static final class HeaderVH extends RecyclerView.ViewHolder {
        private final TextView tvCategory;

        HeaderVH(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tv_category);
        }

        void bind(@NonNull String category) {
            tvCategory.setText(category);
        }
    }

    private static final class ToolVH extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvDescription;

        ToolVH(@NonNull View itemView) {
            super(itemView);
            tvName        = itemView.findViewById(R.id.tv_tool_name);
            tvDescription = itemView.findViewById(R.id.tv_tool_description);
        }

        void bind(@NonNull ToolEntry tool, @NonNull OnToolSelectedListener listener) {
            tvName.setText(tool.name);
            tvDescription.setText(tool.description);
            itemView.setOnClickListener(v -> listener.onToolSelected(tool));
        }
    }
}
