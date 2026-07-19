package mod.jbk.editor.manage.library;

import android.content.Context;

import androidx.annotation.Nullable;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.editor.manage.library.LibraryItemView;

import java.util.List;

import mod.jbk.build.BuiltInLibraries;
import pro.sketchware.util.library.BuiltInLibraryManager;

public class EnableBuiltInLibrariesLibraryItemView extends LibraryItemView {
    private final String sc_id;

    public EnableBuiltInLibrariesLibraryItemView(Context context, String sc_id) {
        super(context);
        this.sc_id = sc_id;
    }

    @Override
    public void setData(@Nullable ProjectLibraryBean projectLibraryBean) {
        boolean enablingEnabled = EnableBuiltInLibrariesActivity.isEnablingEnabled(sc_id);
        List<BuiltInLibraries.BuiltInLibrary> manualLibraries = EnableBuiltInLibrariesActivity.getEnabledLibraries(sc_id);
        List<BuiltInLibraries.BuiltInLibrary> effectiveLibraries = BuiltInLibraryManager.getEffectiveEnabledLibraries(sc_id);
        icon.setImageResource(EnableBuiltInLibrariesActivity.getItemIcon());
        title.setText(EnableBuiltInLibrariesActivity.getItemTitle());
        if (!enablingEnabled) {
            description.setText(EnableBuiltInLibrariesActivity.getDefaultItemDescription());
        } else if (!effectiveLibraries.isEmpty()) {
            description.setText(effectiveLibraries.size() + " active • " + manualLibraries.size() + " manual");
        } else {
            description.setText(String.format(EnableBuiltInLibrariesActivity.getSelectedLibrariesItemDescription(), manualLibraries.size()));
        }
        enabled.setText(enablingEnabled ? "ON" : "OFF");
        enabled.setSelected(enablingEnabled);
    }
}
