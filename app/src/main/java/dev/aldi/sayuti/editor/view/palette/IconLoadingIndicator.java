package dev.aldi.sayuti.editor.view.palette;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.AndroidxOrMaterialView;
import com.besome.sketch.editor.view.palette.IconBase;

import mod.agus.jcoderz.beans.ViewBeans;
import pro.sketchware.R;

/**
 * Palette entry for the Material 3 {@code LoadingIndicator} widget (ported from the
 * Sketchware-DayDream fork). The generated project code targets
 * {@code com.google.android.material.loadingindicator.LoadingIndicator}; the on-canvas
 * preview uses a {@code CircularProgressIndicator} (see {@code ItemLoadingIndicator}).
 */
public class IconLoadingIndicator extends IconBase implements AndroidxOrMaterialView {

    public IconLoadingIndicator(Context context) {
        super(context);
        setWidgetImage(R.drawable.ic_mtrl_loading_indicator);
        setWidgetName("LoadingIndicator");
    }

    @Override
    public ViewBean getBean() {
        ViewBean viewBean = new ViewBean();
        viewBean.type = ViewBeans.VIEW_TYPE_WIDGET_LOADINGINDICATOR;
        viewBean.layout.orientation = VERTICAL;
        viewBean.layout.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        viewBean.layout.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        viewBean.layout.gravity = Gravity.CENTER;
        viewBean.convert = "com.google.android.material.loadingindicator.LoadingIndicator";
        return viewBean;
    }
}
