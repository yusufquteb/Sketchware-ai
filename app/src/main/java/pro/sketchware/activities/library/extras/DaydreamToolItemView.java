package pro.sketchware.activities.library.extras;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.materialswitch.MaterialSwitch;

import pro.sketchware.R;
import pro.sketchware.utility.ThemeUtils;

/**
 * A single toggle row used inside the "daydream tools" cards in
 * {@link com.besome.sketch.editor.manage.library.ManageLibraryActivity}.
 * <p>
 * Mirrors the row style used by daydream's LibrarySettings / GoogleSettings /
 * UISettings screens: title, description, an optional dependency note (shown
 * only when the feature is currently unavailable), and a switch.
 */
public class DaydreamToolItemView extends FrameLayout {

    private final TextView titleView;
    private final TextView descView;
    private final TextView noteView;
    private final MaterialSwitch switchView;
    private final MaterialDivider divider;
    private final View container;

    public DaydreamToolItemView(@NonNull Context context) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.manage_library_daydream_tool_item, this, true);

        container = findViewById(R.id.container);
        titleView = findViewById(R.id.tv_title);
        descView = findViewById(R.id.tv_desc);
        noteView = findViewById(R.id.tv_note);
        switchView = findViewById(R.id.sw_toggle);
        divider = findViewById(R.id.divider);

        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        int dividerColor = ThemeUtils.isDarkThemeEnabled(context)
                ? com.google.android.material.R.attr.colorSurfaceContainerHighest
                : com.google.android.material.R.attr.colorOutlineVariant;
        divider.setDividerColor(ThemeUtils.getColor(context, dividerColor));
    }

    public DaydreamToolItemView setTitle(CharSequence title) {
        titleView.setText(title);
        return this;
    }

    public DaydreamToolItemView setDescription(CharSequence desc) {
        descView.setText(desc);
        return this;
    }

    public DaydreamToolItemView setChecked(boolean checked) {
        switchView.setChecked(checked);
        return this;
    }

    public boolean isChecked() {
        return switchView.isChecked();
    }

    public DaydreamToolItemView setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener listener) {
        switchView.setOnCheckedChangeListener(listener);
        return this;
    }

    public void toggle() {
        switchView.toggle();
    }

    /**
     * Shows a dependency note under the description (e.g. "To use, enable Firebase.")
     * and disables the row. Passing {@code null} clears the note and re-enables the row.
     */
    public DaydreamToolItemView setDependencyNote(@Nullable String note) {
        if (note == null) {
            noteView.setVisibility(View.GONE);
            setRowEnabled(true);
        } else {
            noteView.setText(note);
            noteView.setVisibility(View.VISIBLE);
            setRowEnabled(false);
        }
        return this;
    }

    private void setRowEnabled(boolean enabled) {
        container.setEnabled(enabled);
        switchView.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.6f);
    }

    public DaydreamToolItemView showDivider(boolean show) {
        divider.setVisibility(show ? View.VISIBLE : View.GONE);
        return this;
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener listener) {
        container.setOnClickListener(listener);
    }
}
