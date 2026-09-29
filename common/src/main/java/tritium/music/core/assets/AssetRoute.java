package tritium.music.core.assets;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record AssetRoute(String host, String prefix, boolean mirror) {

    public static final String DEFAULT_REPOSITORY = "IzumiiKonata/tritium-music";
    public static final String DEFAULT_REF = "main";

    private static final List<String> DEFAULT_MIRRORS = List.of(
            "https://gh-proxy.com/",
            "https://ghfast.top/",
            "https://ghproxy.net/");

    private static final String REPOSITORY_PROPERTY = "tritium.assets.repository";
    private static final String REF_PROPERTY = "tritium.assets.ref";
    private static final String MIRRORS_PROPERTY = "tritium.assets.mirrors";

    public String url(RemoteAsset asset) {
        return prefix + asset.path();
    }

    public String displayName() {
        return mirror ? host + " (gh-proxy)" : "GitHub";
    }

    public static List<AssetRoute> candidates() {
        String repository = property(REPOSITORY_PROPERTY, DEFAULT_REPOSITORY);
        String ref = property(REF_PROPERTY, DEFAULT_REF);

        List<AssetRoute> routes = new ArrayList<>();
        routes.add(new AssetRoute("GitHub", rawBase(repository, ref), false));
        routes.add(new AssetRoute("GitHub", githubBase(repository, ref), false));
        for (String mirror : mirrors()) {
            String host = hostOf(mirror);
            routes.add(new AssetRoute(host, mirror + rawBase(repository, ref), true));
            routes.add(new AssetRoute(host, mirror + githubBase(repository, ref), true));
        }
        return List.copyOf(routes);
    }

    private static String rawBase(String repository, String ref) {
        return "https://raw.githubusercontent.com/" + repository + "/" + ref + "/";
    }

    private static String githubBase(String repository, String ref) {
        return "https://github.com/" + repository + "/raw/" + ref + "/";
    }

    private static List<String> mirrors() {
        String override = System.getProperty(MIRRORS_PROPERTY, "").trim();
        if (override.isEmpty()) {
            return DEFAULT_MIRRORS;
        }
        List<String> mirrors = new ArrayList<>();
        for (String entry : override.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty() || "none".equalsIgnoreCase(trimmed)) {
                continue;
            }
            mirrors.add(trimmed.endsWith("/") ? trimmed : trimmed + "/");
        }
        return List.copyOf(mirrors);
    }

    private static String property(String key, String fallback) {
        String value = System.getProperty(key, "").trim();
        return value.isEmpty() ? fallback : value;
    }

    private static String hostOf(String url) {
        int start = url.indexOf("://");
        start = start < 0 ? 0 : start + 3;
        int end = url.indexOf('/', start);
        String host = end < 0 ? url.substring(start) : url.substring(start, end);
        return host.toLowerCase(Locale.ROOT);
    }
}
