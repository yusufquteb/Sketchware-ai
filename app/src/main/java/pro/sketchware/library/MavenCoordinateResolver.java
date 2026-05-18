package pro.sketchware.library;

public final class MavenCoordinateResolver {
    private MavenCoordinateResolver() {}

    public static boolean isCoordinate(String value) {
        return value != null && value.matches("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+\\-]+.*");
    }

    public static String artifactName(String coordinate) {
        if (!isCoordinate(coordinate)) return "";
        String[] parts = coordinate.split(":");
        return parts[1] + "-" + parts[2].replaceAll("[@:].*$", "");
    }
}
