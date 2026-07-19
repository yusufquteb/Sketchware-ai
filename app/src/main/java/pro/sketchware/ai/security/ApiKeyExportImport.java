package pro.sketchware.ai.security;

import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.storage.AiPreferences;

/**
 * Password-protected export/import for AI provider API keys.
 *
 * Audit report §7 root cause: SecureKeyStore encrypts every key with an
 * AES-256-GCM key that lives exclusively in the Android Keystore, tied to
 * this app's specific install (package + signing key). That's the strongest
 * design available on Android, but it also means the encryption key —
 * and therefore every stored API key — is deleted by the OS the moment the
 * app is uninstalled. There is no way to export anything protected by the
 * Keystore itself; its whole point is that the key material never leaves it.
 *
 * This class solves the *portability* problem with a deliberately separate,
 * user-password-based encryption scheme (AES-256-GCM with a PBKDF2-derived
 * key), independent of the Keystore, so the resulting file can be decrypted
 * on any device/install as long as the user remembers the password:
 *
 *   Export: read all provider keys from SecureKeyStore (via AiPreferences,
 *   which already decrypts them) → build a JSON object → encrypt that JSON
 *   with a key derived from a user-chosen password (PBKDF2WithHmacSHA256,
 *   210,000 iterations, random 16-byte salt) → AES/GCM encrypt → store
 *   [salt][iv][ciphertext] as Base64 inside a small JSON envelope written to
 *   a ".skwai-keys" file (the caller is expected to let the user pick the
 *   save location via Storage Access Framework, e.g. ACTION_CREATE_DOCUMENT).
 *
 *   Import: reverse the process — read the file, ask for the password,
 *   derive the same key, decrypt, parse the JSON, and write each key back
 *   through AiPreferences.setApiKey() (which stores it via SecureKeyStore
 *   as usual).
 *
 * Security note (surfaced to the user in AiSettingsActivity, not just here):
 * the exported file is only as strong as the chosen password — anyone who
 * has both the file and the password can read every key inside it in
 * plaintext. This class does not attempt to enforce a "strong password"
 * policy; that's a UX decision left to the caller.
 */
public final class ApiKeyExportImport {

    private ApiKeyExportImport() {}

    /** File extension recommended for exported key backups. */
    public static final String FILE_EXTENSION = ".skwai-keys";
    public static final String MIME_TYPE = "application/octet-stream";

    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final String KEY_DERIVATION_ALGO = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int AES_KEY_LENGTH_BITS = 256;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int SALT_LENGTH_BYTES = 16;

    /** Current on-disk envelope format version, for forward compatibility. */
    private static final int FORMAT_VERSION = 1;

    private static final String ENVELOPE_KEY_VERSION = "version";
    private static final String ENVELOPE_KEY_SALT = "salt";
    private static final String ENVELOPE_KEY_IV = "iv";
    private static final String ENVELOPE_KEY_CIPHERTEXT = "ciphertext";

    /**
     * Result of a successful export: the bytes to write to the file the user
     * picked, plus how many keys were included (for a confirmation message).
     */
    public static final class ExportResult {
        @NonNull public final byte[] fileContents;
        public final int keyCount;

        private ExportResult(@NonNull byte[] fileContents, int keyCount) {
            this.fileContents = fileContents;
            this.keyCount = keyCount;
        }
    }

    /** Thrown for any export/import failure, with a message safe to show the user. */
    public static final class ExportImportException extends Exception {
        public ExportImportException(String message) {
            super(message);
        }

        public ExportImportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Collects every currently-stored provider API key and encrypts them into
     * a single file's contents, protected by {@code password}.
     *
     * @throws ExportImportException if there are no keys to export, or if
     *                                encryption fails.
     */
    @NonNull
    public static ExportResult exportKeys(@NonNull Context context, @NonNull char[] password)
            throws ExportImportException {
        if (password.length == 0) {
            throw new ExportImportException("Choose a password to protect the exported keys.");
        }

        AiPreferences preferences = AiPreferences.getInstance(context);
        JSONObject keysJson = new JSONObject();
        int keyCount = 0;
        try {
            for (AiProvider provider : AiProvider.values()) {
                String key = preferences.getApiKey(provider);
                if (key != null && !key.isEmpty()) {
                    keysJson.put(provider.name(), key);
                    keyCount++;
                }
            }
        } catch (JSONException e) {
            throw new ExportImportException("Failed to prepare keys for export.", e);
        }

        if (keyCount == 0) {
            throw new ExportImportException("No API keys are currently saved — nothing to export.");
        }

        try {
            byte[] salt = randomBytes(SALT_LENGTH_BYTES);
            SecretKey secretKey = deriveKey(password, salt);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();
            byte[] plaintext = keysJson.toString().getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plaintext);

            JSONObject envelope = new JSONObject();
            envelope.put(ENVELOPE_KEY_VERSION, FORMAT_VERSION);
            envelope.put(ENVELOPE_KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP));
            envelope.put(ENVELOPE_KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP));
            envelope.put(ENVELOPE_KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP));

            byte[] fileContents = envelope.toString(2).getBytes(StandardCharsets.UTF_8);
            return new ExportResult(fileContents, keyCount);
        } catch (GeneralSecurityException | JSONException e) {
            throw new ExportImportException("Failed to encrypt the exported keys.", e);
        } finally {
            // Best-effort scrub of the password from memory once we're done with it.
            java.util.Arrays.fill(password, '\0');
        }
    }

    /** Result of a successful import: which providers were restored. */
    public static final class ImportResult {
        @NonNull public final List<AiProvider> importedProviders;

        private ImportResult(@NonNull List<AiProvider> importedProviders) {
            this.importedProviders = importedProviders;
        }
    }

    /**
     * Decrypts a previously-exported file's contents with {@code password} and
     * writes every key found inside back into SecureKeyStore (via
     * AiPreferences), overwriting any existing key for the same provider.
     *
     * @throws ExportImportException if the file is malformed, the password is
     *                                wrong (authentication failure — AES/GCM
     *                                detects this), or decryption otherwise fails.
     */
    @NonNull
    public static ImportResult importKeys(@NonNull Context context, @NonNull byte[] fileContents,
                                           @NonNull char[] password) throws ExportImportException {
        if (password.length == 0) {
            throw new ExportImportException("Enter the password used to export these keys.");
        }

        JSONObject envelope;
        try {
            envelope = new JSONObject(new String(fileContents, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new ExportImportException("This doesn't look like a valid Sketchware-ai key backup file.", e);
        }

        int version = envelope.optInt(ENVELOPE_KEY_VERSION, -1);
        if (version != FORMAT_VERSION) {
            throw new ExportImportException("This key backup file was created by an incompatible version of Sketchware-ai.");
        }

        byte[] salt;
        byte[] iv;
        byte[] ciphertext;
        try {
            salt = Base64.decode(envelope.getString(ENVELOPE_KEY_SALT), Base64.NO_WRAP);
            iv = Base64.decode(envelope.getString(ENVELOPE_KEY_IV), Base64.NO_WRAP);
            ciphertext = Base64.decode(envelope.getString(ENVELOPE_KEY_CIPHERTEXT), Base64.NO_WRAP);
        } catch (JSONException | IllegalArgumentException e) {
            throw new ExportImportException("This key backup file is corrupted or incomplete.", e);
        }

        String decryptedJson;
        try {
            SecretKey secretKey = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            decryptedJson = new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // AES/GCM authentication failure is the expected outcome for a wrong
            // password (or a tampered/corrupted file) — surface a clear message
            // instead of a raw crypto exception.
            throw new ExportImportException("Incorrect password, or this file is corrupted.", e);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }

        JSONObject keysJson;
        try {
            keysJson = new JSONObject(decryptedJson);
        } catch (JSONException e) {
            throw new ExportImportException("This key backup file is corrupted or incomplete.", e);
        }

        AiPreferences preferences = AiPreferences.getInstance(context);
        List<AiProvider> imported = new ArrayList<>();
        Iterator<String> keys = keysJson.keys();
        while (keys.hasNext()) {
            String providerName = keys.next();
            AiProvider provider = resolveProvider(providerName);
            if (provider == null) {
                continue; // unknown/removed provider name — skip rather than fail the whole import
            }
            String value = keysJson.optString(providerName, null);
            if (value != null && !value.isEmpty()) {
                preferences.setApiKey(provider, value);
                imported.add(provider);
            }
        }

        if (imported.isEmpty()) {
            throw new ExportImportException("The backup file was decrypted successfully, but contained no recognizable API keys.");
        }

        return new ImportResult(imported);
    }

    @Nullable
    private static AiProvider resolveProvider(@NonNull String name) {
        for (AiProvider provider : AiProvider.values()) {
            if (provider.name().equals(name)) {
                return provider;
            }
        }
        return null;
    }

    @NonNull
    private static SecretKey deriveKey(@NonNull char[] password, @NonNull byte[] salt)
            throws GeneralSecurityException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGO);
        KeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, AES_KEY_LENGTH_BITS);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    @NonNull
    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
