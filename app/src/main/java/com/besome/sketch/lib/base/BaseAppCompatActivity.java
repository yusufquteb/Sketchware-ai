package com.besome.sketch.lib.base;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.ui.LoadingDialog;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.ArrayList;

import a.a.a.MA;
import a.a.a.lC;
import dev.chrisbanes.insetter.Insetter;
import pro.sketchware.dialogs.ProgressDialog;
import pro.sketchware.utility.theme.ThemeManager;

public abstract class BaseAppCompatActivity extends AppCompatActivity {

    public FirebaseAnalytics mAnalytics;

    @Deprecated
    public Context e;
    public Activity parent;
    protected ProgressDialog progressDialog;
    private LoadingDialog lottieDialog;
    private ArrayList<MA> taskList;

    public void a(MA var1) {
        taskList.add(var1);
    }

    public void addTask(MA task) {
        taskList.add(task);
    }

    public void a(OnCancelListener cancelListener) {
        if (progressDialog != null && !progressDialog.isShowing()) {
            progressDialog.setOnCancelListener(cancelListener);
            progressDialog.show();
        }
    }

    public void a(String var1) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.setMessage(var1);
        }
    }

    public void g() {
        for (MA task : taskList) {
            if (task.getStatus() != MA.Status.FINISHED && !task.isCancelled()) {
                task.cancel(true);
            }
        }
        taskList.clear();
    }

    public void h() {
        try {
            if (lottieDialog != null && lottieDialog.isShowing()) {
                lottieDialog.dismiss();
            }
        } catch (Exception var2) {
            lottieDialog = null;
            lottieDialog = new LoadingDialog(this);
        }
    }

    public void i() {
        try {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        } catch (Exception var2) {
            progressDialog = null;
            progressDialog = new ProgressDialog(this);
        }

    }

    public boolean isStoragePermissionGranted() {
        return pro.sketchware.utility.FileUtil.hasStorageAccess(this);
    }

    public boolean j() {
        return isStoragePermissionGranted();
    }

    public void k() {
        if (lottieDialog != null && !lottieDialog.isShowing() && !isFinishing()) {
            lottieDialog.show();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Apply color-preset overlay AFTER super.onCreate().
        // AppCompatActivity.onCreate() runs AppCompatDelegateImpl's DayNight
        // resolution, which re-applies the base theme (Theme.SketchwarePro)
        // onto this Activity's Resources.Theme via setTheme(). If the preset
        // overlay was applied BEFORE super.onCreate(), that re-application
        // wipes it out, leaving attributes like colorPrimary unresolved
        // (TypedValue -> 0xffffffff) the first time a themed view (e.g.
        // LottieAnimationView reading ?colorPrimary) inflates in this
        // Activity. Applying the overlay here, after AppCompat has finished
        // its own theme setup, ensures it survives and is actually visible
        // to every view inflated afterwards. Android still automatically
        // picks the light/dark variant via resource qualifiers.
        int presetRes = ThemeManager.presetStyleRes(ThemeManager.getPreset(this));
        if (presetRes != 0) {
            getTheme().applyStyle(presetRes, true);
        }
        e = getApplicationContext();
        taskList = new ArrayList<>();
        lottieDialog = new LoadingDialog(this);
        lC.a(getApplicationContext(), false);
        progressDialog = new ProgressDialog(this);
        mAnalytics = FirebaseAnalytics.getInstance(this);
    }

    @Override
    public void onDestroy() {
        g();
        if (lottieDialog != null && lottieDialog.isShowing()) {
            lottieDialog.cancelAnimation();
        }
        super.onDestroy();
    }

    @Override
    public void onPause() {
        if (lottieDialog != null && lottieDialog.isShowing()) {
            lottieDialog.pauseAnimation();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (lottieDialog != null && lottieDialog.isShowing()) {
            lottieDialog.resumeAnimation();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (parent != null) {
            return parent.onCreateOptionsMenu(menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (parent != null) {
            return parent.onOptionsItemSelected(item);
        }
        return false;
    }

    public void handleInsetts(View root) {
        Insetter.builder()
                .padding(WindowInsetsCompat.Type.navigationBars())
                .applyToView(root);
    }

    protected void enableEdgeToEdgeNoContrast() {
        // On Android 15+ (SDK 35+) the OS enforces edge-to-edge automatically.
        // Calling EdgeToEdge.enable() again can conflict with Samsung's window inset
        // handling and cause a crash before any Activity content is shown.
        // We still call it on SDK < 35 to maintain the same visual behaviour.
        if (Build.VERSION.SDK_INT < 35) {
            SystemBarStyle systemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT);
            EdgeToEdge.enable(this, systemBarStyle);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }
}