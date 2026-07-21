package dev.aldi.sayuti.editor.view.palette;

import android.content.Context;
import android.view.ViewGroup;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.AndroidxOrMaterialView;
import com.besome.sketch.editor.view.palette.IconBase;

import mod.agus.jcoderz.beans.ViewBeans;
import pro.sketchware.R;

/**
 * Palette entry for the Material {@code MaterialDivider} widget (ported from the
 * Sketchware-DayDream fork). Generated code targets
 * {@code com.google.android.material.divider.MaterialDivider}.
 */
public class IconMaterialDivider extends IconBase implements AndroidxOrMaterialView {

    public IconMaterialDivider(Context context) {
        super(context);
        setWidgetImage(R.drawable.ic_mtrl_divider);
        setWidgetName("MaterialDivider");
    }

    @Override
    public ViewBean getBean() {
        ViewBean viewBean = new ViewBean();
        viewBean.type = ViewBeans.VIEW_TYPE_WIDGET_MATERIALDIVIDER;
        viewBean.layout.orientation = VERTICAL;
        viewBean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
        viewBean.layout.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        viewBean.convert = "com.google.android.material.divider.MaterialDivider";
        return viewBean;
    }
}
