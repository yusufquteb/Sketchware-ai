package com.besome.sketch.design;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.MaterialShapeUtils;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.ArrayList;
import java.util.List;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.DesignDrawerItemBinding;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.UI;

public class DesignDrawer extends LinearLayout {

    private static final String PREFS_NAME = "design_drawer_state";

    // Unique IDs for the new items
    private static final int ITEM_FILE_MANAGER = 10001;
    private static final int ITEM_SEARCH_IN_PROJECT = 10002;
    private static final int ITEM_TERMINAL = 10003;

    @SuppressLint("NonConstantResourceId")
    private final View.OnClickListener drawerItemClickListener = v -> {
        Activity activity = (Activity) getContext();
        if (!(activity instanceof DesignActivity designActivity)) return;
        int id = v.getId();

        if      (id == R.id.item_library_manager)          { designActivity.toLibraryManager(); }
        else if (id == R.id.item_view_manager)             { designActivity.toViewManager(); }
        else if (id == R.id.item_image_manager)            { designActivity.toImageManager(); }
        else if (id == R.id.item_sound_manager)            { designActivity.toSoundManager(); }
        else if (id == R.id.item_font_manager)             { designActivity.toFontManager(); }
        else if (id == R.id.item_java_manager)             { designActivity.toJavaManager(); }
        else if (id == R.id.item_resource_manager)         { designActivity.toResourceManager(); }
        else if (id == R.id.item_resource_editor)          { designActivity.toResourceEditor(); }
        else if (id == R.id.item_assets_manager)           { designActivity.toAssetManager(); }
        else if (id == R.id.item_permission_manager)       { designActivity.toPermissionManager(); }
        else if (id == R.id.item_appcompat_manager)        { designActivity.toAppCompatInjectionManager(); }
        else if (id == R.id.item_manifest_manager)         { designActivity.toAndroidManifestManager(); }
        else if (id == R.id.item_used_custom_blocks)       { designActivity.toCustomBlocksViewer(); }
        else if (id == R.id.item_code_shrinking_manager)   { designActivity.toProguardManager(); }
        else if (id == R.id.item_stringfog_manager)        { designActivity.toStringFogManager(); }
        else if (id == R.id.item_show_src)                 { designActivity.toSourceCodeViewer(); }
        else if (id == R.id.item_xml_command_manager)      { designActivity.toXMLCommandManager(); }
        else if (id == R.id.item_logcat_reader)            { designActivity.toLogReader(); }
        else if (id == R.id.item_collection_manager)       { designActivity.toCollectionManager(); }
        else if (id == ITEM_FILE_MANAGER)                  { designActivity.toProjectFileManager(); }
        else if (id == ITEM_SEARCH_IN_PROJECT)             { designActivity.toSearchInProject(); }
        else if (id == ITEM_TERMINAL)                     { designActivity.toTerminal(); }
    };

    public DesignDrawer(Context context) {
        this(context, null);
    }

    public DesignDrawer(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setFocusable(true);
        setClickable(true);

        ShapeAppearanceModel shape = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(SketchwareUtil.getDip(24))
                .setBottomLeftCornerSize(SketchwareUtil.getDip(24))
                .build();

        MaterialShapeDrawable background = new MaterialShapeDrawable(shape);
        background.setFillColor(ColorStateList.valueOf(
                ThemeUtils.getColor(context, R.attr.colorSurfaceContainerLow)));
        background.initializeElevationOverlay(context);
        setBackground(background);
        setElevation(3f);
        setPadding(0, 0, 0, SketchwareUtil.dpToPx(4));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setPadding(
                SketchwareUtil.dpToPx(8), SketchwareUtil.dpToPx(8),
                SketchwareUtil.dpToPx(8), SketchwareUtil.dpToPx(8));
        scrollView.addView(content);

        UI.addSystemWindowInsetToPadding(scrollView, false, true, false, false);
        UI.addSystemWindowInsetToPadding(this, false, false, true, true);

        // ── Section: Assets ───────────────────────────────────
        CollapsibleSection assets = new CollapsibleSection(context,
                "Assets", R.drawable.ic_mtrl_image, "assets_open", true);
        assets.addItem(R.id.item_image_manager,  R.drawable.ic_mtrl_image,
                R.string.design_drawer_menu_title_image,       R.string.design_drawer_menu_description_image);
        assets.addItem(R.id.item_sound_manager,  R.drawable.ic_mtrl_music,
                R.string.design_drawer_menu_title_sound,       R.string.design_drawer_menu_description_sound);
        assets.addItem(R.id.item_font_manager,   R.drawable.ic_mtrl_font,
                R.string.design_drawer_menu_title_font,        R.string.design_drawer_menu_description_font);
        assets.addItem(R.id.item_assets_manager, R.drawable.ic_mtrl_file_present,
                R.string.text_title_menu_assets,               R.string.text_subtitle_menu_assets);
        content.addView(assets.build());

        // ── Section: Code ─────────────────────────────────────
        CollapsibleSection code = new CollapsibleSection(context,
                "Code", R.drawable.ic_mtrl_java, "code_open", true);
        code.addItem(R.id.item_library_manager,    R.drawable.ic_mtrl_category,
                R.string.design_drawer_menu_title_library,     R.string.design_drawer_menu_description_library);
        code.addItem(R.id.item_java_manager,       R.drawable.ic_mtrl_java,
                R.string.text_title_menu_java,                 R.string.text_subtitle_menu_java);
        code.addItem(R.id.item_resource_manager,   R.drawable.ic_mtrl_folder,
                R.string.text_title_menu_resource,             R.string.text_subtitle_menu_resource);
        code.addItem(R.id.item_resource_editor,    R.drawable.ic_mtrl_folder_code,
                R.string.text_title_menu_resource_editor,      R.string.text_subtitle_menu_resource_editor);
        code.addItem(R.id.item_permission_manager, R.drawable.ic_mtrl_shield_check,
                R.string.text_title_menu_permission,           R.string.text_subtitle_menu_permission);
        
        // Use ic_mtrl_folder instead of missing ic_mtrl_folder_zip
        code.addCustomItem(ITEM_FILE_MANAGER, R.drawable.ic_mtrl_folder,
                "Project File Manager", "Manage all project files directly");
        
        content.addView(code.build());

        // ── Section: Extra ────────────────────────────────────
        CollapsibleSection extra = new CollapsibleSection(context,
                "Extra", R.drawable.ic_mtrl_tune, "extra_open", false);
        extra.addItem(R.id.item_view_manager,          R.drawable.ic_mtrl_devices,
                R.string.design_drawer_menu_title_view,        R.string.design_drawer_menu_description_view);
        extra.addItem(R.id.item_appcompat_manager,     R.drawable.ic_mtrl_inject,
                R.string.design_drawer_menu_injection,         R.string.design_drawer_menu_injection_subtitle);
        extra.addItem(R.id.item_manifest_manager,      R.drawable.ic_mtrl_deployed_code,
                R.string.design_drawer_menu_androidmanifest,   R.string.design_drawer_menu_androidmanifest_subtitle);
        extra.addCustomItem(ITEM_TERMINAL, R.drawable.ic_mtrl_code,
                "Terminal", "Shell commands & Python 3 interpreter");
        extra.addItem(R.id.item_used_custom_blocks,    R.drawable.ic_mtrl_block,
                R.string.design_drawer_menu_customblocks,      R.string.design_drawer_menu_customblocks_subtitle);
        extra.addItem(R.id.item_code_shrinking_manager,R.drawable.ic_mtrl_shield_lock,
                R.string.design_drawer_menu_proguard,          R.string.design_drawer_menu_proguard_subtitle);
        extra.addItem(R.id.item_stringfog_manager,     R.drawable.ic_mtrl_regular_expression,
                R.string.design_drawer_menu_stringfog,         R.string.design_drawer_menu_stringfog_subtitle);
        extra.addItem(R.id.item_show_src,              R.drawable.ic_mtrl_frame_source,
                R.string.design_drawer_menu_title_source_code, R.string.design_drawer_menu_description_source_code);
        extra.addItem(R.id.item_xml_command_manager,   R.drawable.ic_mtrl_code,
                R.string.design_drawer_menu_title_xml_command, R.string.design_drawer_menu_description_xml_command);
        extra.addItem(R.id.item_logcat_reader,         R.drawable.ic_mtrl_article,
                R.string.design_drawer_menu_title_logcat_reader,R.string.design_drawer_menu_subtitle_logcat_reader);
        
        // Use ic_mtrl_search (standard)
        extra.addCustomItem(ITEM_SEARCH_IN_PROJECT, R.drawable.ic_mtrl_search,
                "Search in Project", "Find text, classes, or resources");
        
        content.addView(extra.build());

        // ── Divider + Collection Manager (always visible) ─────
        addDrawerDivider(this);
        addDrawerItem(R.id.item_collection_manager, R.drawable.ic_mtrl_bookmark,
                R.string.design_drawer_menu_title_collection,
                R.string.design_drawer_menu_description_collection, this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        MaterialShapeUtils.setParentAbsoluteElevation(this);
    }

    @Override
    public void setElevation(float elevation) {
        super.setElevation(elevation);
        MaterialShapeUtils.setElevation(this, elevation);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int maxWidth = SketchwareUtil.dpToPx(300);
        switch (MeasureSpec.getMode(widthSpec)) {
            case MeasureSpec.EXACTLY:
                break;
            case MeasureSpec.AT_MOST:
                widthSpec = MeasureSpec.makeMeasureSpec(
                        Math.min(MeasureSpec.getSize(widthSpec), maxWidth), MeasureSpec.EXACTLY);
                break;
            case MeasureSpec.UNSPECIFIED:
                widthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
                break;
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    private class CollapsibleSection {
        private final Context ctx;
        private final String title;
        private final int headerIcon;
        private final String prefKey;
        private final boolean defaultOpen;
        private final List<SectionItem> items = new ArrayList<>();

        CollapsibleSection(Context ctx, String title, int headerIcon,
                           String prefKey, boolean defaultOpen) {
            this.ctx = ctx;
            this.title = title;
            this.headerIcon = headerIcon;
            this.prefKey = prefKey;
            this.defaultOpen = defaultOpen;
        }

        void addItem(int id, @DrawableRes int icon, @StringRes int titleRes, @StringRes int descRes) {
            items.add(new SectionItem(id, icon, titleRes, descRes));
        }

        void addCustomItem(int id, @DrawableRes int icon, String title, String desc) {
            items.add(new SectionItem(id, icon, title, desc));
        }

        MaterialCardView build() {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isOpen = prefs.getBoolean(prefKey, defaultOpen);

            MaterialCardView card = new MaterialCardView(ctx);
            LayoutParams cardLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            cardLp.topMargin = SketchwareUtil.dpToPx(6);
            card.setLayoutParams(cardLp);
            card.setCardBackgroundColor(ThemeUtils.getColor(ctx, R.attr.colorSurfaceContainerHigh));
            card.setRadius(SketchwareUtil.dpToPx(16));
            card.setCardElevation(0f);
            card.setStrokeWidth(0);

            LinearLayout cardContent = new LinearLayout(ctx);
            cardContent.setOrientation(VERTICAL);
            card.addView(cardContent, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout header = new LinearLayout(ctx);
            header.setOrientation(HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(14), SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(14));
            header.setBackground(makeRipple(ctx));

            ImageView iconView = new ImageView(ctx);
            int iconSize = SketchwareUtil.dpToPx(20);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconLp.setMarginEnd(SketchwareUtil.dpToPx(12));
            iconView.setLayoutParams(iconLp);
            iconView.setImageResource(headerIcon);
            iconView.setColorFilter(ThemeUtils.getColor(ctx, R.attr.colorPrimary));
            header.addView(iconView);

            TextView titleView = new TextView(ctx);
            titleView.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            titleView.setText(title);
            titleView.setTextSize(14f);
            titleView.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorOnSurface));
            titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            header.addView(titleView);

            ImageView chevron = new ImageView(ctx);
            int chevronSize = SketchwareUtil.dpToPx(20);
            chevron.setLayoutParams(new LinearLayout.LayoutParams(chevronSize, chevronSize));
            chevron.setImageResource(R.drawable.ic_mtrl_arrow_down);
            chevron.setColorFilter(ThemeUtils.getColor(ctx, R.attr.colorOnSurfaceVariant));
            chevron.setRotation(isOpen ? 0f : -90f);
            header.addView(chevron);

            cardContent.addView(header, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            MaterialDivider div = new MaterialDivider(ctx);
            div.setDividerInsetStart(SketchwareUtil.dpToPx(16));
            div.setDividerInsetEnd(SketchwareUtil.dpToPx(16));
            div.setVisibility(isOpen ? View.VISIBLE : View.GONE);
            cardContent.addView(div);

            LinearLayout body = new LinearLayout(ctx);
            body.setOrientation(VERTICAL);
            body.setVisibility(isOpen ? View.VISIBLE : View.GONE);
            cardContent.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (SectionItem item : items) {
                DrawerItem di = new DrawerItem(ctx);
                if (item.isResource) {
                    di.setContent(item.icon, Helper.getResString(di, item.titleRes), Helper.getResString(di, item.descRes));
                } else {
                    di.setContent(item.icon, item.titleStr, item.descStr);
                }
                di.setOnClickListener(item.id, drawerItemClickListener);
                body.addView(di);
            }

            header.setOnClickListener(v -> {
                boolean nowOpen = body.getVisibility() != View.VISIBLE;
                body.setVisibility(nowOpen ? View.VISIBLE : View.GONE);
                div.setVisibility(nowOpen ? View.VISIBLE : View.GONE);
                chevron.animate().rotation(nowOpen ? 0f : -90f).setDuration(200).setInterpolator(new AccelerateDecelerateInterpolator()).start();
                prefs.edit().putBoolean(prefKey, nowOpen).apply();
            });

            return card;
        }

        private android.graphics.drawable.Drawable makeRipple(Context ctx) {
            android.content.res.TypedArray ta = ctx.obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});
            android.graphics.drawable.Drawable d = ta.getDrawable(0);
            ta.recycle();
            return d;
        }
    }

    private static class SectionItem {
        int id, icon, titleRes, descRes;
        String titleStr, descStr;
        boolean isResource;

        SectionItem(int id, int icon, int titleRes, int descRes) {
            this.id = id;
            this.icon = icon;
            this.titleRes = titleRes;
            this.descRes = descRes;
            this.isResource = true;
        }

        SectionItem(int id, int icon, String titleStr, String descStr) {
            this.id = id;
            this.icon = icon;
            this.titleStr = titleStr;
            this.descStr = descStr;
            this.isResource = false;
        }
    }

    private void addDrawerItem(int id, int iconResId, int titleResId, int descResId, ViewGroup view) {
        DrawerItem drawerItem = new DrawerItem(getContext());
        drawerItem.setContent(iconResId, Helper.getResString(drawerItem, titleResId), Helper.getResString(drawerItem, descResId));
        drawerItem.setOnClickListener(id, drawerItemClickListener);
        view.addView(drawerItem);
    }

    private void addDrawerDivider(ViewGroup view) {
        MaterialDivider divider = new MaterialDivider(getContext());
        divider.setDividerInsetEnd(SketchwareUtil.dpToPx(20));
        divider.setDividerInsetStart(SketchwareUtil.dpToPx(20));
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = SketchwareUtil.dpToPx(6);
        lp.bottomMargin = SketchwareUtil.dpToPx(4);
        divider.setLayoutParams(lp);
        view.addView(divider);
    }

    private static class DrawerItem extends LinearLayout {
        private final DesignDrawerItemBinding binding;

        public DrawerItem(Context context) {
            this(context, null);
        }

        public DrawerItem(Context context, AttributeSet attrs) {
            super(context, attrs);
            binding = DesignDrawerItemBinding.inflate(LayoutInflater.from(context), this, true);
        }

        public void setContent(@DrawableRes int iconResId, String title, String description) {
            binding.imgIcon.setImageResource(iconResId);
            binding.tvRootTitle.setText(title);
            binding.tvSubTitle.setText(description);
        }

        public void setOnClickListener(int id, View.OnClickListener listener) {
            binding.getRoot().setId(id);
            binding.getRoot().setOnClickListener(listener);
        }
    }
}
