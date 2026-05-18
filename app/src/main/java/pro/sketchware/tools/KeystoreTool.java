package pro.sketchware.tools;

import java.io.File;

import pro.sketchware.utility.keystore.KeystoreSha1Util;

public final class KeystoreTool {
    private KeystoreTool() {}
    public static String sha1(File file, String password, String alias) throws Exception { return KeystoreSha1Util.sha1(file, password, alias); }
}
