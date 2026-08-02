package tritium.music.core.util;

import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.util.Map;

public class HttpUtils {

    @Getter
    @Setter
    private static int retryTimes = 10;

    public static InputStream get(String url, Map<String, String> headers) throws IOException {
        URLConnection urlConnection = URI.create(url).toURL().openConnection();
        HttpURLConnection conn = (HttpURLConnection) urlConnection;
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Charset", "UTF-8");
        conn.setReadTimeout(8000);
        conn.setConnectTimeout(5000);

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        if (conn.getResponseCode() >= 300) {
            throw new IOException("HTTP request failed, response code is " + conn.getResponseCode());
        }

        int contentLength = conn.getContentLength();
        InputStream inputStream = conn.getInputStream();

        return new InputStream() {
            @Override
            public int read() throws IOException {
                return inputStream.read();
            }

            @Override
            public int available() {
                return contentLength;
            }

            @Override
            public void close() throws IOException {
                inputStream.close();
            }
        };
    }

    public static InputStream downloadStream(String path) {
        return downloadStream(path, 0);
    }

    public static InputStream downloadStream(String path, int retry) {
        try {
            URLConnection urlConnection = URI.create(path).toURL().openConnection();
            HttpURLConnection conn = (HttpURLConnection) urlConnection;
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Charset", "UTF-8");
            conn.setReadTimeout(10 * 1000);
            conn.connect();

            return conn.getInputStream();
        } catch (Exception err) {
            if (retry >= retryTimes) {
                throw new RuntimeException("Max retry time reached for url " + path);
            }
            return downloadStream(path, ++retry);
        }
    }
}
