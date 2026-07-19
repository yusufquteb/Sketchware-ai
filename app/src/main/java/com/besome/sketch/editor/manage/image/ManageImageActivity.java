package com.besome.sketch.editor.manage.image;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.lang.ref.WeakReference;

import a.a.a.MA;
import a.a.a.Op;
import a.a.a.fu;
import a.a.a.mB;
import a.a.a.pu;
import pro.sketchware.R;
import pro.sketchware.ai.shared.AiAssistantBottomSheet;
import pro.sketchware.ai.shared.AiPageConfig;
import pro.sketchware.databinding.ManageImageBinding;
import androidx.activity.OnBackPressedCallback;

public class ManageImageActivity extends BaseAppCompatActivity implements ViewPager.OnPageChangeListener {
    private String sc_id;
    private pu projectImagesFragment;
    private fu collectionImagesFragment;
    private ManageImageBinding binding;

    public static int getImageGridColumnCount(Context context) {
        var displayMetrics = context.getResources().getDisplayMetrics();
        return (int) (displayMetrics.widthPixels / displayMetrics.density) / 100;
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    public void f(int i) {
        binding.viewPager.setCurrentItem(i);
    }

    public fu l() {
        return collectionImagesFragment;
    }

    public pu m() {
        return projectImagesFragment;
    }

        /**
     * Back-press logic extracted from the former @Override onBackPressed().
     * Called by OnBackPressedCallback registered in onCreate().
     */
    private void handleBackPress() {
        if (projectImagesFragment.isSelecting) {
            projectImagesFragment.a(false);
        } else if (collectionImagesFragment.isSelecting()) {
            collectionImagesFragment.unselectAll();
            binding.layoutBtnImport.setVisibility(View.GONE);
        } else {
            k();
            new SaveImagesAsyncTask(this).schedule(500L);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ManageImageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (!super.isStoragePermissionGranted()) {
            finish();
        }

        setSupportActionBar(binding.topAppBar);
        binding.topAppBar.setTitle(R.string.design_actionbar_title_manager_image);
        binding.topAppBar.setNavigationOnClickListener(v -> {
            if (!mB.a()) {
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
        // Do NOT use toolbar.inflateMenu() + setOnMenuItemClickListener() here because
        // it would intercept ALL menu clicks, preventing the fragment (pu) from ever
        // receiving its own manage_image_menu item clicks (e.g. menu_image_import).
        // Instead, inflate the AI menu via the standard options menu in onCreateOptionsMenu()
        // and handle all menu items in onOptionsItemSelected().
        if (savedInstanceState == null) {
            sc_id = getIntent().getStringExtra("sc_id");
        } else {
            sc_id = savedInstanceState.getString("sc_id");
        }

        binding.viewPager.setAdapter(new PagerAdapter(getSupportFragmentManager()));
        binding.viewPager.setOffscreenPageLimit(2);
        binding.viewPager.addOnPageChangeListener(this);
        binding.tabLayout.setupWithViewPager(binding.viewPager);
    
        // Register back-press callback — required on targetSdk >= 33
        // where @Override onBackPressed() is bypassed by the dispatcher.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });
}

    @Override
    public void onResume() {
        super.onResume();
        if (!super.isStoragePermissionGranted()) {
            finish();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPageSelected(int position) {
        binding.layoutBtnGroup.setVisibility(View.GONE);
        binding.layoutBtnImport.setVisibility(View.GONE);

        if (position == 0) {
            binding.fab.animate().translationY(0F).setDuration(200L).start();
            binding.fab.show();
            collectionImagesFragment.unselectAll();
        } else {
            binding.fab.animate().translationY(400F).setDuration(200L).start();
            binding.fab.hide();
            projectImagesFragment.a(false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the AI action item alongside whatever the fragment (pu) adds via
        // setHasOptionsMenu(true). Using the standard options-menu pipeline ensures
        // that fragment's onOptionsItemSelected() is also reachable.
        getMenuInflater().inflate(R.menu.compile_log_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_ai_fix && sc_id != null) {
            openImageAi();
            return true;
        }
        // Delegate unhandled items to fragments (e.g. menu_image_import, menu_image_delete)
        return super.onOptionsItemSelected(item);
    }

    private void openImageAi() {
        java.util.List<AiPageConfig.Tool> tools = new java.util.ArrayList<>();
        tools.add(new AiPageConfig.Tool("Image Resources", R.drawable.ic_mtrl_image));
        tools.add(AiPageConfig.Tool.ai("List Project Images",
                R.drawable.ic_mtrl_box,
                "List all images in project " + sc_id + " and suggest optimizations."));
        tools.add(AiPageConfig.Tool.ai("Suggest Image Assets",
                R.drawable.ic_mtrl_image,
                "Suggest drawable/icon assets needed for a typical Android app."));
        tools.add(AiPageConfig.Tool.ai("Generate Placeholder Drawable",
                R.drawable.ic_mtrl_article,
                "Create an XML placeholder drawable for: [describe the drawable]"));

        AiPageConfig config = new AiPageConfig.Builder()
                .pageTitle("Image Manager AI")
                .scopeLabel("Project: " + sc_id)
                .inputHint("Ask about images or drawables…")
                .systemPrompt("You are an Android image/drawable resource assistant inside Sketchware Pro.\n"
                        + "Project sc_id: " + sc_id + "\n"
                        + "Help the user manage images, create drawable XML files, optimize image assets, "
                        + "and suggest appropriate Android image resources.\n"
                        + "Use read_file and write_file tools to inspect or create drawable files in res/drawable/.\n"
                        + "Reply in the user's language.")
                .tools(tools)
                .projectIds(java.util.Arrays.asList(sc_id))
                .workspaceId(sc_id)
                .build();

        AiAssistantBottomSheet.newInstance(config)
                .show(getSupportFragmentManager(), "image_ai");
    }

    private static class SaveImagesAsyncTask extends MA {
        private final WeakReference<ManageImageActivity> activity;

        public SaveImagesAsyncTask(ManageImageActivity activity) {
            super(activity);
            this.activity = new WeakReference<>(activity);
            activity.a(this);
        }

        @Override
        public void a() {
            var activity = this.activity.get();
            activity.h();
            activity.setResult(Activity.RESULT_OK);
            activity.finish();
            Op.g().d();
        }

        @Override
        public void b() {
            activity.get().projectImagesFragment.saveImages();
        }

        @Override
        public void a(String str) {
            activity.get().h();
        }
    }

    private class PagerAdapter extends FragmentPagerAdapter {
        private final String[] labels;

        public PagerAdapter(FragmentManager manager) {
            super(manager);
            labels = new String[2];
            labels[0] = getString(R.string.design_manager_tab_title_this_project);
            labels[1] = getString(R.string.design_manager_tab_title_my_collection);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        @NonNull
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            if (position == 0) {
                projectImagesFragment = (pu) fragment;
            } else {
                collectionImagesFragment = (fu) fragment;
            }
            return fragment;
        }

        @Override
        @NonNull
        public Fragment getItem(int position) {
            if (position != 0) {
                return new fu();
            }
            return new pu();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return labels[position];
        }
    }
}
