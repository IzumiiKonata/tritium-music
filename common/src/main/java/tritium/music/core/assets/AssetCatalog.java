package tritium.music.core.assets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AssetCatalog {

    public static final String FONT_PINGFANG = "fonts/pf_normal.ttf";
    public static final String FONT_PINGFANG_BOLD = "fonts/pf_middleblack.ttf";

    public static final String MODEL_MEL_SPECTROGRAM = "automix_models/mel_spectrogram.onnx";
    public static final String MODEL_BASIC_PITCH = "automix_models/basic_pitch.onnx";
    public static final String MODEL_BEAT_THIS = "automix_models/beat_this.onnx";

    private static final RemoteAsset.Kind FONT = RemoteAsset.Kind.FONT;
    private static final RemoteAsset.Kind MODEL = RemoteAsset.Kind.MODEL;
    private static final RemoteAsset.Kind NATIVE = RemoteAsset.Kind.NATIVE;

    private static final List<RemoteAsset> FONTS = List.of(
            new RemoteAsset(FONT_PINGFANG, 10_757_580L,
                    "4e8f76364b3473e758f39ccba589655edd279de70e3dfd8d62c63727c801f0db", FONT),
            new RemoteAsset(FONT_PINGFANG_BOLD, 10_606_736L,
                    "ebf0c40a72eb8f1ebca9713853556e85276d3d227dde985cb24c1f5e9eca3d7c", FONT));

    private static final List<RemoteAsset> MODELS = List.of(
            new RemoteAsset(MODEL_MEL_SPECTROGRAM, 270_742L,
                    "fdd59e65c515331308e4c8841edf99972deca646bdf6197744c2a5b7755e3de9", MODEL),
            new RemoteAsset(MODEL_BASIC_PITCH, 230_444L,
                    "2c3c1d144bfa61ad236e92e169c13535c880469a12a047d4e73451f2c059a0ec", MODEL),
            new RemoteAsset(MODEL_BEAT_THIS, 83_077_778L,
                    "c5c1466e08abdb03fdeb50668a06f244b787d564c212490482231a9cfbe9ccbd", MODEL));

    private static final Map<String, List<RemoteAsset>> NATIVES = natives();

    private static final List<RemoteAsset> ACTIVE = active();

    private static final Map<String, RemoteAsset> BY_PATH = indexByPath();
    private static final Map<String, RemoteAsset> BY_FILE_NAME = indexByFileName();

    private AssetCatalog() {
    }

    public static List<RemoteAsset> all() {
        return ACTIVE;
    }

    public static List<RemoteAsset> fonts() {
        return FONTS;
    }

    public static List<RemoteAsset> models() {
        return MODELS;
    }

    public static boolean isSupportedPlatform() {
        return AssetPlatform.isSupported();
    }

    public static List<RemoteAsset> natives(String platform) {
        return platform == null ? List.of() : NATIVES.getOrDefault(platform, List.of());
    }

    public static List<RemoteAsset> activeNatives() {
        return natives(AssetPlatform.current());
    }

    public static RemoteAsset find(String path) {
        if (path == null) {
            return null;
        }
        RemoteAsset asset = BY_PATH.get(path);
        return asset != null ? asset : BY_PATH.get(stripLeadingSlash(path));
    }

    public static RemoteAsset findByFileName(String fileName) {
        return fileName == null ? null : BY_FILE_NAME.get(fileName.toLowerCase(Locale.ROOT));
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

    private static List<RemoteAsset> active() {
        List<RemoteAsset> assets = new ArrayList<>(FONTS);
        assets.addAll(activeNatives());
        assets.addAll(MODELS);
        return List.copyOf(assets);
    }

    private static Map<String, List<RemoteAsset>> natives() {
        Map<String, List<RemoteAsset>> map = new LinkedHashMap<>();
        map.put(AssetPlatform.WINDOWS_X64, List.of(
                nativeAsset("win-x64/onnxruntime.dll", 16_149_344L,
                        "c1bae2b15344db7e27ad4ec07d1408630d290700d0db031d973e954e46eabf48"),
                nativeAsset("win-x64/onnxruntime4j_jni.dll", 92_472L,
                        "54d534e343e8fe273774d38a375e769798a4b632f031923ed5c1a605b5ea50be"),
                nativeAsset("win-x64/onnxruntime_providers_shared.dll", 21_816L,
                        "db25c0488566b7c23224211a6420313b77675e7894dd6792679041360cf7ec66")));
        map.put(AssetPlatform.LINUX_X64, List.of(
                nativeAsset("linux-x64/libonnxruntime.so", 28_497_752L,
                        "5715f06d8992ca8eeeddcce43df3a7d38f97d537052126f558e912cb312460ca"),
                nativeAsset("linux-x64/libonnxruntime4j_jni.so", 88_352L,
                        "5017145b4d1ce745c42d88d17bd80612500a20399fa0fad406e5cfbacdb5b8c8")));
        map.put(AssetPlatform.LINUX_AARCH64, List.of(
                nativeAsset("linux-aarch64/libonnxruntime.so", 24_538_024L,
                        "a27d21126db312aa8f02f3d5eaebe466e991f51f469882e6d0407d5a8b64afda"),
                nativeAsset("linux-aarch64/libonnxruntime4j_jni.so", 198_792L,
                        "b3c7701465371191b9f19ef3794d4e6cd02d09c797392b81d86b83cb75526e5d")));
        map.put(AssetPlatform.MACOS_AARCH64, List.of(
                nativeAsset("osx-aarch64/libonnxruntime.dylib", 43_151_368L,
                        "07c5a23fecedb27d9325b1b2ba0c87830173f87b64edf2b294e32931af5c09cb"),
                nativeAsset("osx-aarch64/libonnxruntime4j_jni.dylib", 104_456L,
                        "dcd561b282af0d83523f637f8a28752fd638f9b0c1d3bf8a6de22e4a67f44a85")));
        return Map.copyOf(map);
    }

    private static RemoteAsset nativeAsset(String relative, long size, String sha256) {
        return new RemoteAsset("onnxruntime_native/" + relative, size, sha256, NATIVE);
    }

    private static Map<String, RemoteAsset> indexByPath() {
        Map<String, RemoteAsset> map = new LinkedHashMap<>();
        for (RemoteAsset asset : FONTS) {
            map.put(asset.path(), asset);
        }
        for (List<RemoteAsset> platform : NATIVES.values()) {
            for (RemoteAsset asset : platform) {
                map.put(asset.path(), asset);
            }
        }
        for (RemoteAsset asset : MODELS) {
            map.put(asset.path(), asset);
        }
        return Map.copyOf(map);
    }

    private static Map<String, RemoteAsset> indexByFileName() {
        Map<String, RemoteAsset> map = new LinkedHashMap<>();
        for (RemoteAsset asset : BY_PATH.values()) {
            map.putIfAbsent(asset.fileName().toLowerCase(Locale.ROOT), asset);
        }
        return Map.copyOf(map);
    }
}
