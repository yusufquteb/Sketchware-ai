package pro.sketchware.activities.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import pro.sketchware.R;
import pro.sketchware.utility.theme.ThemeManager;

/**
 * ThemeSettingsActivity — unified theme screen.
 * Top half: Appearance (system default toggle + light/dark selection cards).
 * Bottom half: Color Preset picker.
 * Accessible from: Settings → "Theme Settings"
 */
public class ThemeSettingsActivity extends AppCompatActivity {

    private MaterialCardView themeLight;
    private MaterialCardView themeDark;
    private MaterialSwitch switchSystem;
    private RecyclerView.Adapter<?> presetAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        applyInsets();
        setupAppearanceSection();
        setupColorPresets();
    }

    private void applyInsets() {
        View appBarLayout = findViewById(R.id.app_bar_layout);
        if (appBarLayout != null) {
            int left = appBarLayout.getPaddingLeft();
            int top = appBarLayout.getPaddingTop();
            int right = appBarLayout.getPaddingRight();
            int bottom = appBarLayout.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(appBarLayout, (v, insets) -> {
                Insets sysInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + sysInsets.left, top + sysInsets.top, right + sysInsets.right, bottom);
                return insets;
            });
        }

        View content = findViewById(R.id.content);
        if (content != null) {
            int left = content.getPaddingLeft();
            int top = content.getPaddingTop();
            int right = content.getPaddingRight();
            int bottom = content.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
                Insets sysInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(left, top, right, bottom + sysInsets.bottom);
                return insets;
            });
        }
    }

    // ── Appearance section (system default / light / dark) ──────────────

    private void setupAppearanceSection() {
        MaterialCardView themeSystem = findViewById(R.id.theme_system);
        themeLight = findViewById(R.id.theme_light);
        themeDark = findViewById(R.id.theme_dark);
        switchSystem = findViewById(R.id.switch_system);

        boolean isSystemTheme = ThemeManager.isSystemMode(this);
        switchSystem.setChecked(isSystemTheme);
        updateThemeCardSelection(ThemeManager.getCurrentNightMode(this));
        setThemeCardsEnabled(!isSystemTheme);

        themeSystem.setOnClickListener(v -> switchSystem.setChecked(!switchSystem.isChecked()));

        switchSystem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setThemeCardsEnabled(!isChecked);
            if (isChecked) {
                ThemeManager.applyNightMode(this, ThemeManager.THEME_SYSTEM);
                unselectThemeCards();
                return;
            }
            int resolved = ThemeManager.getSystemAppliedTheme(this);
            ThemeManager.applyNightMode(this, resolved);
            updateThemeCardSelection(resolved);
        });

        themeLight.setOnClickListener(v -> {
            if (!switchSystem.isChecked()) {
                ThemeManager.applyNightMode(this, ThemeManager.THEME_LIGHT);
                updateThemeCardSelection(ThemeManager.THEME_LIGHT);
            }
        });

        themeDark.setOnClickListener(v -> {
            if (!switchSystem.isChecked()) {
                ThemeManager.applyNightMode(this, ThemeManager.THEME_DARK);
                updateThemeCardSelection(ThemeManager.THEME_DARK);
            }
        });
    }

    private void updateThemeCardSelection(int mode) {
        unselectThemeCards();
        if (switchSystem.isChecked()) return;
        if (mode == ThemeManager.THEME_LIGHT) themeLight.setChecked(true);
        else if (mode == ThemeManager.THEME_DARK) themeDark.setChecked(true);
    }

    private void unselectThemeCards() {
        themeLight.setChecked(false);
        themeDark.setChecked(false);
    }

    private void setThemeCardsEnabled(boolean enabled) {
        themeLight.setEnabled(enabled);
        themeDark.setEnabled(enabled);

        float alpha = enabled ? 1.0f : 0.5f;
        themeLight.animate().alpha(alpha).start();
        themeDark.animate().alpha(alpha).start();
    }

    // ── Color preset section ─────────────────────────────────────────────

    private void setupColorPresets() {
        RecyclerView rv = findViewById(R.id.rv_color_presets);
        rv.setLayoutManager(new GridLayoutManager(this, 3));
        presetAdapter = new PresetAdapter();
        rv.setAdapter(presetAdapter);
    }

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
                notifyItemRangeChanged(0, getItemCount());

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
