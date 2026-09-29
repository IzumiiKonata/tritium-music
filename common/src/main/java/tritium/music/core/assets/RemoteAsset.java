package tritium.music.core.assets;

public record RemoteAsset(String path, long size, String sha256, boolean essential) {

    public String fileName() {
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    public String folder() {
        int index = path.lastIndexOf('/');
        return index < 0 ? "" : path.substring(0, index);
    }

    public String kindKey() {
        return essential ? "tritium-music.ui.assets.kind.font" : "tritium-music.ui.assets.kind.model";
    }
}
