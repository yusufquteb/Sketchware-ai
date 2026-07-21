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
 * Palette entry for the Material {@code BottomSheetDragHandleView} widget (ported from the
 * Sketchware-DayDream fork). Generated code targets
 * {@code com.google.android.material.bottomsheet.BottomSheetDragHandleView}.
 */
public class IconBottomSheetDragHandleView extends IconBase implements AndroidxOrMaterialView {

    public IconBottomSheetDragHandleView(Context context) {
        super(context);
        setWidgetImage(R.drawable.ic_mtrl_drag_handle);
        setWidgetName("BottomSheetDragHandleView");
    }

    @Override
    public ViewBean getBean() {
        ViewBean viewBean = new ViewBean();
        viewBean.type = ViewBeans.VIEW_TYPE_WIDGET_BOTTOMSHEETDRAGHANDLEVIEW;
        viewBean.layout.orientation = VERTICAL;
        viewBean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
        viewBean.layout.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        viewBean.layout.gravity = Gravity.CENTER;
        viewBean.convert = "com.google.android.material.bottomsheet.BottomSheetDragHandleView";
        return viewBean;
    }
}
