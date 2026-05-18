package com.besome.sketch.lib.base;

import android.Manifest;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import a.a.a.Sp;
import a.a.a.mB;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.utility.FileUtil;

public abstract class BasePermissionAppCompatActivity extends BaseAppCompatActivity {

    private int pendingStorageRequestCode = -1;

    public boolean f(int requestCode) {
        boolean granted = isStoragePermissionGranted();
        if (!granted) {
            i(requestCode);
        }
        return granted;
    }

    public abstract void g(int requestCode);

    public abstract void h(int requestCode);

    public void i(int requestCode) {
        if (!Sp.a) {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
            dialog.setTitle(Helper.getResString(R.string.common_message_permission_title_storage));
            dialog.setIcon(R.drawable.break_warning_96_red);
            dialog.setMessage(Build.VERSION.SDK_INT > 29
                    ? "On Android 11 and higher, Sketchware Pro needs full file access to work with your existing project directories, libraries, backups, and exports." 
                    : Helper.getResString(R.string.common_message_permission_storage));
            dialog.setPositiveButton(Helper.getResString(R.string.common_word_ok), (v, which) -> {
                if (!mB.a()) {
                    requestStorageAccess(requestCode);
                    v.dismiss();
                }
            });
            dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), (v, which) -> {
                l();
                v.dismiss();
            });
            dialog.setOnDismissListener(dialog1 -> Sp.a = false);
            dialog.setCancelable(false);
            dialog.show();
            Sp.a = true;
        }
    }

    @Override
    public boolean isStoragePermissionGranted() {
        return FileUtil.hasStorageAccess(this);
    }

    public abstract void l();

    public abstract void m();

    @Override
    public void onResume() {
        super.onResume();
        if (pendingStorageRequestCode != -1 && isStoragePermissionGranted()) {
            int requestCode = pendingStorageRequestCode;
            pendingStorageRequestCode = -1;
            g(requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (Build.VERSION.SDK_INT > 29) {
            if (isStoragePermissionGranted()) {
                g(requestCode);
            } else {
                j(requestCode);
            }
            return;
        }
        for (String permission : permissions) {
            if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
                if (grantResults.length > 1 && grantResults[0] == 0 && grantResults[1] == 0) {
                    g(requestCode);
                } else {
                    j(requestCode);
                }
                return;
            }
        }
    }

    public void j(int requestCode) {
        if (!Sp.a) {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
            dialog.setTitle(Helper.getResString(R.string.common_message_permission_title_storage));
            dialog.setIcon(R.drawable.break_warning_96_red);
            dialog.setMessage(Build.VERSION.SDK_INT > 29
                    ? "Sketchware Pro still needs full file access. Open settings and grant \"Allow access to manage all files\" for Sketchware Pro." 
                    : Helper.getResString(R.string.common_message_permission_storage1));
            dialog.setPositiveButton(Helper.getResString(R.string.common_word_settings), (v, which) -> {
                if (!mB.a()) {
                    requestStorageAccess(requestCode);
                    v.dismiss();
                }
            });
            dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), (v, which) -> {
                m();
                v.dismiss();
            });
            dialog.setOnDismissListener(dialog1 -> Sp.a = false);
            dialog.setCancelable(false);
            dialog.show();
            Sp.a = true;
        }
    }

    private void requestStorageAccess(int requestCode) {
        pendingStorageRequestCode = requestCode;
        if (Build.VERSION.SDK_INT > 29) {
            if (Environment.isExternalStorageManager()) {
                int grantedRequestCode = pendingStorageRequestCode;
                pendingStorageRequestCode = -1;
                g(grantedRequestCode);
            } else {
                FileUtil.requestAllFilesAccessPermission(this);
            }
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                requestCode);
    }
}
