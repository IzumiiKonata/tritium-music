package tritium.music.client.rendering.hud.dance;

import net.minecraft.client.Minecraft;
import tritium.music.client.config.WidgetConfig;
import tritium.music.core.util.JsonUtils;
import tritium.music.platform.Platform;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DanceStyleRegistry {

    public static final String DIRECTORY_PROPERTY = "tritium.dance.dir";
    private static final String CONFIG_FILE = "dance.json";
    private static final int MAXIMUM_DEPTH = 3;
    private static final int GAME_DIR_PARENTS = 4;

    private static final Map<String, DanceStyle> STYLES = new LinkedHashMap<>();
    private static volatile boolean scanned;
    private static volatile DanceStyle active;
    private static boolean readmeWritten;

    private DanceStyleRegistry() {
    }

    public record StyleInfo(String id, String name) {
    }

    public static synchronized void refresh() {
        Map<String, DanceStyle> discovered = new LinkedHashMap<>();

        for (File root : roots()) {
            if (!root.isDirectory()) {
                continue;
            }
            collect(root, root, discovered, 0);
        }

        for (Map.Entry<String, DanceStyle> entry : STYLES.entrySet()) {
            if (!discovered.containsKey(entry.getKey())) {
                entry.getValue().release();
            }
        }

        STYLES.clear();
        STYLES.putAll(discovered);
        if (active != null && !STYLES.containsValue(active)) {
            active = null;
        }
        scanned = true;
    }

    public static synchronized List<StyleInfo> styles() {
        ensureScanned();
        List<StyleInfo> infos = new ArrayList<>(STYLES.size());
        for (DanceStyle style : STYLES.values()) {
            infos.add(new StyleInfo(style.getId(), style.displayName()));
        }
        return infos;
    }

    public static synchronized DanceStyle byId(String id) {
        ensureScanned();
        return id == null ? null : STYLES.get(id);
    }

    public static synchronized DanceStyle active() {
        ensureScanned();
        String wanted = WidgetConfig.get().dance.style;
        DanceStyle style = wanted == null || wanted.isBlank() ? null : STYLES.get(wanted);
        if (style == null && !STYLES.isEmpty()) {
            style = STYLES.values().iterator().next();
        }
        if (style != active) {
            if (active != null) {
                active.release();
            }
            active = style;
        }
        if (style != null) {
            style.requestLoad();
        }
        return style;
    }

    public static synchronized boolean isEmpty() {
        ensureScanned();
        return STYLES.isEmpty();
    }

    public static File directory() {
        File directory = new File(Platform.configDir(), "dance");
        if (!directory.isDirectory()) {
            directory.mkdirs();
        }
        return directory;
    }

    private static void ensureScanned() {
        if (!scanned) {
            refresh();
        }
    }

    private static void collect(File root, File directory, Map<String, DanceStyle> discovered, int depth) {
        File config = new File(directory, CONFIG_FILE);
        if (config.isFile()) {
            String id = relativeId(root, directory);
            if (id.isEmpty() || discovered.containsKey(id)) {
                return;
            }
            DanceStyle style = parse(id, directory, config);
            if (style != null) {
                discovered.put(id, style);
            }
            return;
        }

        if (depth >= MAXIMUM_DEPTH) {
            return;
        }

        File[] children = directory.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        List<File> sorted = new ArrayList<>(List.of(children));
        sorted.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File child : sorted) {
            collect(root, child, discovered, depth + 1);
        }
    }

    private static DanceStyle parse(String id, File directory, File config) {
        try {
            String json = Files.readString(config.toPath(), StandardCharsets.UTF_8);
            DanceStyleSpec spec = JsonUtils.parse(json, DanceStyleSpec.class);
            if (spec == null) {
                Platform.log("[NCM] Dance style " + id + " has an empty configuration");
                return null;
            }
            return new DanceStyle(id, directory, spec);
        } catch (Throwable throwable) {
            Platform.log("[NCM] Failed to read dance style " + id + ": " + throwable);
            return null;
        }
    }

    private static String relativeId(File root, File directory) {
        String rootPath = root.getAbsolutePath();
        String directoryPath = directory.getAbsolutePath();
        if (directoryPath.equals(rootPath)) {
            return "";
        }
        if (!directoryPath.startsWith(rootPath)) {
            return "";
        }
        return directoryPath.substring(rootPath.length() + 1).replace(File.separatorChar, '/');
    }

    private static List<File> roots() {
        List<File> roots = new ArrayList<>();
        String override = System.getProperty(DIRECTORY_PROPERTY, "").trim();
        if (!override.isEmpty()) {
            roots.add(new File(override));
        }
        roots.add(new File(Platform.configDir(), "dance"));

        File gameDirectory = Minecraft.getInstance().gameDirectory;
        if (gameDirectory != null) {
            File current = gameDirectory.getAbsoluteFile();
            for (int depth = 0; depth < GAME_DIR_PARENTS && current != null; depth++) {
                File candidate = new File(current, "dance");
                if (!roots.contains(candidate)) {
                    roots.add(candidate);
                }
                current = current.getParentFile();
            }
        }
        return roots;
    }

    public static void writeReadme() {
        if (readmeWritten) {
            return;
        }
        readmeWritten = true;
        File readme = new File(directory(), "README.md");
        if (readme.isFile()) {
            return;
        }
        try {
            Files.writeString(readme.toPath(), README, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            Platform.log("[NCM] Failed to write the dance style readme: " + exception);
        }
    }

    private static final String README = """
            # 自定义舞蹈样式

            每个样式是 `dance` 文件夹下的一个子文件夹（可以再套一层分组文件夹），
            里面放一堆静态帧图片，外加一个 `dance.json`：

            ```
            dance/
              my_dance/
                00.png
                01.png
                dance.json
            ```

            帧图片按文件名排序后依次播放。`dance.json` 的字段（除 `frames` / `atlas` 外都可省略）：

            | 字段 | 默认值 | 说明 |
            | --- | --- | --- |
            | `name` | 文件夹名 | 设置界面里显示的名字 |
            | `pack` | 空 | 分组名，会显示成 `分组 · 名字` |
            | `frames` | 目录下全部 png | 显式指定帧文件名及其顺序 |
            | `atlas` | 无 | 改用一张大图切帧，见下 |
            | `frameDurationMs` | `110` | 未取到节拍网格时每一帧的时长（毫秒） |
            | `beatsPerCycle` | `2` | 有节拍网格时，整套动作占几拍 |
            | `speed` | `1` | 播放速度倍率 |
            | `scale` | `1` | 该样式自身的缩放 |
            | `anchorX` / `anchorY` | `0.5` / `0.95` | 缩放与鼓点脉冲的锚点（0~1，相对帧尺寸） |
            | `shadowWidth` | `0.42` | 地面阴影宽度相对帧宽的比例，`0` 关闭 |
            | `shadowOffsetY` | `0` | 阴影相对帧底部的偏移比例 |
            | `opacity` | `1` | 该样式自身的不透明度 |

            `atlas` 示例（一张 8 列 10 行的大图，取第 1 行）：

            ```json
            {
              "name": "My Dance",
              "atlas": {
                "file": "atlas.png",
                "columns": 8,
                "rows": 10,
                "row": 0,
                "x": 8,
                "y": 2,
                "width": 204,
                "height": 250
              }
            }
            ```

            改完之后在设置的 Dance 页点一下 `重新扫描` 即可。
            """;
}
