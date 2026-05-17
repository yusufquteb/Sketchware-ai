package pro.sketchware.activities.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import pro.sketchware.R;
import pro.sketchware.utility.theme.ThemeManager;

/**
 * ThemeSettingsActivity — color preset picker + dark/light/system toggle.
 * Mirrors the open source ThemeSettings / SettingsAppearanceFragment.
 * Accessible from: MainDrawer → "Theme Manager"
 */
public class ThemeSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        setupNightModeToggles();
        setupColorPresets();
    }

    private void setupNightModeToggles() {
        MaterialSwitch swLight  = findViewById(R.id.sw_theme_light);
        MaterialSwitch swDark   = findViewById(R.id.sw_theme_dark);
        MaterialSwitch swSystem = findViewById(R.id.sw_theme_system);

        int current = ThemeManager.getCurrentNightMode(this);
        swLight .setChecked(current == ThemeManager.THEME_LIGHT);
        swDark  .setChecked(current == ThemeManager.THEME_DARK);
        swSystem.setChecked(current == ThemeManager.THEME_SYSTEM);

        View.OnClickListener l = v -> {
            int mode = ThemeManager.THEME_SYSTEM;
            if (v == swLight  || v == findViewById(R.id.ln_theme_light))  mode = ThemeManager.THEME_LIGHT;
            if (v == swDark   || v == findViewById(R.id.ln_theme_dark))   mode = ThemeManager.THEME_DARK;
            ThemeManager.applyNightMode(this, mode);
            int m = mode;
            swLight .setChecked(m == ThemeManager.THEME_LIGHT);
            swDark  .setChecked(m == ThemeManager.THEME_DARK);
            swSystem.setChecked(m == ThemeManager.THEME_SYSTEM);
        };

        swLight .setOnClickListener(l);
        swDark  .setOnClickListener(l);
        swSystem.setOnClickListener(l);
        findViewById(R.id.ln_theme_light) .setOnClickListener(l);
        findViewById(R.id.ln_theme_dark)  .setOnClickListener(l);
        findViewById(R.id.ln_theme_system).setOnClickListener(l);
    }

    private void setupColorPresets() {
        RecyclerView rv = findViewById(R.id.rv_color_presets);
        rv.setLayoutManager(new GridLayoutManager(this, 3));
        rv.setAdapter(new PresetAdapter());
    }

    // ── Preset Adapter ──────────────────────────────────────────────────

    private class PresetAdapter extends RecyclerView.Adapter<PresetAdapter.VH> {

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_theme_preset, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            boolean dark = ThemeManager.isDarkMode(ThemeSettingsActivity.this);
            int[] colors = dark ? ThemeManager.DARK_PREVIEW[pos] : ThemeManager.LIGHT_PREVIEW[pos];
            h.dot1.setBackgroundColor(colors[0]);
            h.dot2.setBackgroundColor(colors[1]);
            h.dot3.setBackgroundColor(colors[2]);
            h.name.setText(ThemeManager.PRESET_NAMES[pos]);
            int saved = ThemeManager.getPreset(ThemeSettingsActivity.this);
            h.card.setStrokeWidth(saved == pos ? 4 : 0);

            h.card.setOnClickListener(v -> {
                ThemeManager.setPreset(ThemeSettingsActivity.this, pos);
                notifyDataSetChanged(); // update selection highlight immediately

                // Restart the app cleanly so every Activity re-runs onCreate()
                // and picks up the new preset overlay via BaseAppCompatActivity.
                // FLAG_ACTIVITY_CLEAR_TASK tears down the whole back-stack cleanly
                // without killing the process, allowing Android to save state properly.
                Intent restart = ThemeSettingsActivity.this
                        .getPackageManager()
                        .getLaunchIntentForPackage(
                                ThemeSettingsActivity.this.getPackageName());
                if (restart != null) {
                    restart.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    ThemeSettingsActivity.this.startActivity(restart);
                }
            });
        }

        @Override public int getItemCount() { return ThemeManager.PRESET_NAMES.length; }

        class VH extends RecyclerView.ViewHolder {
            MaterialCardView card;
            View dot1, dot2, dot3;
            TextView name;
            VH(View v) {
                super(v);
                card = v.findViewById(R.id.card_preset);
                dot1 = v.findViewById(R.id.dot_primary);
                dot2 = v.findViewById(R.id.dot_secondary);
                dot3 = v.findViewById(R.id.dot_tertiary);
                name = v.findViewById(R.id.tv_preset_name);
            }
        }
    }
}
