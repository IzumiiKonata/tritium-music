package tritium.music.core.assets;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AssetCatalog {

    public static final String FONT_PINGFANG = "fonts/pf_normal.ttf";
    public static final String FONT_PINGFANG_BOLD = "fonts/pf_middleblack.ttf";

    public static final String MODEL_MEL_SPECTROGRAM = "automix_models/mel_spectrogram.onnx";
    public static final String MODEL_BASIC_PITCH = "automix_models/basic_pitch.onnx";
    public static final String MODEL_BEAT_THIS = "automix_models/beat_this.onnx";

    private static final List<RemoteAsset> ASSETS = List.of(
            new RemoteAsset(FONT_PINGFANG, 10_757_580L,
                    "4e8f76364b3473e758f39ccba589655edd279de70e3dfd8d62c63727c801f0db", true),
            new RemoteAsset(FONT_PINGFANG_BOLD, 10_606_736L,
                    "ebf0c40a72eb8f1ebca9713853556e85276d3d227dde985cb24c1f5e9eca3d7c", true),
            new RemoteAsset(MODEL_MEL_SPECTROGRAM, 270_742L,
                    "fdd59e65c515331308e4c8841edf99972deca646bdf6197744c2a5b7755e3de9", false),
            new RemoteAsset(MODEL_BASIC_PITCH, 230_444L,
                    "2c3c1d144bfa61ad236e92e169c13535c880469a12a047d4e73451f2c059a0ec", false),
            new RemoteAsset(MODEL_BEAT_THIS, 83_077_778L,
                    "c5c1466e08abdb03fdeb50668a06f244b787d564c212490482231a9cfbe9ccbd", false));

    private static final Map<String, RemoteAsset> BY_PATH = indexByPath();
    private static final Map<String, RemoteAsset> BY_FILE_NAME = indexByFileName();

    private AssetCatalog() {
    }

    public static List<RemoteAsset> all() {
        return ASSETS;
    }

    public static RemoteAsset find(String path) {
        if (path == null) {
            return null;
        }
        RemoteAsset asset = BY_PATH.get(path);
        if (asset != null) {
            return asset;
        }
        return BY_PATH.get(stripLeadingSlash(path));
    }

    public static RemoteAsset findByFileName(String fileName) {
        return fileName == null ? null : BY_FILE_NAME.get(fileName.toLowerCase(java.util.Locale.ROOT));
    }

    public static String relativePathOf(String fileName) {
        RemoteAsset asset = findByFileName(fileName);
        return asset == null ? null : asset.path();
    }

    private static String stripLeadingSlash(String path) {
        int index = 0;
        while (index < path.length() && path.charAt(index) == '/') {
            index++;
        }
        return path.substring(index);
    }

    private static Map<String, RemoteAsset> indexByPath() {
        Map<String, RemoteAsset> map = new LinkedHashMap<>();
        for (RemoteAsset asset : ASSETS) {
            map.put(asset.path(), asset);
        }
        return Map.copyOf(map);
    }

    private static Map<String, RemoteAsset> indexByFileName() {
        Map<String, RemoteAsset> map = new LinkedHashMap<>();
        for (RemoteAsset asset : ASSETS) {
            map.put(asset.fileName().toLowerCase(java.util.Locale.ROOT), asset);
        }
        return Map.copyOf(map);
    }
}
