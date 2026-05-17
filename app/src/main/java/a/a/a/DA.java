package a.a.a;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import pro.sketchware.R;
import pro.sketchware.utility.FileUtil;

public abstract class DA extends qA {
    private int pendingStorageRequestCode = -1;

    public DA() {
    }

    public boolean a(int requestCode) {
        boolean granted = c();
        if (!granted) {
            d(requestCode);
        }
        return granted;
    }

    public abstract void b(int requestCode);

    public abstract void c(int requestCode);

    public boolean c() {
        if (Build.VERSION.SDK_INT > 29) {
            return Environment.isExternalStorageManager();
        }
        return FileUtil.hasStorageAccess(requireContext());
    }

    public abstract void d();

    public void d(int requestCode) {
        if (!Sp.a) {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(super.a);
            dialog.setTitle(R.string.common_message_permission_title_storage);
            dialog.setIcon(R.drawable.break_warning_96_red);
            dialog.setMessage(Build.VERSION.SDK_INT > 29
                    ? "On Android 11 and higher, Sketchware Pro needs full file access to work with project folders, libraries, backups, and exports." 
                    : getString(R.string.common_message_permission_storage));
            dialog.setPositiveButton(R.string.common_word_ok, (view, which) -> {
                if (!mB.a()) {
                    requestStoragePermission(requestCode);
                    view.dismiss();
                }
            });
            dialog.setNegativeButton(R.string.common_word_cancel, (view, which) -> {
                d();
                view.dismiss();
            });
            dialog.setOnDismissListener(dialog1 -> Sp.a = false);
            dialog.setCancelable(false);
            dialog.create().setCanceledOnTouchOutside(false);
            dialog.show();
            Sp.a = true;
        }
    }

    public abstract void e();

    public void e(int requestCode) {
        if (!Sp.a) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(super.a);
            builder.setTitle(R.string.common_message_permission_title_storage);
            builder.setIcon(R.drawable.break_warning_96_red);
            builder.setMessage(Build.VERSION.SDK_INT > 29
                    ? "Sketchware Pro still needs full file access. Open settings and grant \"Allow access to manage all files\" for Sketchware Pro." 
                    : getString(R.string.common_message_permission_storage1));
            builder.setPositiveButton(R.string.common_word_settings, (view, which) -> {
                if (!mB.a()) {
                    requestStoragePermission(requestCode);
                    view.dismiss();
                }
            });
            builder.setNegativeButton(R.string.common_word_cancel, (view, which) -> {
                e();
                view.dismiss();
            });
            builder.setOnDismissListener(dialog1 -> Sp.a = false);
            builder.setCancelable(false);

            var dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
            Sp.a = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, @NonNull int[] grantResults) {
        if (Build.VERSION.SDK_INT > 29) {
            if (c()) {
                b(requestCode);
            } else {
                e(requestCode);
            }
            return;
        }
        for (String permission : permissions) {
            if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
                if (grantResults.length > 1 && grantResults[0] == 0 && grantResults[1] == 0) {
                    b(requestCode);
                } else {
                    e(requestCode);
                }
                return;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pendingStorageRequestCode != -1 && c()) {
            int requestCode = pendingStorageRequestCode;
            pendingStorageRequestCode = -1;
            b(requestCode);
        }
    }

    private void requestStoragePermission(int requestCode) {
        pendingStorageRequestCode = requestCode;
        if (Build.VERSION.SDK_INT > 29) {
            if (Environment.isExternalStorageManager()) {
                int grantedRequestCode = pendingStorageRequestCode;
                pendingStorageRequestCode = -1;
                b(grantedRequestCode);
            } else {
                FileUtil.requestAllFilesAccessPermission(requireContext());
            }
            return;
        }
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, requestCode);
    }
}
