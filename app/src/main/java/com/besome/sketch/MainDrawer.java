package com.besome.sketch;

import static com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;

import pro.sketchware.activities.importproject.ImportAndroidStudioProjectActivity;
import com.google.android.material.navigation.NavigationView;

import dev.chrisbanes.insetter.Insetter;
import dev.chrisbanes.insetter.Side;
import mod.hilal.saif.activities.tools.AppSettings;
import pro.sketchware.R;
import pro.sketchware.activities.tools.ProjectCleanUpActivity;
import pro.sketchware.utility.UI;

public class MainDrawer extends NavigationView {
    private static final int DEF_STYLE_RES = R.style.Widget_SketchwarePro_NavigationView_Main;

    public MainDrawer(@NonNull Context context) {
        this(context, null);
    }

    public MainDrawer(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.navigationViewStyle);
    }

    public MainDrawer(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(wrap(context, attrs, defStyleAttr, DEF_STYLE_RES), attrs, defStyleAttr);
        context = getContext();

        var layoutDirection = context.getResources().getConfiguration().getLayoutDirection();
        Insetter.builder()
                .margin(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.navigationBars(),
                        Side.create(layoutDirection == LAYOUT_DIRECTION_LTR,
                                false, layoutDirection == LAYOUT_DIRECTION_RTL, false))
                .applyToView(this);

        ViewGroup headerView = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.main_drawer_header, null);
        headerView.findViewById(R.id.status_bar_overlapper).setMinimumHeight(UI.getStatusBarHeight(context));

        addHeaderView(headerView);
        inflateMenu(R.menu.main_drawer_menu);
        setNavigationItemSelectedListener(item -> {
            initializeDrawerItems(item.getItemId());

            // Return false to prevent selection
            return false;
        });
    }

    private void initializeDrawerItems(@IdRes int id) {
        Activity activity = unwrap(getContext());
        if (id == R.id.app_settings) {
            Intent intent = new Intent(activity, AppSettings.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } else if (id == R.id.import_android_studio_project) {
            Intent intent = new Intent(activity, ImportAndroidStudioProjectActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } else if (id == R.id.project_cleanup) {
            activity.startActivity(new Intent(activity, ProjectCleanUpActivity.class));
        }
    }

    private Activity unwrap(Context context) {
        while (!(context instanceof Activity) && context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }

        return (Activity) context;
    }
}
