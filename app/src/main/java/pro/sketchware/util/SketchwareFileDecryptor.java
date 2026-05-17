package pro.sketchware.util;

import java.io.File;
import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Reads project files while respecting the active project's scope.
 */
public class SketchwareFileDecryptor {
    private static final String ENCRYPTION_KEY = "sketchwaresecure";
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    public static String decryptFile(String scId, String filePath) {
        try {
            ProjectPathResolver.ResolvedPath resolvedPath = resolvePath(scId, filePath);
            if (resolvedPath == null) {
                return null;
            }

            File file = resolvedPath.getFile();
            if (!file.exists() || file.isDirectory()) {
                return null;
            }

            boolean formatKnownAsPlain = isPlainTextFile(file);
            byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());

            try {
                String possibleText = new String(fileBytes, StandardCharsets.UTF_8);
                String trimmed = possibleText.trim();

                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    return new org.json.JSONObject(trimmed).toString(4);
                } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    return new org.json.JSONArray(trimmed).toString(4);
                }

                if (formatKnownAsPlain || trimmed.startsWith("<?xml") || trimmed.startsWith("<")) {
                    return possibleText;
                }
            } catch (Exception ignored) {
            }

            try {
                Cipher cipher = Cipher.getInstance(ALGORITHM);
                byte[] key = ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8);
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));

                byte[] decrypted = cipher.doFinal(fileBytes);
                String decryptedString = new String(decrypted, StandardCharsets.UTF_8);
                String trimmed = decryptedString.trim();

                try {
                    if (trimmed.startsWith("{")) {
                        return new org.json.JSONObject(trimmed).toString(4);
                    } else if (trimmed.startsWith("[")) {
                        return new org.json.JSONArray(trimmed).toString(4);
                    }
                } catch (Exception ignored) {
                }

                return decryptedString;
            } catch (Exception cryptoEx) {
                if (!formatKnownAsPlain) {
                    return new String(fileBytes, StandardCharsets.UTF_8);
                }
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean fileExists(String scId, String filePath) {
        ProjectPathResolver.ResolvedPath resolvedPath = resolvePath(scId, filePath);
        return resolvedPath != null && resolvedPath.getFile().exists();
    }

    private static ProjectPathResolver.ResolvedPath resolvePath(String scId, String filePath) {
        String normalizedPath = filePath;
        if (normalizedPath.endsWith(".json") || normalizedPath.endsWith(".xml")) {
            int extensionIndex = normalizedPath.lastIndexOf(".");
            if (extensionIndex > 0) {
                String pathWithoutExtension = normalizedPath.substring(0, extensionIndex);
                ProjectPathResolver.ResolvedPath withoutExtension = ProjectPathResolver.resolveForRead(scId, pathWithoutExtension);
                if (withoutExtension != null && withoutExtension.getFile().exists()) {
                    return withoutExtension;
                }
            }
        }
        return ProjectPathResolver.resolveForRead(scId, normalizedPath);
    }

    private static boolean isPlainTextFile(File file) {
        String fileName = file.getName();
        if (!fileName.contains(".")) {
            return false;
        }

        String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return ext.equals("xml")
                || ext.equals("json")
                || ext.equals("txt")
                || ext.equals("java")
                || ext.equals("kt")
                || ext.equals("gradle")
                || ext.equals("properties")
                || ext.equals("pro")
                || ext.equals("html")
                || ext.equals("md");
    }
}
