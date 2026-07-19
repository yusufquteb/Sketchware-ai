package pro.sketchware.utility.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class HashUtil {
    private HashUtil() {}

    public static String sha1(String input) { return hash(input, "SHA-1"); }
    public static String sha256(String input) { return hash(input, "SHA-256"); }
    public static String md5(String input) { return hash(input, "MD5"); }

    public static String hash(String input, String algorithm) {
        if (input == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
