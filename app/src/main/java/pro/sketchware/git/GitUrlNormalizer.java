package pro.sketchware.git;

public final class GitUrlNormalizer {
    private GitUrlNormalizer() {}

    public static String normalize(String url) {
        if (url == null) return "";
        String value = url.trim();
        if (value.startsWith("git@")) value = value.replaceFirst("git@", "https://").replaceFirst(":", "/");
        if (value.startsWith("ssh://git@")) value = value.replaceFirst("ssh://git@", "https://");
        if (value.startsWith("ssh://")) value = value.replaceFirst("ssh://", "https://");
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if ((value.contains("github.com") || value.contains("gitlab.com")) && !value.endsWith(".git")) value += ".git";
        return value;
    }
}
