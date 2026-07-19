package pro.sketchware.activities.library.extras;

import android.content.Context;

import com.besome.sketch.editor.manage.library.LibraryItemView;

import pro.sketchware.R;

/**
 * A plain navigation row (icon + title + description, no ON/OFF state) used
 * to open {@link DaydreamToolsActivity} from ManageLibraryActivity's
 * "External libraries" section.
 */
public class DaydreamToolsNavItemView extends LibraryItemView {

    public DaydreamToolsNavItemView(Context context) {
        super(context);
        icon.setImageResource(R.drawable.ic_mtrl_component);
        title.setText("daydream tools");
        description.setText("Firebase & Google, User Interface, and Extra Libraries — ported from daydream");
        setHideEnabled();
    }

    @Override
    public void setData(com.besome.sketch.beans.ProjectLibraryBean projectLibraryBean) {
        // No bean-backed state; this row is a static navigation entry.
    }
}
