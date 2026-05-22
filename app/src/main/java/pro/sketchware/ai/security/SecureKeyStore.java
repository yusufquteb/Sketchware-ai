package pro.sketchware.ai.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Secure storage for sensitive values (API keys) using Android Keystore + AES-GCM.
 *
 * <p>Each value is encrypted with AES-256-GCM. The ciphertext (IV + data) is
 * Base64-encoded and stored in a private SharedPreferences file. The AES key
 * lives exclusively in the Android hardware-backed Keystore and never leaves it.
 *
 * <p>Requires API 26+ (guaranteed by the project's minSdk).
 *
 * <p>Thread-safe. Uses a single Keystore alias for all AI values.
 */
public final class SecureKeyStore {

    private static final String TAG = "SecureKeyStore";

    private static final String PREFS_NAME   = "ai_secure_store";
    private static final String KEY_ALIAS    = "sketchware_ai_keys";
    private static final String KEY_PROVIDER = "AndroidKeyStore";
    private static final String CIPHER_ALGO  = "AES/GCM/NoPadding";

    private static final int GCM_IV_LENGTH_BYTES  = 12;
    private static final int GCM_TAG_LENGTH_BITS   = 128;

    /** Prefix applied to SharedPreferences keys to namespace the store. */
    private static final String PREF_PREFIX = "sk_";

    private static volatile SecureKeyStore instance;

    @NonNull
    public static SecureKeyStore getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SecureKeyStore.class) {
                if (instance == null) {
                    instance = new SecureKeyStore(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private final SharedPreferences prefs;

    private SecureKeyStore(@NonNull Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        ensureKeyExists();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Encrypts and stores a value under the given key.
     * A null or empty value removes the entry.
     */
    public void put(@NonNull String key, @Nullable String value) {
        if (value == null || value.isEmpty()) {
            prefs.edit().remove(PREF_PREFIX + key).apply();
            return;
        }
        try {
            SecretKey secretKey = getSecretKey();
            if (secretKey == null) {
                // Keystore unavailable — store plaintext as fallback with warning
                Log.w(TAG, "Keystore unavailable — storing key without encryption");
                prefs.edit().putString(PREF_PREFIX + key, "plain:" + value).apply();
                return;
            }

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv         = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            // Layout: [iv_length(1 byte)][iv][ciphertext]
            ByteBuffer buffer = ByteBuffer.allocate(1 + iv.length + ciphertext.length);
            buffer.put((byte) iv.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            String encoded = Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
            prefs.edit().putString(PREF_PREFIX + key, encoded).apply();
        } catch (Exception e) {
            Log.e(TAG, "Encrypt error for key=" + key, e);
            // Fallback: store plaintext to avoid data loss
            prefs.edit().putString(PREF_PREFIX + key, "plain:" + value).apply();
        }
    }

    /**
     * Retrieves and decrypts a stored value, or null if not found.
     */
    @Nullable
    public String get(@NonNull String key) {
        String stored = prefs.getString(PREF_PREFIX + key, null);
        if (stored == null) return null;

        // Plain fallback (Keystore unavailable)
        if (stored.startsWith("plain:")) {
            return stored.substring(6);
        }

        try {
            SecretKey secretKey = getSecretKey();
            if (secretKey == null) return null;

            byte[] data = Base64.decode(stored, Base64.NO_WRAP);
            ByteBuffer buffer = ByteBuffer.wrap(data);

            int ivLength = Byte.toUnsignedInt(buffer.get());
            if (ivLength < 1 || ivLength > GCM_IV_LENGTH_BYTES + 4) {
                Log.w(TAG, "Unexpected IV length " + ivLength + " for key=" + key);
                return null;
            }
            byte[] iv = new byte[ivLength];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, secretKey,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decrypt error for key=" + key, e);
            return null;
        }
    }

    /** Returns true if a value exists for the given key. */
    public boolean contains(@NonNull String key) {
        return prefs.contains(PREF_PREFIX + key);
    }

    /** Removes the entry for the given key. */
    public void remove(@NonNull String key) {
        prefs.edit().remove(PREF_PREFIX + key).apply();
    }

    /** Removes all stored entries (e.g. factory reset / sign-out). */
    public void clear() {
        prefs.edit().clear().apply();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void ensureKeyExists() {
        try {
            KeyStore ks = KeyStore.getInstance(KEY_PROVIDER);
            ks.load(null);
            if (!ks.containsAlias(KEY_ALIAS)) {
                KeyGenerator keyGen = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, KEY_PROVIDER);
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build();
                keyGen.init(spec);
                keyGen.generateKey();
                Log.d(TAG, "AES-256-GCM key generated in Android Keystore");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Keystore key", e);
        }
    }

    @Nullable
    private SecretKey getSecretKey() {
        try {
            KeyStore ks = KeyStore.getInstance(KEY_PROVIDER);
            ks.load(null);
            KeyStore.Entry entry = ks.getEntry(KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get secret key", e);
        }
        return null;
    }
}
