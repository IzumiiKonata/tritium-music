package tritium.music.core.ncm;

import lombok.experimental.UtilityClass;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class OptionsUtil {

    private final Map<String, String> COOKIES = new LinkedHashMap<>();
    private final Set<String> COOKIE_ATTRIBUTES = Set.of(
            "domain", "path", "expires", "max-age", "secure", "httponly", "samesite", "priority", "partitioned"
    );

    public synchronized void setCookie(String cookie) {
        COOKIES.clear();
        mergeCookieHeader(cookie);
    }

    public synchronized String getCookie() {
        return COOKIES.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    public synchronized boolean hasAuthentication() {
        return COOKIES.containsKey("MUSIC_U") || COOKIES.containsKey("MUSIC_A");
    }

    public synchronized void mergeCookies(Map<String, String> cookies) {
        cookies.forEach(OptionsUtil::putCookie);
    }

    public synchronized void mergeSetCookieHeaders(String[] headers) {
        if (headers == null) return;
        for (String header : headers) {
            if (header == null || header.isBlank()) continue;
            putCookiePair(header.split(";", 2)[0]);
        }
    }

    public synchronized void clearAuthentication() {
        COOKIES.remove("MUSIC_U");
        COOKIES.remove("MUSIC_A");
        COOKIES.remove("__csrf");
    }

    private void mergeCookieHeader(String header) {
        if (header == null || header.isBlank()) return;
        for (String pair : header.split(";")) {
            putCookiePair(pair);
        }
    }

    private void putCookiePair(String pair) {
        String[] parts = pair.trim().split("=", 2);
        if (parts.length != 2) return;
        putCookie(parts[0].trim(), parts[1].trim());
    }

    private void putCookie(String name, String value) {
        if (name.isEmpty() || COOKIE_ATTRIBUTES.contains(name.toLowerCase(Locale.ROOT))) return;
        if (value.isEmpty()) {
            COOKIES.remove(name);
        } else {
            COOKIES.put(name, value);
        }
    }

    public RequestUtil.RequestOptions createOptions() {
        return createOptions("");
    }

    public RequestUtil.RequestOptions createOptions(String crypto) {
        return createOptions(crypto, null);
    }

    public RequestUtil.RequestOptions createOptions(String crypto, Map<String, String> headers) {
        return RequestUtil.RequestOptions.builder()
                .headers(headers)
                .crypto(crypto)
                .cookie(getCookie())
                .ua("")
                .proxy("")
                .encryptedResponse(null)
                .build();
    }
}
