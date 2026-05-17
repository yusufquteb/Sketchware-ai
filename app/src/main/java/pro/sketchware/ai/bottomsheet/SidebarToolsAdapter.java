package pro.sketchware.ai.bottomsheet;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.ChangeBounds;
import androidx.transition.TransitionManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pro.sketchware.R;

/**
 * SidebarToolsAdapter — two-level expandable sidebar with categories and tools.
 *
 * Two display modes (controlled by ToolsSidebarManager):
 *   COLLAPSED → 48dp wide — only category icons visible
 *   EXPANDED  → 220dp wide — icons + labels + arrows visible
 *
 * Each category can be independently expanded/collapsed to show child tools.
 */
public class SidebarToolsAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // ── Item types ────────────────────────────────────────────────────────────
    private static final int TYPE_CATEGORY = 0;
    private static final int TYPE_TOOL     = 1;

    // ── Callback ──────────────────────────────────────────────────────────────
    public interface OnToolClickListener {
        /** Called when the user taps a tool child item. */
        void onToolClicked(ToolEntry tool);
    }

    // ── Data model ────────────────────────────────────────────────────────────

    public static class ToolEntry {
        public final String name;          // e.g. "generate_layout"
        public final String displayName;   // e.g. "Generate Layout"
        public final @DrawableRes int iconRes;
        /** True when this tool applies to the current screen only. */
        public final boolean screenScoped;

        public ToolEntry(String name, String displayName,
                         @DrawableRes int iconRes, boolean screenScoped) {
            this.name = name;
            this.displayName = displayName;
            this.iconRes = iconRes;
            this.screenScoped = screenScoped;
        }
    }

    public static class CategoryEntry {
        public final String label;
        public final @DrawableRes int iconRes;
        public final List<ToolEntry> tools;
        public boolean isExpanded;

        public CategoryEntry(String label, @DrawableRes int iconRes,
                             List<ToolEntry> tools) {
            this.label = label;
            this.iconRes = iconRes;
            this.tools = tools;
            this.isExpanded = false;
        }
    }

    // ── Flat list item (category or tool) ─────────────────────────────────────

    private static class ListItem {
        final int type;                   // TYPE_CATEGORY or TYPE_TOOL
        final CategoryEntry category;     // non-null when type=CATEGORY
        final ToolEntry tool;             // non-null when type=TOOL
        final int categoryIndex;          // index into categories list

        ListItem(CategoryEntry cat, int catIdx) {
            this.type = TYPE_CATEGORY;
            this.category = cat;
            this.tool = null;
            this.categoryIndex = catIdx;
        }

        ListItem(ToolEntry tool, int catIdx) {
            this.type = TYPE_TOOL;
            this.category = null;
            this.tool = tool;
            this.categoryIndex = catIdx;
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final Context context;
    private final List<CategoryEntry> categories;
    private List<ListItem> flatList = new ArrayList<>();
    private OnToolClickListener listener;
    private RecyclerView attachedRv;

    /** Whether labels and arrows are visible (expanded sidebar). */
    private boolean sidebarExpanded = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public SidebarToolsAdapter(Context context, List<CategoryEntry> categories) {
        this.context = context;
        this.categories = categories;
        rebuildFlatList();
    }

    public void setOnToolClickListener(OnToolClickListener l) { this.listener = l; }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView rv) {
        super.onAttachedToRecyclerView(rv);
        this.attachedRv = rv;
    }

    // ── Sidebar expand / collapse ─────────────────────────────────────────────

    public boolean isSidebarExpanded() { return sidebarExpanded; }

    public void setSidebarExpanded(boolean expanded) {
        this.sidebarExpanded = expanded;
        notifyDataSetChanged();
    }

    // ── Category expand / collapse ────────────────────────────────────────────

    private void toggleCategory(int categoryIndex) {
        CategoryEntry cat = categories.get(categoryIndex);
        cat.isExpanded = !cat.isExpanded;
        if (attachedRv != null) {
            TransitionManager.beginDelayedTransition(attachedRv, new ChangeBounds());
        }
        rebuildFlatList();
        notifyDataSetChanged();
    }

    private void rebuildFlatList() {
        flatList = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            CategoryEntry cat = categories.get(i);
            flatList.add(new ListItem(cat, i));
            if (cat.isExpanded) {
                for (ToolEntry tool : cat.tools) {
                    flatList.add(new ListItem(tool, i));
                }
            }
        }
    }

    // ── RecyclerView.Adapter ──────────────────────────────────────────────────

    @Override public int getItemCount() { return flatList.size(); }
    @Override public int getItemViewType(int pos) { return flatList.get(pos).type; }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inf = LayoutInflater.from(context);
        if (type == TYPE_CATEGORY) {
            View v = inf.inflate(R.layout.item_sidebar_tool_category, parent, false);
            return new CategoryVH(v);
        } else {
            View v = inf.inflate(R.layout.item_sidebar_tool_child, parent, false);
            return new ToolVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        ListItem item = flatList.get(pos);
        if (item.type == TYPE_CATEGORY) {
            ((CategoryVH) holder).bind((CategoryEntry) item.category,
                    item.categoryIndex, sidebarExpanded);
        } else {
            ((ToolVH) holder).bind(item.tool, sidebarExpanded);
        }
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    class CategoryVH extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView  label;
        private final ImageView arrow;

        CategoryVH(View v) {
            super(v);
            icon  = v.findViewById(R.id.sidebar_cat_icon);
            label = v.findViewById(R.id.sidebar_cat_label);
            arrow = v.findViewById(R.id.sidebar_cat_arrow);
        }

        void bind(CategoryEntry cat, int catIdx, boolean expanded) {
            icon.setImageResource(cat.iconRes);
            label.setText(cat.label);

            int labelVis = expanded ? View.VISIBLE : View.GONE;
            label.setVisibility(labelVis);
            arrow.setVisibility(labelVis);

            if (expanded) {
                // Rotate arrow 90° when expanded
                arrow.setRotation(cat.isExpanded ? 90f : 0f);
            }

            itemView.setOnClickListener(v -> toggleCategory(catIdx));
        }
    }

    class ToolVH extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView  label;
        private final ImageView arrow;

        ToolVH(View v) {
            super(v);
            icon  = v.findViewById(R.id.sidebar_tool_icon);
            label = v.findViewById(R.id.sidebar_tool_label);
            arrow = v.findViewById(R.id.sidebar_tool_arrow);
        }

        void bind(ToolEntry tool, boolean sidebarExpanded) {
            icon.setImageResource(tool.iconRes);
            label.setText(tool.displayName);

            int vis = sidebarExpanded ? View.VISIBLE : View.GONE;
            label.setVisibility(vis);
            arrow.setVisibility(vis);

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onToolClicked(tool);
            });
        }
    }

    // ── Static factory: builds the canonical tool category list ──────────────

    /**
     * Builds the consolidated tool categories shown in the sidebar.
     *
     * Tool consolidation rules applied here:
     * - describe_layout + get_layout → merged into "Read Layout" (describe_layout)
     * - add_view + add_view_xml → merged into "Add View XML" (add_view_xml — preferred)
     * - generate_layout + generate_layout_from_description → merged into "Generate Layout"
     * - modify_view + edit_layout → merged into "Edit Layout" (edit_layout — preferred)
     * - list_activities + get_screen_source → both kept (different data)
     * - write_file + patch_file → kept separately (different mutation styles)
     */
    public static List<CategoryEntry> buildCategories() {
        List<CategoryEntry> cats = new ArrayList<>();

        // ── 1. UI / Layout ────────────────────────────────────────────────
        cats.add(new CategoryEntry("UI", R.drawable.ic_mtrl_screen,
                Arrays.asList(
                    new ToolEntry("generate_layout",
                            "Generate Layout",  R.drawable.ic_mtrl_bulb,       true),
                    new ToolEntry("describe_layout",
                            "Read Layout",      R.drawable.ic_mtrl_visibility,  true),
                    new ToolEntry("add_view_xml",
                            "Add View XML",     R.drawable.ic_mtrl_add_circle,  true),
                    new ToolEntry("edit_ui",
                            "Edit Layout",      R.drawable.ic_mtrl_edit,        true),
                    new ToolEntry("remove_view",
                            "Remove View",      R.drawable.ic_mtrl_delete,      true),
                    new ToolEntry("validate_rtl_layout",
                            "Validate RTL",     R.drawable.ic_mtrl_preview,     true)
                )));

        // ── 2. File ────────────────────────────────────────────────────────
        cats.add(new CategoryEntry("File", R.drawable.ic_mtrl_folder,
                Arrays.asList(
                    new ToolEntry("read_file",
                            "Read File",       R.drawable.ic_mtrl_visibility,     false),
                    new ToolEntry("write_file",
                            "Write File",      R.drawable.ic_mtrl_edit,           false),
                    new ToolEntry("patch_file",
                            "Patch File",      R.drawable.ic_mtrl_article,     false),
                    new ToolEntry("append_code",
                            "Append Code",     R.drawable.ic_mtrl_add,            false),
                    new ToolEntry("delete_file",
                            "Delete File",     R.drawable.ic_mtrl_delete,         false),
                    new ToolEntry("list_files",
                            "List Files",      R.drawable.ic_mtrl_list,           false),
                    new ToolEntry("search_in_file",
                            "Search in File",  R.drawable.ic_mtrl_search,         false)
                )));

        // ── 3. Activities / Screens ────────────────────────────────────────
        cats.add(new CategoryEntry("Screens", R.drawable.ic_mtrl_view_vertical,
                Arrays.asList(
                    new ToolEntry("list_activities",
                            "List Screens",    R.drawable.ic_mtrl_list,           false),
                    new ToolEntry("get_screen_source",
                            "Screen Source",   R.drawable.ic_mtrl_code,           false),
                    new ToolEntry("create_activity",
                            "New Screen",      R.drawable.ic_mtrl_add,            false),
                    new ToolEntry("delete_activity",
                            "Delete Screen",   R.drawable.ic_mtrl_delete,         false)
                )));

        // ── 4. Block Logic ─────────────────────────────────────────────────
        cats.add(new CategoryEntry("Logic", R.drawable.ic_mtrl_deployed_code,
                Arrays.asList(
                    new ToolEntry("get_activity_events",
                            "List Events",     R.drawable.ic_mtrl_flash_on,           true),
                    new ToolEntry("get_event_blocks",
                            "Read Blocks",     R.drawable.ic_mtrl_visibility,     true),
                    new ToolEntry("add_block",
                            "Add Block",       R.drawable.ic_mtrl_add,            true),
                    new ToolEntry("modify_block",
                            "Edit Block",      R.drawable.ic_mtrl_edit,           true),
                    new ToolEntry("delete_block",
                            "Delete Block",    R.drawable.ic_mtrl_delete,         true),
                    new ToolEntry("get_moreblocks",
                            "Custom Blocks",   R.drawable.ic_mtrl_puzzle,      false),
                    new ToolEntry("create_moreblock",
                            "New Custom Block",R.drawable.ic_mtrl_add,            false)
                )));

        // ── 5. Resources ───────────────────────────────────────────────────
        cats.add(new CategoryEntry("Resources", R.drawable.ic_mtrl_image,
                Arrays.asList(
                    new ToolEntry("list_resources",
                            "List Resources",  R.drawable.ic_mtrl_list,           false),
                    new ToolEntry("add_string_resource",
                            "Add String",      R.drawable.ic_mtrl_label,          false),
                    new ToolEntry("add_color_resource",
                            "Add Color",       R.drawable.ic_mtrl_palette,        false),
                    new ToolEntry("read_raw_resource_file",
                            "Read Raw File",   R.drawable.ic_mtrl_visibility,     false),
                    new ToolEntry("write_raw_resource_file",
                            "Write Raw File",  R.drawable.ic_mtrl_edit,           false)
                )));

        // ── 6. Libraries ───────────────────────────────────────────────────
        cats.add(new CategoryEntry("Libs", R.drawable.ic_mtrl_box,
                Arrays.asList(
                    new ToolEntry("list_libraries",
                            "List Libraries",  R.drawable.ic_mtrl_list,           false),
                    new ToolEntry("add_library",
                            "Add Library",     R.drawable.ic_mtrl_add,            false),
                    new ToolEntry("remove_library",
                            "Remove Library",  R.drawable.ic_mtrl_delete,         false),
                    new ToolEntry("search_maven",
                            "Search Maven",    R.drawable.ic_mtrl_search,         false),
                    new ToolEntry("download_dependency",
                            "Download Dep",    R.drawable.ic_mtrl_download,       false)
                )));

        // ── 7. Project ─────────────────────────────────────────────────────
        cats.add(new CategoryEntry("Project", R.drawable.ic_mtrl_folder_code,
                Arrays.asList(
                    new ToolEntry("get_project_info",
                            "Project Info",    R.drawable.ic_mtrl_info,           false),
                    new ToolEntry("list_projects",
                            "List Projects",   R.drawable.ic_mtrl_list,           false),
                    new ToolEntry("create_project",
                            "New Project",     R.drawable.ic_mtrl_add,            false),
                    new ToolEntry("duplicate_project",
                            "Duplicate",       R.drawable.ic_mtrl_content_copy,       false),
                    new ToolEntry("delete_project",
                            "Delete Project",  R.drawable.ic_mtrl_delete,         false),
                    new ToolEntry("add_permission",
                            "Add Permission",  R.drawable.ic_mtrl_shield_lock,       false),
                    new ToolEntry("get_project_structure",
                            "Structure",       R.drawable.ic_mtrl_deployed_code,   false)
                )));

        // ── 8. Build ───────────────────────────────────────────────────────
        cats.add(new CategoryEntry("Build", R.drawable.ic_mtrl_sprint,
                Arrays.asList(
                    new ToolEntry("build_project",
                            "Build",           R.drawable.ic_mtrl_sprint,          false),
                    new ToolEntry("get_compile_logs",
                            "Compile Logs",    R.drawable.ic_mtrl_terminal,       false),
                    new ToolEntry("analyze_code",
                            "Analyze Code",    R.drawable.ic_mtrl_code,      false),
                    new ToolEntry("review_source_code",
                            "Review Code",     R.drawable.ic_mtrl_preview,    false),
                    new ToolEntry("export_to_android_studio",
                            "Export to AS",    R.drawable.ic_mtrl_export,    false)
                )));

        return cats;
    }
}
