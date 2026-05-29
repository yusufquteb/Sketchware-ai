package mod.jbk.export;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;

import a.a.a.wq;
import kellinwood.security.zipsigner.optional.CertCreator;
import kellinwood.security.zipsigner.optional.DistinguishedNameValues;
import mod.hey.studios.util.Helper;
import org.spongycastle.asn1.x500.style.BCStyle;
import pro.sketchware.R;
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
        boolean isCreate   = mode == SigningMode.CREATE_KEYSTORE;
        boolean isSigning  = mode != SigningMode.DONT_SIGN;

        binding.cardKeystore.setVisibility(isKeystore ? View.VISIBLE : View.GONE);
        binding.cardCreate.setVisibility(isCreate   ? View.VISIBLE : View.GONE);
        binding.cardSchemes.setVisibility(isSigning  ? View.VISIBLE : View.GONE);

        switch (mode) {
            case CREATE_KEYSTORE: binding.btnBuild.setText("Create & Build"); break;
            case DONT_SIGN:       binding.btnBuild.setText("Build unsigned"); break;
            default:              binding.btnBuild.setText("Build"); break;
        }
    }

    private void setupButtons() {
        binding.btnCancel.setOnClickListener(v -> bottomSheet.dismiss());
        binding.btnBuild.setOnClickListener(v -> onBuildClick());
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

            case CREATE_KEYSTORE:
                if (!validateCreate()) return;
                createAndBuild();
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

    private void createAndBuild() {
        String alias    = Helper.getText(binding.etNewAlias).trim();
        String password = Helper.getText(binding.etNewPassword);
        String validityStr = Helper.getText(binding.etValidity).trim();
        int validity = 25;
        try { validity = Integer.parseInt(validityStr); } catch (Exception ignored) {}

        DistinguishedNameValues dn = new DistinguishedNameValues();
        String cn      = Helper.getText(binding.etCn).trim();
        String ou      = Helper.getText(binding.etOu).trim();
        String org     = Helper.getText(binding.etOrg).trim();
        String city    = Helper.getText(binding.etCity).trim();
        String state   = Helper.getText(binding.etState).trim();
        String country = Helper.getText(binding.etCountry).trim();
        if (!cn.isEmpty())      dn.put(BCStyle.CN, cn);
        if (!ou.isEmpty())      dn.put(BCStyle.OU, ou);
        if (!org.isEmpty())     dn.put(BCStyle.O,  org);
        if (!city.isEmpty())    dn.put(BCStyle.L,  city);
        if (!state.isEmpty())   dn.put(BCStyle.ST, state);
        if (!country.isEmpty()) dn.put(BCStyle.C,  country);

        String keystorePath = wq.j();
        try {
            File dir = new File(keystorePath).getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            CertCreator.createKeystoreAndKey(
                    keystorePath, password.toCharArray(),
                    "RSA", 2048, alias, password.toCharArray(),
                    "SHA256withRSA", validity, dn);
            preferences.edit()
                    .putString(PREF_SIGNING_MODE, SigningMode.OWN_KEY_STORE.name())
                    .putString(PREF_KEYSTORE_PATH, keystorePath)
                    .putString(PREF_KEY_ALIAS, alias)
                    .apply();
            bottomSheet.dismiss();
            receiver.gotCredentials(new Credentials(
                    keystorePath, alias, password, password,
                    binding.cbV1.isChecked(), binding.cbV2.isChecked(),
                    binding.cbV3.isChecked(), binding.cbV4.isChecked()
            ));
        } catch (Exception e) {
            SketchwareUtil.toastError("Failed to create keystore: " + e.getMessage());
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

    private boolean validateCreate() {
        boolean ok = true;
        String alias = Helper.getText(binding.etNewAlias).trim();
        String pwd   = Helper.getText(binding.etNewPassword);
        String pwd2  = Helper.getText(binding.etNewPasswordConfirm);

        if (TextUtils.isEmpty(alias)) {
            binding.tilNewAlias.setError("Certificate name can't be empty");
            ok = false;
        } else {
            binding.tilNewAlias.setError(null);
        }
        if (pwd.length() < 6) {
            binding.tilNewPassword.setError("Password must be at least 6 characters");
            ok = false;
        } else {
            binding.tilNewPassword.setError(null);
        }
        if (!pwd.equals(pwd2)) {
            binding.tilNewPasswordConfirm.setError("Passwords do not match");
            ok = false;
        } else {
            binding.tilNewPasswordConfirm.setError(null);
        }
        String country = Helper.getText(binding.etCountry).trim();
        if (!country.isEmpty() && country.length() != 2) {
            binding.tilCountry.setError("Country code must be 2 letters");
            ok = false;
        } else {
            binding.tilCountry.setError(null);
        }
        if (!ok) SketchwareUtil.toastError("Please fix the keystore inputs and try again.");
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
        CREATE_KEYSTORE("Create new keystore"),
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
