package mod.jbk.export;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;

import com.besome.sketch.tools.NewKeyStoreActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;

import a.a.a.wq;
import mod.hey.studios.util.Helper;
import pro.sketchware.databinding.DialogKeystoreCredentialsBinding;
import pro.sketchware.utility.SketchwareUtil;

public class GetKeyStoreCredentialsDialog {

    private static final String PREFS_NAME = "export_signing_prefs";
    private static final String PREF_SIGNING_MODE = "signing_mode";
    private static final String PREF_KEYSTORE_PATH = "keystore_path";
    private static final String PREF_KEY_ALIAS = "key_alias";

    private final Activity activity;
    private final BottomSheetDialog bottomSheet;
    private final DialogKeystoreCredentialsBinding binding;
    private final SharedPreferences preferences;
    private CredentialsReceiver receiver;
    private SigningMode mode;

    public GetKeyStoreCredentialsDialog(Activity activity, int iconResourceId, String title, String noticeText) {
        this.activity = activity;
        bottomSheet = new BottomSheetDialog(activity);
        binding = DialogKeystoreCredentialsBinding.inflate(LayoutInflater.from(activity));
        bottomSheet.setContentView(binding.getRoot());
        preferences = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);

        binding.tvSubtitle.setText(noticeText);

        setupDropdown();
        restoreLastUsedValues();
        setupButtons();
    }

    private void setupDropdown() {
        String[] labels = new String[SigningMode.values().length];
        for (int i = 0; i < SigningMode.values().length; i++) {
            labels[i] = SigningMode.values()[i].label;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, labels);
        binding.actSigningMode.setAdapter(adapter);
        binding.actSigningMode.setOnItemClickListener((parent, view, position, id) -> {
            mode = SigningMode.values()[position];
            updateSectionVisibility();
        });
    }

    private void restoreLastUsedValues() {
        String defaultPath = preferences.getString(PREF_KEYSTORE_PATH, wq.j());
        binding.etKeystorePath.setText(defaultPath);
        binding.etAlias.setText(preferences.getString(PREF_KEY_ALIAS, ""));

        String savedMode = preferences.getString(PREF_SIGNING_MODE, SigningMode.OWN_KEY_STORE.name());
        try {
            mode = SigningMode.valueOf(savedMode);
        } catch (Exception ignored) {
            mode = SigningMode.OWN_KEY_STORE;
        }
        binding.actSigningMode.setText(mode.label, false);
        updateSectionVisibility();
    }

    private void updateSectionVisibility() {
        boolean isKeystore = mode == SigningMode.OWN_KEY_STORE;
        boolean isSigning  = mode != SigningMode.DONT_SIGN;

        binding.cardKeystore.setVisibility(isKeystore ? View.VISIBLE : View.GONE);
        binding.cardSchemes.setVisibility(isSigning  ? View.VISIBLE : View.GONE);
        binding.btnBuild.setText(mode == SigningMode.DONT_SIGN ? "Build unsigned" : "Build");
    }

    private void setupButtons() {
        binding.btnCancel.setOnClickListener(v -> bottomSheet.dismiss());
        binding.btnBuild.setOnClickListener(v -> onBuildClick());
        binding.btnCreateKeystore.setOnClickListener(v -> openCreateKeystore());
    }

    private void openCreateKeystore() {
        // Pre-fill the path with the default release key location so the user
        // can see where the new keystore will land once creation is complete.
        binding.etKeystorePath.setText(wq.j());
        // Switch to OWN_KEY_STORE mode so the credentials card is visible on return.
        mode = SigningMode.OWN_KEY_STORE;
        binding.actSigningMode.setText(mode.label, false);
        updateSectionVisibility();
        activity.startActivity(new Intent(activity, NewKeyStoreActivity.class));
    }

    private void onBuildClick() {
        switch (mode) {
            case OWN_KEY_STORE:
                if (!validateKeystore()) return;
                savePreferences();
                bottomSheet.dismiss();
                receiver.gotCredentials(new Credentials(
                        Helper.getText(binding.etKeystorePath),
                        Helper.getText(binding.etAlias),
                        Helper.getText(binding.etKeystorePassword),
                        Helper.getText(binding.etKeyPassword),
                        binding.cbV1.isChecked(),
                        binding.cbV2.isChecked(),
                        binding.cbV3.isChecked(),
                        binding.cbV4.isChecked()
                ));
                break;

            case TESTKEY:
                preferences.edit().putString(PREF_SIGNING_MODE, mode.name()).apply();
                bottomSheet.dismiss();
                receiver.gotCredentials(Credentials.forTestkey());
                break;

            case DONT_SIGN:
                preferences.edit().putString(PREF_SIGNING_MODE, mode.name()).apply();
                bottomSheet.dismiss();
                receiver.gotCredentials(null);
                break;
        }
    }

    private boolean validateKeystore() {
        boolean ok = true;
        String path = Helper.getText(binding.etKeystorePath).trim();
        if (TextUtils.isEmpty(path)) {
            binding.tilKeystorePath.setError("Keystore path can't be empty");
            ok = false;
        } else if (!new File(path).isFile()) {
            binding.tilKeystorePath.setError("Keystore file not found");
            ok = false;
        } else {
            binding.tilKeystorePath.setError(null);
        }
        if (TextUtils.isEmpty(binding.etKeystorePassword.getText())) {
            binding.tilKeystorePassword.setError("Password can't be empty");
            ok = false;
        } else {
            binding.tilKeystorePassword.setError(null);
        }
        if (TextUtils.isEmpty(binding.etAlias.getText())) {
            binding.tilAlias.setError("Alias can't be empty");
            ok = false;
        } else {
            binding.tilAlias.setError(null);
        }
        if (TextUtils.isEmpty(binding.etKeyPassword.getText())) {
            binding.tilKeyPassword.setError("Key password can't be empty");
            ok = false;
        } else {
            binding.tilKeyPassword.setError(null);
        }
        if (!ok) SketchwareUtil.toastError("Please fix the signing inputs and try again.");
        return ok;
    }

    private void savePreferences() {
        preferences.edit()
                .putString(PREF_SIGNING_MODE, mode.name())
                .putString(PREF_KEYSTORE_PATH, Helper.getText(binding.etKeystorePath))
                .putString(PREF_KEY_ALIAS, Helper.getText(binding.etAlias))
                .apply();
    }

    public void show() {
        bottomSheet.show();
    }

    public void setListener(CredentialsReceiver receiver) {
        this.receiver = receiver;
    }

    private enum SigningMode {
        OWN_KEY_STORE("Sign with keystore"),
        TESTKEY("Sign with test key"),
        DONT_SIGN("Don't sign");

        final String label;
        SigningMode(String label) { this.label = label; }
    }

    public interface CredentialsReceiver {
        void gotCredentials(Credentials credentials);
    }

    public static class Credentials {
        private final boolean signWithTestkey;
        private final String keyStorePath;
        private final String keyAlias;
        private final String keyStorePassword;
        private final String keyPassword;
        private final boolean enableV1, enableV2, enableV3, enableV4;

        private Credentials(boolean signWithTestkey, String keyStorePath, String keyAlias,
                            String keyStorePassword, String keyPassword,
                            boolean enableV1, boolean enableV2, boolean enableV3, boolean enableV4) {
            this.signWithTestkey  = signWithTestkey;
            this.keyStorePath     = keyStorePath;
            this.keyAlias         = keyAlias;
            this.keyStorePassword = keyStorePassword;
            this.keyPassword      = keyPassword;
            this.enableV1 = enableV1; this.enableV2 = enableV2;
            this.enableV3 = enableV3; this.enableV4 = enableV4;
        }

        public Credentials(String keyStorePath, String keyAlias, String keyStorePassword,
                           String keyPassword,
                           boolean enableV1, boolean enableV2, boolean enableV3, boolean enableV4) {
            this(false, keyStorePath, keyAlias, keyStorePassword, keyPassword,
                    enableV1, enableV2, enableV3, enableV4);
        }

        public static Credentials forTestkey() {
            return new Credentials(true, null, null, null, null, true, true, true, false);
        }

        public boolean isForSigningWithTestkey() { return signWithTestkey; }
        public String  getKeyStorePath()         { return keyStorePath; }
        public String  getKeyAlias()             { return keyAlias; }
        public String  getKeyStorePassword()     { return keyStorePassword; }
        public String  getKeyPassword()          { return keyPassword; }
        public boolean isEnableV1()              { return enableV1; }
        public boolean isEnableV2()              { return enableV2; }
        public boolean isEnableV3()              { return enableV3; }
        public boolean isEnableV4()              { return enableV4; }
        /** Legacy compat — callers that still read the algorithm get a sensible default. */
        public String  getSigningAlgorithm()     { return "SHA256withRSA"; }
    }
}
