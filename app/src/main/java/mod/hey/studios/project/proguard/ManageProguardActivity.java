package mod.hey.studios.project.proguard;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;

import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
import mod.hey.studios.code.SrcCodeEditor;
import pro.sketchware.R;
import pro.sketchware.databinding.ManageProguardBinding;

public class ManageProguardActivity extends BaseAppCompatActivity
        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private ProguardHandler pg;

    private ManageProguardBinding binding;

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == binding.lnPgRules.getId()) {
            Intent intent = new Intent(this, SrcCodeEditor.class);
            intent.putExtra("title", "proguard-rules.pro");
            intent.putExtra("content", pg.getCustomProguardRules());
            startActivity(intent);
        } else if (id == binding.lnPgFm.getId()) {
            fmDialog();
        } else if (id == binding.lnR8Profile.getId()) {
            showR8ProfileDialog();
        }
    }

    private void fmDialog() {
        ManageLocalLibrary mll = new ManageLocalLibrary(getIntent().getStringExtra("sc_id"));

        String[] libraries = new String[mll.list.size()];
        boolean[] enabledLibraries = new boolean[mll.list.size()];

        for (int i = 0; i < mll.list.size(); i++) {
            HashMap<String, Object> current = mll.list.get(i);

            Object name = current.get("name");
            if (name instanceof String) {
                libraries[i] = (String) name;
                enabledLibraries[i] = pg.libIsProguardFMEnabled(libraries[i]);
            } else {
                libraries[i] = "(broken library configuration)";
                enabledLibraries[i] = false;
            }
        }

        MaterialAlertDialogBuilder bld = new MaterialAlertDialogBuilder(this);
        bld.setTitle("Select Local libraries");
        bld.setMultiChoiceItems(
                libraries,
                enabledLibraries,
                (dialog, which, isChecked) -> enabledLibraries[which] = isChecked);
        bld.setPositiveButton(
                R.string.common_word_save,
                (dialog, which) -> {
                    ArrayList<String> finalList = new ArrayList<>();

                    for (int i = 0; i < libraries.length; i++) {
                        if (enabledLibraries[i]) {
                            finalList.add(libraries[i]);
                        }
                    }

                    pg.setProguardFMLibs(finalList);
                });
        bld.setNegativeButton(R.string.common_word_cancel, null);
        bld.create().show();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        int id = buttonView.getId();
        if (id == binding.swPgEnabled.getId()) {
            pg.setProguardEnabled(isChecked);
        } else if (id == binding.r8Enabled.getId()) {
            pg.setR8Enabled(isChecked);
        } else if (id == binding.swPgDebug.getId()) {
            pg.setDebugEnabled(isChecked);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ManageProguardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initialize();
        initializeLogic();
    }

    private void initialize() {
        binding.swPgEnabled.setOnCheckedChangeListener(this);
        binding.lnPgRules.setOnClickListener(this);
        binding.r8Enabled.setOnCheckedChangeListener(this);
        binding.swPgDebug.setOnCheckedChangeListener(this);
        binding.lnPgFm.setOnClickListener(this);
        binding.lnR8Profile.setOnClickListener(this);
    }

    private void initializeLogic() {
        _initToolbar();
        pg = new ProguardHandler(getIntent().getStringExtra("sc_id"));
        binding.swPgEnabled.setChecked(pg.isShrinkingEnabled());
        binding.swPgDebug.setChecked(pg.isDebugFilesEnabled());
        binding.r8Enabled.setChecked(pg.isR8Enabled());
        updateR8ProfileSummary();
    }


    private void updateR8ProfileSummary() {
        R8Profiles.Profile profile = pg.getR8Profile();
        binding.tvR8ProfileValue.setText(profile.getDisplayName() + " - " + profile.getDescription());
    }

    private void showR8ProfileDialog() {
        java.util.List<R8Profiles.Profile> profiles = R8Profiles.getAll();
        String[] labels = new String[profiles.size()];
        int checkedItem = 0;
        String currentProfile = pg.getR8ProfileId();
        for (int i = 0; i < profiles.size(); i++) {
            labels[i] = profiles.get(i).getDisplayName() + "\n" + profiles.get(i).getDescription();
            if (profiles.get(i).getId().equals(currentProfile)) {
                checkedItem = i;
            }
        }

        final int[] selected = new int[]{checkedItem};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select R8 optimization profile")
                .setSingleChoiceItems(labels, checkedItem, (dialog, which) -> selected[0] = which)
                .setPositiveButton(R.string.common_word_save, (dialog, which) -> {
                    pg.setR8ProfileId(profiles.get(selected[0]).getId());
                    updateR8ProfileSummary();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void _initToolbar() {
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Code Shrinking Manager");
        binding.toolbar.setNavigationOnClickListener(view -> onBackPressed());
    }
}
