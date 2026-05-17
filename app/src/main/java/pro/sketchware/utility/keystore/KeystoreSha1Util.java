package pro.sketchware.utility.keystore;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Enumeration;

public final class KeystoreSha1Util {
    private KeystoreSha1Util() {}

    public static String sha1(File keystoreFile, String storePassword, String alias) throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream in = new FileInputStream(keystoreFile)) {
            store.load(in, (storePassword == null ? "" : storePassword).toCharArray());
        }
        String actualAlias = alias;
        if (actualAlias == null || actualAlias.isEmpty()) {
            Enumeration<String> aliases = store.aliases();
            if (aliases.hasMoreElements()) actualAlias = aliases.nextElement();
        }
        Certificate certificate = store.getCertificate(actualAlias);
        if (certificate == null) return "";
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(certificate.getEncoded());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", digest[i]));
        }
        return sb.toString();
    }
}
