package tritium.music.client.rendering.hud.dance;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

interface DanceFrameSource {

    InputStream open(String name) throws IOException;

    List<String> frames();

    static DanceFrameSource directory(File directory) {
        return new DanceFrameSource() {

            @Override
            public InputStream open(String name) throws IOException {
                return Files.newInputStream(new File(directory, name).toPath());
            }

            @Override
            public List<String> frames() {
                File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
                if (files == null) {
                    return List.of();
                }
                List<String> names = new ArrayList<>(files.length);
                for (File file : files) {
                    names.add(file.getName());
                }
                names.sort(String.CASE_INSENSITIVE_ORDER);
                return names;
            }
        };
    }

    static DanceFrameSource resource(String basePath) {
        String prefix = "/" + basePath + "/";
        return new DanceFrameSource() {

            @Override
            public InputStream open(String name) {
                return DanceFrameSource.class.getResourceAsStream(prefix + name);
            }

            @Override
            public List<String> frames() {
                return List.of();
            }
        };
    }
}
