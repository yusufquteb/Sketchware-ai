package pro.sketchware.activities.library.extras;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.editor.manage.library.LibraryItemView;

/**
 * A {@link LibraryItemView} variant used as a plain navigation item
 * (no ON/OFF badge — just icon, title, description, and a forward chevron).
 */
@SuppressLint("ViewConstructor")
public class NavLibraryItemView extends LibraryItemView {

    public NavLibraryItemView(Context context, @DrawableRes int iconRes,
                              String title, String description) {
        super(context);
        icon.setImageResource(iconRes);
        this.title.setText(title);
        this.description.setText(description);
        // Replace ON/OFF label with a forward-arrow indicator
        enabled.setText("›");
        enabled.setTextSize(22f);
        enabled.setSelected(false);
    }

    @Override
    public void setData(@Nullable ProjectLibraryBean bean) {
        // Data already set in constructor; intentionally a no-op here.
    }
}
