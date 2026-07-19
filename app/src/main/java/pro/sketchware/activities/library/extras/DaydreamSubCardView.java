package pro.sketchware.activities.library.extras;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import pro.sketchware.R;

/**
 * A titled sub-card used to group a set of {@link DaydreamToolItemView} rows
 * inside the "daydream tools" section of ManageLibraryActivity (e.g. one
 * sub-card for "Firebase & Google", one for "User Interface", one for
 * "Extra Libraries").
 */
public class DaydreamSubCardView extends LinearLayout {

    private final LinearLayout container;

    public DaydreamSubCardView(@NonNull Context context, @DrawableRes int iconRes, CharSequence title) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.manage_library_daydream_subcard, this, true);

        ImageView icon = findViewById(R.id.subcard_icon);
        TextView titleView = findViewById(R.id.subcard_title);
        container = findViewById(R.id.subcard_container);

        icon.setImageResource(iconRes);
        titleView.setText(title);

        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    /** Adds a toggle row to this sub-card, with a bottom divider unless it's the last row. */
    public void addToolItem(DaydreamToolItemView item, boolean addDivider) {
        container.addView(item);
        item.showDivider(addDivider);
    }

    /** Adds a plain child view (e.g. the OneSignal App ID input row) with no divider handling. */
    public void addRawView(android.view.View view) {
        container.addView(view);
    }
}
