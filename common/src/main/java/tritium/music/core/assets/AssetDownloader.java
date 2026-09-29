package tritium.music.core.assets;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class AssetDownloader {

    private static final int CONNECT_TIMEOUT_MILLIS = 8_000;
    private static final int READ_TIMEOUT_MILLIS = 20_000;
    private static final int PROBE_TIMEOUT_MILLIS = 5_000;
    private static final int BUFFER_SIZE = 1 << 16;
    private static final String USER_AGENT = "tritium-music-asset-loader";

    interface Progress {
        void accept(int chunk, long total);
    }

    private AssetDownloader() {
    }

    static boolean reachable(AssetRoute route, RemoteAsset asset) {
        HttpURLConnection connection = null;
        try {
            connection = open(route.url(asset), PROBE_TIMEOUT_MILLIS);
            connection.setRequestMethod("HEAD");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }
            long length = connection.getContentLengthLong();
            return length <= 0 || asset.size() <= 0 || length == asset.size();
        } catch (Throwable throwable) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static void download(AssetRoute route, RemoteAsset asset, Path target, Progress progress) throws IOException {
        HttpURLConnection connection = open(route.url(asset), READ_TIMEOUT_MILLIS);
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " from " + route.displayName());
            }
            long total = connection.getContentLengthLong();
            if (total <= 0) {
                total = asset.size();
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream(), BUFFER_SIZE);
                 OutputStream output = new BufferedOutputStream(Files.newOutputStream(target,
                         StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING),
                         BUFFER_SIZE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                    progress.accept(read, total);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url, int readTimeout) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "*/*");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(readTimeout);
        return connection;
    }
}
