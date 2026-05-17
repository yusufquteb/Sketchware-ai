package mod.jbk.export;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.LinkedList;

import a.a.a.wq;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.DialogKeystoreCredentialsBinding;
import pro.sketchware.utility.SketchwareUtil;

public class GetKeyStoreCredentialsDialog {

    private static final String PREFS_NAME = "export_signing_prefs";
    private static final String PREF_SIGNING_MODE = "signing_mode";
    private static final String PREF_KEYSTORE_PATH = "keystore_path";
    private static final String PREF_KEY_ALIAS = "key_alias";
    private static final String PREF_SIGNING_ALGORITHM = "signing_algorithm";
    private static final String DEFAULT_SIGNING_ALGORITHM = "SHA256withRSA";

    private final Activity activity;
    private final MaterialAlertDialogBuilder dialog;
    private final DialogKeystoreCredentialsBinding binding;
    private final SharedPreferences preferences;
    private CredentialsReceiver receiver;
    private SigningMode mode;

    public GetKeyStoreCredentialsDialog(Activity activity, int iconResourceId, String title, String noticeText) {
        this.activity = activity;
        dialog = new MaterialAlertDialogBuilder(activity);
        dialog.setIcon(iconResourceId);
        dialog.setTitle(title);
        dialog.setMessage(noticeText);
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_next), (dialog1, which) -> onNextButtonClick(dialog1));

        binding = DialogKeystoreCredentialsBinding.inflate(LayoutInflater.from(activity));
        dialog.setView(binding.getRoot());
        preferences = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);

        setupSpinner();
        restoreLastUsedValues();
    }

    private void setupSpinner() {
        String[] dropdownItems = getDropdownItems();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, dropdownItems);
        binding.actSigningMode.setAdapter(adapter);
        binding.actSigningMode.setOnItemClickListener((parent, view, position, id) -> {
            mode = SigningMode.values()[position];
            updateInputFieldsState();
        });
    }

    private void restoreLastUsedValues() {
        String defaultPath = preferences.getString(PREF_KEYSTORE_PATH, wq.j());
        binding.etKeystorePath.setText(defaultPath);
        binding.etAlias.setText(preferences.getString(PREF_KEY_ALIAS, ""));
        binding.etSigningAlgorithm.setText(preferences.getString(PREF_SIGNING_ALGORITHM, DEFAULT_SIGNING_ALGORITHM));

        String savedMode = preferences.getString(PREF_SIGNING_MODE,
                new File(defaultPath).isFile() ? SigningMode.OWN_KEY_STORE.name() : SigningMode.TESTKEY.name());
        try {
            mode = SigningMode.valueOf(savedMode);
        } catch (Exception ignored) {
            mode = new File(defaultPath).isFile() ? SigningMode.OWN_KEY_STORE : SigningMode.TESTKEY;
        }
        binding.actSigningMode.setText(mode.label, false);
        updateInputFieldsState();
    }

    private String[] getDropdownItems() {
        LinkedList<String> labels = new LinkedList<>();
        for (SigningMode mode : SigningMode.values()) {
            labels.add(mode.label);
        }
        return labels.toArray(new String[0]);
    }

    private void updateInputFieldsState() {
        boolean signingWithKeyStore = mode == SigningMode.OWN_KEY_STORE;
        boolean signingEnabled = mode != SigningMode.DONT_SIGN;

        binding.tilKeystorePath.setEnabled(signingWithKeyStore);
        binding.tilKeystorePassword.setEnabled(signingWithKeyStore);
        binding.tilAlias.setEnabled(signingWithKeyStore);
        binding.tilKeyPassword.setEnabled(signingWithKeyStore);
        binding.tilSigningAlgorithm.setEnabled(signingEnabled);
    }

    private void onNextButtonClick(DialogInterface dialogInterface) {
        if (mode == SigningMode.OWN_KEY_STORE) {
            if (!validateInputs()) {
                return;
            }
            saveNonSecretPreferences();
            dialogInterface.dismiss();
            receiver.gotCredentials(new Credentials(
                    Helper.getText(binding.etKeystorePath),
                    Helper.getText(binding.etSigningAlgorithm),
                    Helper.getText(binding.etKeystorePassword),
                    Helper.getText(binding.etAlias),
                    Helper.getText(binding.etKeyPassword)
            ));
            return;
        }

        if (mode == SigningMode.TESTKEY) {
            preferences.edit().putString(PREF_SIGNING_MODE, mode.name()).apply();
            dialogInterface.dismiss();
            receiver.gotCredentials(Credentials.forTestkey());
            return;
        }

        preferences.edit().putString(PREF_SIGNING_MODE, mode.name()).apply();
        dialogInterface.dismiss();
        receiver.gotCredentials(null);
    }

    private void saveNonSecretPreferences() {
        preferences.edit()
                .putString(PREF_SIGNING_MODE, mode.name())
                .putString(PREF_KEYSTORE_PATH, Helper.getText(binding.etKeystorePath))
                .putString(PREF_KEY_ALIAS, Helper.getText(binding.etAlias))
                .putString(PREF_SIGNING_ALGORITHM, Helper.getText(binding.etSigningAlgorithm))
                .apply();
    }

    private boolean validateInputs() {
        boolean isValid = true;

        String keystorePath = Helper.getText(binding.etKeystorePath).trim();
        if (TextUtils.isEmpty(keystorePath)) {
            binding.tilKeystorePath.setError("Keystore path can't be empty");
            isValid = false;
        } else {
            File keystoreFile = new File(keystorePath);
            if (!keystoreFile.isFile()) {
                binding.tilKeystorePath.setError("Keystore file was not found");
                isValid = false;
            } else {
                binding.tilKeystorePath.setError(null);
            }
        }

        if (TextUtils.isEmpty(binding.etKeystorePassword.getText())) {
            binding.tilKeystorePassword.setError("Keystore password can't be empty");
            isValid = false;
        } else {
            binding.tilKeystorePassword.setError(null);
        }

        if (TextUtils.isEmpty(binding.etAlias.getText())) {
            binding.tilAlias.setError("Alias can't be empty");
            isValid = false;
        } else {
            binding.tilAlias.setError(null);
        }

        if (TextUtils.isEmpty(binding.etKeyPassword.getText())) {
            binding.tilKeyPassword.setError("Key password can't be empty");
            isValid = false;
        } else {
            binding.tilKeyPassword.setError(null);
        }

        if (TextUtils.isEmpty(binding.etSigningAlgorithm.getText())) {
            binding.tilSigningAlgorithm.setError("Algorithm can't be empty");
            isValid = false;
        } else {
            binding.tilSigningAlgorithm.setError(null);
        }

        if (!isValid) {
            SketchwareUtil.toastError("Please fix the signing inputs and try again.");
        }
        return isValid;
    }

    public void show() {
        binding.etAlias.requestFocus();
        dialog.show();
    }

    public void setListener(CredentialsReceiver receiver) {
        this.receiver = receiver;
    }

    private enum SigningMode {
        OWN_KEY_STORE("Sign using keystore"),
        TESTKEY("Sign using a test key"),
        DONT_SIGN("Don't sign");

        private final String label;

        SigningMode(String label) {
            this.label = label;
        }
    }

    public interface CredentialsReceiver {
        void gotCredentials(Credentials credentials);
    }

    public static class Credentials {

        private final boolean signWithTestkey;
        private final String keyStorePath;
        private final String keyStorePassword;
        private final String keyAlias;
        private final String keyPassword;
        private final String signingAlgorithm;

        private Credentials(boolean signWithTestkey, String keyStorePath, String signingAlgorithm,
                            String keyStorePassword, String keyAlias, String keyPassword) {
            this.signWithTestkey = signWithTestkey;
            this.keyStorePath = keyStorePath;
            this.keyStorePassword = keyStorePassword;
            this.keyAlias = keyAlias;
            this.keyPassword = keyPassword;
            this.signingAlgorithm = signingAlgorithm;
        }

        public static Credentials forTestkey() {
            return new Credentials(true, null, null, null, null, null);
        }

        public Credentials(String keyStorePath, String signingAlgorithm, String keyStorePassword,
                           String keyAlias, String keyPassword) {
            this(false, keyStorePath, signingAlgorithm, keyStorePassword, keyAlias, keyPassword);
        }

        public boolean isForSigningWithTestkey() {
            return signWithTestkey;
        }

        public String getKeyStorePath() {
            return keyStorePath;
        }

        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        public String getKeyAlias() {
            return keyAlias;
        }

        public String getKeyPassword() {
            return keyPassword;
        }

        public String getSigningAlgorithm() {
            return signingAlgorithm;
        }
    }
}
